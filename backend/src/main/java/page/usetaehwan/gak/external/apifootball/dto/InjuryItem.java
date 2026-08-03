package page.usetaehwan.gak.external.apifootball.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * {@code GET /injuries} 응답 배열의 원소 하나 = "이 경기에 이 선수가 빠졌다" 한 줄.
 *
 * <p>엔드포인트 이름은 injuries 지만 실제로는 결장 전반이다 — 징계·질병·대표팀 차출·
 * 감독 결정이 섞여 온다. 도메인 쪽에서 {@code Absence}로 부르는 이유는
 * {@code AbsenceReason} 주석 참고.
 *
 * <p>이 레코드에는 <b>고유 id가 없다.</b> {@code (fixture.id, player.id)}가 자연키다.
 *
 * <p>쓰지 않는 필드(photo, logo, flag, timezone …)는 {@code ignoreUnknown}으로 흘려보낸다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InjuryItem(
		Player player,
		Team team,
		Fixture fixture,
		League league
) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Player(
			Long id,
			String name,
			/** "Missing Fixture"(결장 확정) 또는 "Questionable"(불투명). */
			String type,
			/** 자유 문구. "Knee Injury"·"Suspended"·"Coach's decision" 등. */
			String reason
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Team(Long id, String name) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Fixture(Long id, Long timestamp, String date) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record League(Long id, Integer season) {
	}
}
