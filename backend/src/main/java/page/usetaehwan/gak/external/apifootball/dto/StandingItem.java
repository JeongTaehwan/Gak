package page.usetaehwan.gak.external.apifootball.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * {@code /standings?league={id}&season={year}} 응답 한 건.
 *
 * <p>응답은 리그당 하나이고 그 안에 {@code standings} 가 <b>표의 배열</b>로 들어 있다.
 * 정규 리그는 표가 하나지만, 조별리그가 있는 대회는 조마다 표가 하나씩이라 여러 개다.
 * 그래서 바깥이 {@code List<List<Row>>} 다.
 *
 * <p><b>이 응답은 "지금 시점의 표" 하나뿐이다.</b> 날짜를 지정할 방법이 없어서 지난
 * 시즌을 물으면 최종 표가 온다. 경기 시점 순위는 우리가 계산한다
 * ({@link page.usetaehwan.gak.service.analysis.LeagueTable}) — 이 응답은 <b>순위표 화면</b>과
 * <b>승점 삭감 추출</b>에 쓴다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StandingItem(League league) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record League(
			Long id,
			String name,
			Integer season,
			List<List<Row>> standings
	) {
	}

	/**
	 * 표의 한 줄.
	 *
	 * @param rank        순위
	 * @param points      승점. <b>승점 삭감이 이미 반영된 값</b>이다. 경기 결과로 계산한
	 *                    승점과 이 값의 차이가 곧 삭감분이다
	 * @param goalsDiff   득실차
	 * @param group       조 이름. 정규 리그는 리그명이 그대로 온다
	 * @param form        최근 5경기(예: {@code "WWDLW"})
	 * @param description 순위의 의미(예: {@code "Promotion - Champions League"}). 없을 수 있다
	 * @param all         전체 경기 집계
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Row(
			Integer rank,
			Team team,
			Integer points,
			Integer goalsDiff,
			String group,
			String form,
			String description,
			Record all
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Team(Long id, String name) {
	}

	/** 승·무·패와 득실. API 필드명이 {@code all} 이라 그대로 둔다. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Record(Integer played, Integer win, Integer draw, Integer lose, Goals goals) {
	}

	/** API 필드명이 {@code for} 인데 자바 예약어라 그대로 쓸 수 없다 — 이름만 바꿔 받는다. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Goals(
			@JsonProperty("for") Integer forGoals,
			Integer against
	) {
	}
}
