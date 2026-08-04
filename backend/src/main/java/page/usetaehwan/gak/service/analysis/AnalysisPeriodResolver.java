package page.usetaehwan.gak.service.analysis;

import java.time.Instant;
import java.util.List;
import page.usetaehwan.gak.domain.CompetitionType;
import page.usetaehwan.gak.domain.Fixture;

/**
 * "이번 진단은 어느 경기들을 보는가" — 진단의 <b>기간</b>을 정한다.
 *
 * <h2>기간은 하나다</h2>
 * <p>예전에는 지표마다 기간이 달랐다. 폼·상대 강도는 최근 6경기, 밀집도·간격·이동거리는
 * 가진 경기 전부. 한 화면에 섞여 나오면 "무엇을 진단한 것인가"가 흐려진다 — "6경기 4패"
 * 옆에 "밀집 구간 3개"가 붙으면 그 셋이 그 6경기 안에서 벌어진 일처럼 읽히지만
 * 실은 두 해에 걸친 55경기에서 나온 값이었다.
 *
 * <p>지금은 <b>가장 최신 시즌에서, 지금까지 치른 경기 전체</b> 하나뿐이다. 시즌이
 * 진행 중이면 그 시점까지의 경기(10월이면 12경기), 끝났으면 결과적으로 시즌 전체가 된다.
 *
 * <h2>치르지 않은 경기는 계산에서 뺀다 — 화면에서는 빼지 않는다</h2>
 * <p>안 치른 경기로는 폼도 밀집도 계산할 수 없다. 그렇다고 응답에서 지우면 타임라인에
 * 다가올 일정이 사라지고 예측을 걸 경기도 없어진다. 그래서 <b>자르는 건 계산뿐</b>이고,
 * 경기 목록에는 그대로 실어 {@code inAnalysis} 로 구분한다.
 *
 * <h2>최신 시즌을 "치른 경기"로 고르는 이유</h2>
 * <p>단순히 가장 큰 시즌 번호를 고르면, 다음 시즌 일정이 먼저 들어온 순간(프리시즌에
 * 흔하다) 한 경기도 안 치른 시즌으로 넘어가 화면이 통째로 비고 "판정 불가"만 남는다.
 * 그 시즌은 아직 진단할 것이 없다. <b>실제로 치른 경기가 있는 시즌 중 가장 최신</b>을
 * 고르면 개막 첫 라운드부터 자연스럽게 새 시즌으로 넘어간다.
 *
 * <h2>이 판정은 데이터 상태에 의존한다</h2>
 * <p>"지금이 어느 시즌인가"는 세상에 대한 사실이고, 우리 DB는 그것을 비출 뿐이다. 그래서
 * DB에 서로 다른 시즌이 섞여 있으면 이 판정도 그만큼 흔들린다. 경기 수 임계값("N경기
 * 이상인 시즌만 인정") 같은 보정을 넣지 <b>않는</b> 이유가 여기 있다 — 그 보정은 하필
 * <b>개막 직후</b>(경기가 2~3건뿐인 진짜 시즌 초)에 지난 시즌으로 조용히 되돌아간다.
 * 아무 에러 없이 2년 전 일정을 보여 주는 실패는 우리가 이미 한 번 겪은 것이다.
 * 대신 고른 시즌과 경기 수를 응답에 실어 <b>화면이 그것을 말하게</b> 한다.
 */
public final class AnalysisPeriodResolver {

	private AnalysisPeriodResolver() {
	}

	/**
	 * 이번 계산이 볼 기간.
	 *
	 * @param season          고른 시즌(API 시즌 번호 = 시작 연도). 경기가 하나도 없으면 null
	 * @param calendarSeason  한 해 안에서 끝나는 시즌인가(브라질·K리그). 표기가 "2025"인지
	 *                        "23/24"인지를 가른다 — 우리가 날짜로 추측하지 않고 대회 시드가 정한다
	 * @param asOf            "여기까지 치른 경기"의 기준 시각(서버 시계)
	 * @param seasonFixtures  그 시즌 이 팀의 경기 전부(예정·연기 포함), 킥오프 오름차순
	 * @param otherSeasons    다른 시즌이라 이번 계산에서 통째로 뺀 경기 수
	 */
	public record Period(
			Integer season,
			boolean calendarSeason,
			Instant asOf,
			List<Fixture> seasonFixtures,
			int otherSeasons
	) {

		/** 이 경기를 이미 치렀는가. 킥오프가 지났으면 그 부하는 이미 발생한 사실이다. */
		public boolean played(Fixture fixture) {
			return fixture.getKickoff() != null && !fixture.getKickoff().isAfter(asOf);
		}
	}

	/**
	 * @param all             이 팀의 전 경기(킥오프 오름차순). 여러 시즌이 섞여 있을 수 있다
	 * @param requestedSeason 특정 시즌을 콕 집어 볼 때. null이면 자동(최신)
	 * @param asOf            "지금". 이 시각을 넘긴 경기는 계산에서 뺀다
	 */
	public static Period resolve(List<Fixture> all, Integer requestedSeason, Instant asOf) {
		Integer season = requestedSeason != null ? requestedSeason : latestPlayedSeason(all, asOf);
		if (season == null) {
			// 치른 경기가 하나도 없다 — 그래도 다가올 시즌은 보여 줄 수 있어야 한다.
			// (개막 전에 일정만 들어온 상태. "판정 불가"로 나오되 일정은 보인다)
			season = latestSeason(all);
		}
		if (season == null) {
			return new Period(null, false, asOf, List.of(), all.size());
		}

		Integer chosen = season;
		List<Fixture> seasonFixtures = all.stream()
				.filter(f -> chosen.equals(f.getSeason()))
				.toList();
		return new Period(season, calendarSeason(seasonFixtures), asOf,
				seasonFixtures, all.size() - seasonFixtures.size());
	}

	/** 실제로 치른 경기가 있는 시즌 중 가장 최신. 연기·취소된 경기는 "치렀다"에 넣지 않는다. */
	private static Integer latestPlayedSeason(List<Fixture> all, Instant asOf) {
		Integer latest = null;
		for (Fixture f : all) {
			if (f.getSeason() == null
					|| !SchedulePolicy.countsForSchedule(f)
					|| f.getKickoff().isAfter(asOf)) {
				continue;
			}
			if (latest == null || f.getSeason() > latest) {
				latest = f.getSeason();
			}
		}
		return latest;
	}

	private static Integer latestSeason(List<Fixture> all) {
		Integer latest = null;
		for (Fixture f : all) {
			if (f.getSeason() != null && (latest == null || f.getSeason() > latest)) {
				latest = f.getSeason();
			}
		}
		return latest;
	}

	/**
	 * 이 시즌이 한 해 안에서 끝나는가. 대회 시드(`competitions.json`)가 들고 있는 사실을
	 * 그대로 읽는다 — 날짜 범위로 되짚으면 시즌 초(아직 8월 경기밖에 없는 시점)에
	 * 유럽 리그가 "2026 시즌"으로 표기된다.
	 *
	 * <p>리그를 우선해서 본다. 한 팀의 일정에는 리그·컵·유럽대항전이 섞이는데, 시즌
	 * 경계를 정하는 건 그 팀의 <b>자국 리그</b>다.
	 */
	private static boolean calendarSeason(List<Fixture> seasonFixtures) {
		for (Fixture f : seasonFixtures) {
			if (f.getCompetition().getType() == CompetitionType.LEAGUE) {
				return f.getCompetition().isCalendarSeason();
			}
		}
		return !seasonFixtures.isEmpty() && seasonFixtures.get(0).getCompetition().isCalendarSeason();
	}
}
