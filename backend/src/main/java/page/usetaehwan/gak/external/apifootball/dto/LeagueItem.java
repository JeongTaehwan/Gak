package page.usetaehwan.gak.external.apifootball.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * {@code GET /leagues} 응답 배열의 원소 하나 = 대회 한 건.
 *
 * <p>동기화 파이프라인이 매번 부르는 엔드포인트는 아니다(대회 목록은 시드로 고정).
 * 저장해 둔 {@code leagues-raw.json}을 읽어 <b>시드에 적은 20개 id가 실제로
 * 그 이름·그 나라의 대회가 맞는지</b> 검증하는 데 쓴다.
 *
 * <p>{@code league.type}은 League/Cup 두 값뿐이라 우리의 HYBRID(조별리그+녹아웃)를
 * 판별할 수 없다. 그래서 {@code CompetitionType}은 API가 아니라 시드가 정한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LeagueItem(League league, Country country) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record League(Long id, String name, String type) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Country(String name, String code) {
	}
}
