package page.usetaehwan.gak.external.apifootball;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import page.usetaehwan.gak.config.ApiFootballProperties;
import page.usetaehwan.gak.domain.SyncSource;
import page.usetaehwan.gak.external.apifootball.dto.FixtureItem;

/**
 * 재생 클라이언트 — 파싱·오류 처리 검증. 네트워크를 전혀 쓰지 않는다.
 *
 * <p>여기서 확인하는 핵심은 <b>HTTP 200 + errors 본문</b>을 실패로 판정하는지다.
 * 이걸 놓치면 플랜 제한 응답(response: [])을 "경기 0건"으로 오해해서
 * 정상 동작처럼 SUCCESS를 남기게 된다.
 */
class ReplayApiFootballClientTest {

	private ReplayApiFootballClient client;

	@BeforeEach
	void setUp() {
		ApiFootballProperties properties = new ApiFootballProperties(
				null, null, ApiFootballProperties.Mode.REPLAY,
				Duration.ofSeconds(5), Duration.ofSeconds(20),
				List.of("classpath:apifootball/"), null);

		ReplayResources resources = new ReplayResources(new DefaultResourceLoader(), properties);
		client = new ReplayApiFootballClient(resources, new ApiFootballResponseParser(new ObjectMapper()));
	}

	@Test
	@DisplayName("재생은 요청 예산을 쓰지 않는다")
	void replayConsumesNoQuota() {
		ApiFootballClient.FixturesFetch fetch = client.fetchFixtures(39, 2024);

		assertThat(client.source()).isEqualTo(SyncSource.REPLAY);
		assertThat(fetch.requestCount()).isZero();
	}

	@Test
	@DisplayName("응답을 경기 목록으로 파싱한다")
	void parsesFixtures() {
		List<FixtureItem> items = client.fetchFixtures(39, 2024).items();

		assertThat(items).hasSize(6);

		FixtureItem opener = items.getFirst();
		assertThat(opener.fixture().id()).isEqualTo(1208021L);
		assertThat(opener.teams().home().name()).isEqualTo("Manchester United");
		assertThat(opener.teams().away().name()).isEqualTo("Liverpool");
		assertThat(opener.goals().home()).isEqualTo(1);
		assertThat(opener.goals().away()).isEqualTo(3);
		assertThat(opener.fixture().status().code()).isEqualTo("FT");
		// "long"은 자바 예약어라 이름을 바꿔 받는다 — 그 매핑이 살아 있는지 확인.
		assertThat(opener.fixture().status().description()).isEqualTo("Match Finished");
		assertThat(opener.league().round()).isEqualTo("Regular Season - 1");
	}

	@Test
	@DisplayName("경기장이 미정이면 venue.id가 null로 온다 — 정상 응답이다")
	void venueCanBeAbsent() {
		List<FixtureItem> items = client.fetchFixtures(2, 2024).items();

		FixtureItem undecided = items.stream()
				.filter(i -> "Quarter-finals".equals(i.league().round()))
				.findFirst().orElseThrow();

		assertThat(undecided.fixture().venue().id()).isNull();
	}

	@Test
	@DisplayName("HTTP 200이어도 body에 errors가 있으면 실패로 처리한다")
	void errorsInBodyBecomeFailure() {
		// 무료 플랜이 막힌 시즌을 요청한 실제 응답 형태.
		assertThatThrownBy(() -> client.fetchFixtures(39, 2019))
				.isInstanceOf(ApiFootballException.class)
				.hasMessageContaining("plan")
				.hasMessageContaining("Free plans do not have access to this season");
	}

	@Test
	@DisplayName("재생 파일이 없으면 찾아본 위치까지 알려 주고 실패한다")
	void missingReplayFileFailsLoudly() {
		assertThatThrownBy(() -> client.fetchFixtures(140, 2024))
				.isInstanceOf(ApiFootballException.class)
				.hasMessageContaining("fixtures-league140-season2024.json")
				.hasMessageContaining("classpath:apifootball/");
	}
}
