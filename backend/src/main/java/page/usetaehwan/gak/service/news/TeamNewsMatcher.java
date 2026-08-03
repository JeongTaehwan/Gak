package page.usetaehwan.gak.service.news;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import page.usetaehwan.gak.service.seed.NewsAliasCatalog;

/**
 * 게이트 — <b>이 헤드라인이 우리 팀 것인가.</b>
 *
 * <h2>왜 여기는 LLM이 아닌가</h2>
 * <p>실제 헤드라인 137건(정답 손라벨: 참 37 / 거짓 100)으로 재 봤다.
 *
 * <pre>
 *                        정밀도    재현율   오탐   누락
 *  단순 문자열 포함        80.0%    97.3%     9     1
 *  별칭 + 제외 규칙       100.0%    97.3%     0     1
 * </pre>
 *
 * <p>오탐 9건이 <b>딱 두 모양</b>이었고, 규칙 두 줄로 전부 잡혔다(시드 파일 주석 참고).
 * 규칙으로 100%가 나오는 자리에 모델을 넣을 이유가 없다 — 결정론적이고, 공짜고, 즉시고,
 * 틀리면 어디를 고칠지 알 수 있다.
 *
 * <h2>게이트가 앞에 있는 것이 안전 장치다</h2>
 * <p>갈래 태거(LLM)는 <b>이 게이트를 통과한 항목만</b> 본다. 무엇이 피드에 들어올지는
 * 절대 결정하지 않는다. 그래서 태거가 아무리 이상하게 굴어도 <b>엉뚱한 기사가 들어올 수
 * 없다</b> — 최악의 실패가 "배지가 잘못 붙음"으로 묶여 있다.
 * "LLM은 판단자가 아니라 분류기"를 프롬프트로 부탁하는 게 아니라 순서로 강제한다.
 *
 * <h2>남은 누락 1건</h2>
 * <p>{@code "A footballing deepfake: how Bruno Fernandes fell victim to..."} —
 * 제목에 구단명이 아예 없다. 선수 이름 사전이 있어야 잡히는데,
 * <b>137건 중 1건 때문에 사전을 들이지 않았다.</b> 동명이인 위험(Xabi/Fernando Alonso가
 * 실제 표본에 함께 있었다)이 이득보다 크다.
 */
@Component
public class TeamNewsMatcher {

	private final NewsAliasCatalog catalog;

	/** 팀별 별칭 정규식. 시드가 안 바뀌므로 한 번만 컴파일한다. */
	private final Map<Long, List<Pattern>> aliasPatterns;
	private final Pattern excludeAnyPattern;

	public TeamNewsMatcher(NewsAliasCatalog catalog) {
		this.catalog = catalog;
		this.aliasPatterns = compileAliases(catalog);
		this.excludeAnyPattern = compileExcludeAny(catalog);
	}

	/**
	 * 이 제목이 어느 팀 소식인가.
	 *
	 * @param title        헤드라인 원문
	 * @param sourceTeamId 이 피드가 특정 팀 전용이면 그 팀 id. 범용 피드면 null.
	 *                     <b>전용 피드라도 게이트를 건너뛰지 않는다</b> — Guardian 맨유 피드에도
	 *                     프리미어리그 일반 칼럼과 전 소속 선수 기사가 섞여 온다(실측 4/20건)
	 * @return 매칭된 팀 id. 우리 팀 소식이 아니면 {@link Optional#empty()}
	 */
	public Optional<Long> match(String title, Long sourceTeamId) {
		if (title == null || title.isBlank() || aliasPatterns.isEmpty()) {
			return Optional.empty();
		}
		String lower = title.toLowerCase(Locale.ROOT);

		// 1단계 — 여자팀·유소년이면 여기서 끝. 별칭을 볼 필요도 없다.
		if (excludeAnyPattern != null && excludeAnyPattern.matcher(lower).find()) {
			return Optional.empty();
		}

		// 2단계 — 후보 팀. 전용 피드면 그 팀만 본다(다른 팀 별칭을 볼 이유가 없고,
		// 봐 봐야 "맨유가 아스널과 경쟁" 같은 문장에서 엉뚱한 팀으로 매칭된다).
		if (sourceTeamId != null) {
			return matchesTeam(lower, sourceTeamId) ? Optional.of(sourceTeamId) : Optional.empty();
		}
		for (Long teamId : aliasPatterns.keySet()) {
			if (matchesTeam(lower, teamId)) {
				return Optional.of(teamId);
			}
		}
		return Optional.empty();
	}

