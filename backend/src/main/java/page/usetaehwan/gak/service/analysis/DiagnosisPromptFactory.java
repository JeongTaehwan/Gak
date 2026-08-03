package page.usetaehwan.gak.service.analysis;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import page.usetaehwan.gak.domain.AbsenceReason;
import page.usetaehwan.gak.dto.analysis.CongestionSpanView;
import page.usetaehwan.gak.dto.analysis.Omission;
import page.usetaehwan.gak.dto.analysis.OpponentStrength;
import page.usetaehwan.gak.dto.analysis.TeamDiagnostics;

/**
 * AI 진단에 넘길 프롬프트를 만든다.
 *
 * <h2>원시 데이터가 아니라 계산된 지표만 넘긴다</h2>
 * <p>경기 52건의 킥오프·득점·경기장을 통째로 던져 주고 "밀집 구간을 찾아 줘"라고 하면
 * 세 가지가 한꺼번에 나빠진다.
 * <ol>
 *   <li><b>답이 흔들린다.</b> 밀집 판정은 "14일 창에 5경기"라는 규칙이지 판단이 아니다.
 *       모델이 매번 세면 매번 조금씩 다른 답이 나오고, 화면의 브래킷과 AI 문장이 어긋난다.
 *       이미 백엔드가 확정한 값을 그대로 주면 어긋날 여지가 없다.</li>
 *   <li><b>검산할 수 없다.</b> 모델이 센 숫자는 틀려도 티가 안 난다. 우리가 센 숫자는
 *       테스트가 지키고 있다.</li>
 *   <li><b>비싸다.</b> 52경기 원본은 수천 토큰이고, 매 호출마다 같은 계산을 다시 시킨다.
 *       요약된 지표는 수백 토큰이다.</li>
 * </ol>
 * <p>그래서 이 클래스는 <b>규칙 기반 문장을 만드는 함수와 같은 입력</b>을 쓴다. 모델의 일은
 * 세는 게 아니라 <b>이미 센 것을 읽고 인과를 말하는 것</b>이다.
 *
 * <h2>모르는 것은 애초에 프롬프트에 없다</h2>
 * <p>"지어내지 마라"고 쓰는 것보다 강한 장치는 <b>지어낼 재료를 주지 않는 것</b>이다.
 * 선수 개인 기여도·전술·라커룸 데이터는 우리에게 없으므로 프롬프트에도 없고, 대신
 * "우리가 갖고 있지 않은 것" 목록을 명시적으로 넘겨 모델이 그 경계를 알게 한다.
 */
final class DiagnosisPromptFactory {

	private DiagnosisPromptFactory() {
	}

