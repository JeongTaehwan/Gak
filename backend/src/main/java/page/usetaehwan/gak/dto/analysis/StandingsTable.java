package page.usetaehwan.gak.dto.analysis;

import java.time.Instant;
import java.util.List;

/**
 * 순위표 — <b>API가 준 현재 표</b>.
 *
 * <p>진단의 상대 강도와 <b>출처가 다르다.</b> 저쪽은 우리가 경기 결과로 계산한
 * "그 경기 시점" 순위이고, 이건 API 가 준 지금 순위다. 둘이 다를 수 있고, 그건
 * 버그가 아니라 서로 다른 질문에 답하는 값이기 때문이다.
 *
 * <p>그래서 {@code updatedAt} 을 함께 준다. 화면이 <b>"언제 기준 순위인지"</b>를 밝히지
 * 않으면 사용자는 이 표를 실시간으로 읽는다.
 *
 * @param available   순위표가 있는가. 컵대회이거나 아직 동기화하지 않았으면 false
 * @param unavailableReason 없을 때 그 이유. 화면에 그대로 띄울 수 있는 한국어
 * @param rows        순위순
 * @param updatedAt   이 표를 마지막으로 받아 온 시각
 */
public record StandingsTable(
		boolean available,
		String unavailableReason,
		long competitionId,
		String competitionName,
		Integer season,
		List<Row> rows,
		Instant updatedAt
) {

	/**
	 * @param highlighted 화면이 보고 있는 팀인가 — 20줄 중 어디를 봐야 하는지 표시한다
	 * @param description API 가 붙인 순위의 의미(승격·강등권 등). 없을 수 있다
	 */
	public record Row(
			int rank,
			long teamId,
			String teamName,
			String teamCode,
			int played,
			int points,
			int goalsFor,
			int goalsAgainst,
			int goalsDiff,
			String description,
			boolean highlighted
	) {
	}

	public static StandingsTable unavailable(String reason) {
		return new StandingsTable(false, reason, 0, null, null, List.of(), null);
	}
}