	private boolean matchesTeam(String lowerTitle, Long teamId) {
		List<Pattern> patterns = aliasPatterns.get(teamId);
		if (patterns == null) {
			return false;
		}
		for (Pattern pattern : patterns) {
			Matcher matcher = pattern.matcher(lowerTitle);
			while (matcher.find()) {
				// 3단계 — 이 등장 바로 앞에 ex-/former 가 붙었나.
				// 등장이 여러 번이면 그중 하나라도 깨끗하면 통과다.
				if (!precededByExclusion(lowerTitle, matcher.start())) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * 별칭 등장 위치 바로 앞을 본다.
	 *
	 * <p>정규식 하나로 {@code (ex-|former )alias} 를 잡지 않고 위치로 확인하는 이유:
	 * 별칭이 여러 개라 조합이 곱으로 늘고, "몇 번째 등장이 깨끗한가"를 표현하기 어렵다.
	 */
	private boolean precededByExclusion(String lowerTitle, int aliasStart) {
		for (String marker : catalog.excludeBefore()) {
			int markerStart = aliasStart - marker.length();
			if (markerStart < 0) {
				continue;
			}
			if (lowerTitle.startsWith(marker, markerStart)) {
				return true;
			}
		}
		return false;
	}

	private static Map<Long, List<Pattern>> compileAliases(NewsAliasCatalog catalog) {
		Map<Long, List<Pattern>> compiled = new java.util.LinkedHashMap<>();
		catalog.teams().forEach((teamId, aliases) -> {
			List<Pattern> patterns = new ArrayList<>();
			for (String alias : aliases.aliases()) {
				if (alias == null || alias.isBlank()) {
					continue;
				}
				patterns.add(wordBoundary(alias.toLowerCase(Locale.ROOT)));
			}
			if (!patterns.isEmpty()) {
				compiled.put(teamId, List.copyOf(patterns));
			}
		});
		return Map.copyOf(compiled);
	}

	private static Pattern compileExcludeAny(NewsAliasCatalog catalog) {
		List<String> words = catalog.excludeAny();
		if (words.isEmpty()) {
			return null;
		}
		String joined = words.stream().map(Pattern::quote)
				.reduce((a, b) -> a + "|" + b).orElseThrow();
		return wordBoundaryRaw("(?:" + joined + ")");
	}

	/**
	 * 단어 경계로 감싼다.
	 *
	 * <p>{@code \b} 를 쓰지 않는 이유: 별칭이 {@code "Man Utd"} 처럼 공백을 품거나
	 * {@code "Man."} 처럼 문장부호로 끝나면 {@code \b} 의 위치가 직관과 어긋난다.
	 * 앞뒤가 글자·숫자가 아니면 된다는 조건이 우리가 원하는 것이다 —
	 * {@code "Man Utd's"} 는 통과하고 {@code "Manchester"} 안의 {@code "Man"} 은 안 걸린다.
	 */
	private static Pattern wordBoundary(String literal) {
		return wordBoundaryRaw(Pattern.quote(literal));
	}

	private static Pattern wordBoundaryRaw(String regex) {
		return Pattern.compile("(?<![\\p{L}\\p{N}])" + regex + "(?![\\p{L}\\p{N}])",
				Pattern.CASE_INSENSITIVE);
	}

	/** 시드가 비어 게이트가 아무것도 통과시키지 못하는 상태인가. */
	public boolean disabled() {
		return aliasPatterns.isEmpty();
	}
}