	/**
	 * 시스템 프롬프트. 역할·금지사항·출력 규칙만 담고 데이터는 넣지 않는다
	 * (매 요청 같은 문자열이라 프롬프트 캐시가 붙는다).
	 */
	static final String SYSTEM = """
			당신은 축구 팀의 일정 부하를 분석하는 애널리스트입니다. 한국어로 답합니다.

			## 당신이 받는 것
			이미 계산이 끝난 지표만 받습니다. 경기 원본 데이터는 받지 않습니다.
			숫자를 다시 세거나 계산하지 마세요 — 받은 값을 그대로 인용하면 됩니다.

			## 반드시 지킬 것
			1. 주어진 지표에 없는 사실을 만들지 마세요. 특히 다음은 우리가 수집하지 않는
			   정보이므로 언급하거나 추측하지 마세요:
			   - 선수 개개인의 기량·중요도·컨디션 (누가 빠져서 전력이 얼마나 떨어졌는지)
			   - 전술·포메이션·감독의 선택
			   - 팀 분위기·사기·라커룸 상황
			   - 선수 영입·이적, 구단 재정, 감독 교체 같은 팀 바깥 사정
			   위 항목이 결론에 필요하다고 판단되면, 그 사실을 unknowns 에 적으세요.
			2. 모든 주장에는 근거 수치가 붙어야 합니다. evidence 없이 서술하지 마세요.
			   "일정이 빡빡했다" 같은 문장은 그 자체로 근거가 아닙니다 —
			   어떤 수치가 그렇게 말하는지 함께 적으세요.
			3. 상대 순위가 주어지면 반드시 함께 읽으세요. "4패"와 "4패인데 셋이 상위권
			   상대였다"는 전혀 다른 말입니다. 다만 순위를 매기지 못한 경기(컵 대회,
			   시즌 초)가 있으면 그 사실도 함께 적으세요 — 분모가 다릅니다.
			4. 상관과 인과를 구분하세요. "밀집 구간에 성적이 나빴다"는 관측이고,
			   "밀집이 원인이다"는 주장입니다. 후자를 말하려면 그럴 만한 근거가 있어야 하고,
			   없으면 관측으로만 적으세요.
			5. 데이터가 일부만 있는 지표(부분합·일부 경기만 측정)를 전체인 것처럼 말하지 마세요.

			## 출력
			- headline: 한 줄 결론. 30자 안팎. 근거의 요지가 드러나야 합니다.
			- sub: 2~3문장. 결론을 뒷받침하는 설명.
			- evidence: 결론의 근거가 된 지표. 각 항목은 주장(claim)과 그 근거가 되는
			  지표 이름(metric)·값(value)을 함께 담습니다. 값은 받은 숫자를 그대로 씁니다.
			- unknowns: 이 결론을 더 확실하게 만들려면 필요하지만 우리가 갖고 있지 않은 정보.

			문체: 단정적이되 과장하지 않습니다. 데이터가 말하는 만큼만 말합니다.
			""";

