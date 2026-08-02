package page.usetaehwan.gak.external.apifootball;

import java.util.List;
import page.usetaehwan.gak.domain.SyncSource;
import page.usetaehwan.gak.external.apifootball.dto.FixtureItem;

/**
 * API-Football 접근 창구. 구현이 둘이다.
 * <ul>
 *   <li>{@link RestApiFootballClient} — 실제 호출. 요청 예산을 소모한다.</li>
 *   <li>{@link ReplayApiFootballClient} — 저장해 둔 응답 파일 재생. 예산을 소모하지 않는다.</li>
 * </ul>
 *
 * <p>동기화 서비스는 둘을 구분하지 않는다. 다만 이력에 남길 "소모한 요청 수"는
 * 구현이 알려 주므로 {@link FixturesFetch#requestCount()}로 받는다.
 */
public interface ApiFootballClient {

	/** 이 구현이 실 호출인지 재생인지. 이력(SyncLog)에 그대로 남는다. */
	SyncSource source();

	/**
	 * 한 대회의 한 시즌 경기 전체를 가져온다({@code /fixtures?league=&season=}).
	 * 이 엔드포인트는 시즌 전체를 한 번에 주므로 <b>대회·시즌당 1요청</b>이 된다.
	 *
	 * @throws ApiFootballException 통신 실패, HTTP 오류, 또는 200이지만 body에 errors가 있을 때
	 */
	FixturesFetch fetchFixtures(long leagueId, int season);

	/**
	 * 가져온 결과 + 그 대가로 소모한 요청 수.
	 *
	 * @param items        경기 목록(빈 목록일 수 있다 — 시즌 시작 전 등)
	 * @param requestCount 실제로 소모한 요청 수(REPLAY면 0)
	 */
	record FixturesFetch(List<FixtureItem> items, int requestCount) {
	}
}
