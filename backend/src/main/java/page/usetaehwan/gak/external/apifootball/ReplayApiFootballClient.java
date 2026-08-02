package page.usetaehwan.gak.external.apifootball;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import page.usetaehwan.gak.domain.SyncSource;
import page.usetaehwan.gak.external.apifootball.dto.ApiFootballEnvelope;
import page.usetaehwan.gak.external.apifootball.dto.FixtureItem;

/**
 * 저장된 응답을 재생하는 클라이언트. <b>이쪽이 기본값이다.</b>
 *
 * <p>개발 중 앱을 몇 번만 재시작해도 하루 100요청은 금방 사라진다. 그래서 "실 호출"은
 * 명시적으로 켠 사람만 하고, 아무 설정 없이 띄우면 파일을 읽는다. 실수의 방향을
 * 안전한 쪽으로 몰아 두는 선택이다.
 *
 * <p>파싱은 {@link ApiFootballResponseParser}로 실 호출과 완전히 같은 경로를 탄다.
 * 저장된 파일이 실제 응답 원본이므로 errors 필드 처리도 여기서 함께 검증된다.
 */
@Component
@ConditionalOnProperty(prefix = "gak.api-football", name = "mode",
		havingValue = "replay", matchIfMissing = true)
public class ReplayApiFootballClient implements ApiFootballClient {

	private static final Logger log = LoggerFactory.getLogger(ReplayApiFootballClient.class);

	private final ReplayResources replayResources;
	private final ApiFootballResponseParser parser;

	public ReplayApiFootballClient(ReplayResources replayResources, ApiFootballResponseParser parser) {
		this.replayResources = replayResources;
		this.parser = parser;
		log.info("API-Football: REPLAY 모드 — 실제 호출을 하지 않습니다. 응답 파일 위치 {}",
				replayResources.locations());
	}

	@Override
	public SyncSource source() {
		return SyncSource.REPLAY;
	}

	@Override
	public FixturesFetch fetchFixtures(long leagueId, int season) {
		String fileName = ReplayResources.fixturesFileName(leagueId, season);
		String body = replayResources.read(fileName);
		String context = "replay " + fileName;

		ApiFootballEnvelope<FixtureItem> envelope = parser.parse(body, FixtureItem.class, context, 0);
		log.debug("replay {} → 경기 {}건", fileName, envelope.responseOrEmpty().size());

		// 재생은 할당량을 쓰지 않는다 → requestCount = 0.
		return new FixturesFetch(envelope.responseOrEmpty(), 0);
	}
}