	/**
	 * 사용자 프롬프트 — 계산된 지표를 사람이 읽는 형태로 나열한다.
	 *
	 * <p>JSON 을 그대로 붓지 않고 문장/표로 옮기는 이유: 필드명이 그대로 인용되는 걸 줄이고
	 * (사용자에게 {@code shortestGapDays} 라고 말하면 안 된다), 단위와 분모를 값 옆에 붙여
	 * 모델이 "44경기 중"이라는 조건을 놓치지 않게 하기 위해서다.
	 */
	static String userPrompt(TeamDiagnostics d) {
		StringBuilder sb = new StringBuilder();
		sb.append("# ").append(d.teamName()).append(" 진단 요청\n\n");

		var w = d.window();
		sb.append("## 대상\n");
		sb.append("- 분석 경기: ").append(w.analyzedFixtures()).append("경기");
		if (w.excludedFixtures() > 0) {
			sb.append(" (연기·취소 ").append(w.excludedFixtures()).append("경기는 제외)");
		}
		sb.append("\n");
		if (w.from() != null && w.to() != null) {
			sb.append("- 기간: ").append(day(w.from())).append(" ~ ").append(day(w.to())).append("\n");
		}

		// --- 밀집도 ---
		var c = d.congestion();
		sb.append("\n## 일정 밀집도\n");
		sb.append("- 판정 기준: ").append(c.windowDays()).append("일 안에 ")
				.append(c.minMatches()).append("경기 이상\n");
		if (!c.detectable()) {
			sb.append("- 판정 불가: 경기 수가 기준에 못 미칩니다\n");
		} else if (c.spans().isEmpty()) {
			sb.append("- 밀집 구간 없음. 가장 빡빡했던 ").append(c.windowDays())
					.append("일에도 ").append(c.busiestWindowMatchCount()).append("경기\n");
		} else {
			sb.append("- 밀집 구간 ").append(c.spans().size()).append("개\n");
			for (CongestionSpanView s : c.spans()) {
				sb.append("  · ").append(day(s.from())).append("~").append(day(s.to()))
						.append(": ").append(s.spanDays()).append("일 ").append(s.matchCount()).append("경기")
						.append(", 원정 ").append(s.awayCount());
				if (s.extraTimeMatchCount() > 0) {
					sb.append(", 연장 ").append(s.extraTimeMatchCount())
							.append("회(+").append(s.extraMinutes()).append("분)");
				}
				sb.append(", 구간 내 최단 간격 ").append(s.shortestGapDays()).append("일\n");
			}
		}
		if (c.medianGapDays() != null) {
			sb.append("- 경기 간격 중앙값 ").append(c.medianGapDays()).append("일")
					.append(", 최단 ").append(c.shortestGapDays()).append("일\n");
		}

		// --- 폼 ---
		var f = d.form();
		sb.append("\n## 최근 폼 (확정된 경기만)\n");
		if (f.sampleSize() == 0) {
			sb.append("- 결과가 확정된 경기가 없습니다\n");
		} else {
			sb.append("- 최근 ").append(f.sampleSize()).append("경기: ")
					.append(f.wins()).append("승 ").append(f.draws()).append("무 ")
					.append(f.losses()).append("패\n");
			sb.append("- 획득 승점 ").append(f.points()).append("/").append(f.maxPoints()).append("\n");
			if (f.pointsRate() != null) {
				sb.append("- 승점률 ").append(Math.round(f.pointsRate() * 100)).append("%\n");
			} else {
				sb.append("- 승점률: 표본이 작아 계산하지 않았습니다 (비율로 말하지 마세요)\n");
			}
			sb.append("- 순서(과거→최근): ")
					.append(f.recent().stream().map(Enum::name).collect(Collectors.joining("")))
					.append("\n");
		}

		// --- 상대 강도 ---
		var o = d.opponentStrength();
		sb.append("\n## 상대 강도 (그 경기 시점의 순위)\n");
		if (!o.available()) {
			sb.append("- 순위를 매길 수 없었습니다(컵 대회이거나 시즌 초). 상대의 강약을 말하지 마세요\n");
		} else {
			sb.append("- 순위를 매긴 경기: ").append(o.measured()).append("경기");
			if (o.unmeasured() > 0) {
				sb.append(" (나머지 ").append(o.unmeasured())
						.append("경기는 컵이거나 시즌 초라 순위 없음 — 아래 숫자의 분모가 아닙니다)");
			}
			sb.append("\n");
			sb.append("- 상대 평균 순위 ").append(o.averageRank()).append("위 (")
					.append(o.tableSize()).append("팀 중)\n");
			sb.append("- 상위권 기준: ").append(o.topCut()).append("위 이내\n");
			sb.append("  · 상위권 상대: ").append(splitLine(o.vsTop())).append("\n");
			sb.append("  · 그 외 상대: ").append(splitLine(o.vsRest())).append("\n");
			if (!o.deductionsKnown()) {
				sb.append("- 승점 삭감을 확인하지 못했습니다. 순위가 실제와 다를 수 있습니다\n");
			}
		}

		// --- 결장 ---
		var a = d.absences();
		sb.append("\n## 결장 (부상·징계·질병 등)\n");
		if (!a.covered()) {
			sb.append("- 결장 데이터 없음. '결장 0명'이 아니라 '모름'입니다\n");
		} else {
			sb.append("- 확인된 경기: ").append(a.coveredMatches()).append("/")
					.append(a.analyzedMatches()).append("경기");
			if (a.coveredMatches() < a.analyzedMatches()) {
				sb.append(" (나머지는 '0명'이 아니라 '모름')");
			}
			sb.append("\n");
			sb.append("- 확정 결장 연인원 ").append(a.totalOut()).append("명");
			if (a.coveredMatches() > 0) {
				sb.append(", 확인된 경기당 평균 ")
						.append(Math.round((double) a.totalOut() / a.coveredMatches() * 10) / 10.0)
						.append("명");
			}
			sb.append(", 한 경기 최대 ").append(a.maxOutInOneMatch()).append("명\n");
			sb.append("- 사유별: ").append(reasonBreakdown(a.byReason())).append("\n");
			sb.append("  (엔드포인트 이름과 달리 부상만이 아니라 징계·질병·차출이 섞여 있습니다.\n");
			sb.append("   '부상 N명'으로 뭉뚱그리지 마세요.)\n");
		}

		// --- 이동거리 ---
		var t = d.travel();
		sb.append("\n## 원정 이동거리\n");
		if (t.measuredMatches() == 0 || t.totalKm() == null) {
			sb.append("- 측정된 경기 없음\n");
		} else {
			sb.append("- 원정 ").append(t.awayMatches()).append("경기 중 ")
					.append(t.measuredMatches()).append("경기 측정: 합계 ")
					.append(Math.round(t.totalKm())).append("km, 경기당 평균 ")
					.append(Math.round(t.averageKmPerMeasuredMatch())).append("km\n");
			if (t.unknownCoordinateMatches() > 0) {
				sb.append("- 좌표를 몰라 못 잰 경기 ").append(t.unknownCoordinateMatches())
						.append("건 → 위 합계는 부분합입니다\n");
			}
		}

		// --- 모르는 것 ---
		sb.append("\n## 우리가 갖고 있지 않은 정보\n");
		for (String line : unavailable(d)) {
			sb.append("- ").append(line).append("\n");
		}

		sb.append("\n위 지표만으로 이 팀의 상태를 진단하세요.");
		return sb.toString();
	}


