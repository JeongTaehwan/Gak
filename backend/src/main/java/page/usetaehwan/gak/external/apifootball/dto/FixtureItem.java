package page.usetaehwan.gak.external.apifootball.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * {@code GET /fixtures} 응답 배열의 원소 하나 = 경기 한 건.
 *
 * <p>우리가 쓰지 않는 필드(logo, referee, timezone, periods, winner …)는
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)}로 흘려보낸다. API가 필드를
 * 추가해도 파싱이 깨지지 않아야 한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FixtureItem(
		Fixture fixture,
		League league,
		Teams teams,
		Goals goals,
		Score score
) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Fixture(
			Long id,
			/** 킥오프 epoch seconds. date보다 이 값을 우선 쓴다(타임존 해석 여지가 없다). */
			Long timestamp,
			/** ISO-8601 오프셋 시각. timestamp가 없을 때의 대비책. */
			String date,
			Venue venue,
			Status status
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Venue(Long id, String name, String city) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Status(
			/** "Match Finished" 등 서술형. JSON 키가 하필 자바 예약어라 이름을 바꿔 받는다. */
			@JsonProperty("long") String description,
			@JsonProperty("short") String code,
			Integer elapsed
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record League(
			Long id,
			String name,
			String country,
			Integer season,
			String round
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Teams(Team home, Team away) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Team(Long id, String name) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Goals(Integer home, Integer away) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Score(Goals halftime, Goals fulltime, Goals extratime, Goals penalty) {
	}
}
