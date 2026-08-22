package page.usetaehwan.gak.dto.analysis;

import java.time.Instant;

/**
 * 이번 계산이 <b>어느 기간의</b> 무엇을 보고 무엇을 뺐는지.
 *
 * <p>진단 숫자보다 먼저 읽혀야 하는 값이다. "밀집 구간 0개"가 일정이 여유로워서인지,
 * 우리가 가진 경기가 3건뿐이어서인지를 여기서 알 수 있다.
 *
 * <h2>모든 지표가 이 기간 하나를 본다</h2>
 * <p>폼만 최근 6경기, 밀집도는 가진 경기 전부 — 이런 식으로 지표마다 기간이 다르면 한
 * 화면에서 무엇을 진단한 것인지 흐려진다. 지금은 <b>가장 최신 시즌의, 지금까지 치른
 * 경기 전체</b> 하나뿐이고, 화면은 이 값을 근거로 각 카드에 "23/24 시즌 52경기 기준"
 * 이라고 적는다.
 *
 * <h2>표기는 화면이 만든다</h2>
 * <p>"23/24"인지 "2025"인지는 {@link #calendarSeason}이 가른다(브라질·아르헨티나·K리그는
 * 한 해 안에서 끝난다). 판정은 서버가 하고 문자열은 화면이 만든다 — 이 앱의 다른
 * 값들과 같은 규칙이다.
 *
 * @param season              고른 시즌(API 시즌 번호 = 시작 연도). 경기가 하나도 없으면 null
 * @param calendarSeason      한 해 안에서 끝나는 시즌인가. 표기가 "2025"인지 "23/24"인지를 가른다
 * @param seasonFrom          그 시즌 첫 킥오프(예정 경기 포함). 없으면 null
 * @param seasonTo            그 시즌 마지막 킥오프(예정 경기 포함). 없으면 null
 * @param asOf                "여기까지 치른 경기"의 기준 시각
 * @param from                계산에 넣은 경기의 첫 킥오프. 대상이 없으면 null
 * @param to                  계산에 넣은 경기의 마지막 킥오프. 대상이 없으면 null
 * @param seasonFixtures      그 시즌 이 팀의 경기 수(예정·연기 포함)
 * @param analyzedFixtures    그중 실제로 계산에 넣은 수(치렀고 일정에 실재한 경기)
 * @param upcomingFixtures    아직 치르지 않아 계산에서 뺀 수. <b>화면에는 그대로 보인다</b>
 * @param excludedFixtures    연기·취소·중단이라 뺀 수
 * @param otherSeasonFixtures 다른 시즌이라 통째로 뺀 수. 0이 아니면 DB에 여러 시즌이 섞여 있다
 * @param leagueSeasonFixtures   그중 <b>자국 리그</b> 경기 수(예정·연기 포함). "리그만 보기"의
 *                            분모다. 화면이 셀 수 없어 여기서 준다 — 연기·취소된 경기는
 *                            {@code matches}에 실리지 않으므로 응답만 봐서는 리그 시즌
 *                            전체가 몇 경기였는지 알 방법이 없다
 * @param leagueExcludedFixtures 그중 연기·취소·중단이라 뺀 <b>리그</b> 경기 수. 리그만
 *                            보기에서 "몇 경기가 일정에서 빠졌나"를 전 대회 수치로
 *                            말하지 않기 위해 따로 센다
 */
public record AnalysisWindow(
		Integer season,
		boolean calendarSeason,
		Instant seasonFrom,
		Instant seasonTo,
		Instant asOf,
		Instant from,
		Instant to,
		int seasonFixtures,
		int analyzedFixtures,
		int upcomingFixtures,
		int excludedFixtures,
		int otherSeasonFixtures,
		int leagueSeasonFixtures,
		int leagueExcludedFixtures
) {

	/** 시즌이 아직 진행 중인가(치르지 않은 경기가 남아 있는가). */
	public boolean seasonInProgress() {
		return upcomingFixtures > 0;
	}
}