	/** 상위권/그 외 성적 한 줄. 표본이 얇으면 비율을 적지 않는다. */
	private static String splitLine(OpponentStrength.Split s) {
		if (s.matches() == 0) {
			return "만난 적 없음";
		}
		String line = "%d경기 %d승 %d무 %d패, 승점 %d/%d"
				.formatted(s.matches(), s.wins(), s.draws(), s.losses(), s.points(), s.maxPoints());
		return s.pointsRate() == null
				? line + " (표본이 작아 승점률은 내지 않았습니다 — 비율로 말하지 마세요)"
				: line + ", 승점률 %d%%".formatted(Math.round(s.pointsRate() * 100));
	}

	/** Instant 를 날짜만 남긴 문자열로 — 모델에게 초 단위 정밀도는 필요 없다. */
	private static String day(Instant instant) {
		return LocalDate.ofInstant(instant, ZoneOffset.UTC).toString();
	}

	/** 사유 갈래를 사람이 읽는 라벨로. */
	private static String reasonBreakdown(Map<AbsenceReason, Integer> byReason) {
		if (byReason.isEmpty()) {
			return "없음";
		}
		List<String> parts = new ArrayList<>();
		byReason.forEach((reason, count) -> parts.add(label(reason) + " " + count));
		return String.join(", ", parts);
	}

	private static String label(AbsenceReason reason) {
		return switch (reason) {
			case INJURY -> "부상";
			case SUSPENSION -> "징계";
			case ILLNESS -> "질병";
			case NATIONAL_DUTY -> "대표팀 차출";
			case OTHER -> "기타";
		};
	}

	/**
	 * "우리가 못 보는 것" 목록. 백엔드가 계산을 생략한 이유(omissions)에 더해, 애초에
	 * 수집하지 않는 영역을 항상 명시한다 — 이게 모델의 상상 범위를 좁히는 실질적 장치다.
	 */
	private static List<String> unavailable(TeamDiagnostics d) {
		List<String> lines = new ArrayList<>();
		for (Omission o : d.omissions()) {
			lines.add(o.reason());
		}
		lines.add("선수 개개인의 기량·중요도: 수집하지 않습니다. 누가 빠졌는지는 알아도 "
				+ "그 선수가 팀에 얼마나 중요한지는 모릅니다.");
		lines.add("컵 대회 상대의 강함: 컵에는 순위표가 없어 순위를 매기지 못합니다. "
				+ "컵에서 강팀을 이겼어도 위 상대 강도에는 잡히지 않습니다.");
		lines.add("전술·포메이션·라인업: 수집하지 않습니다.");
		lines.add("팀 분위기·사기: 데이터로 잴 수 없습니다.");
		return lines;
	}
}
