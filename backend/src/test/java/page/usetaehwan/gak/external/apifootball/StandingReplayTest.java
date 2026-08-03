package page.usetaehwan.gak.external.apifootball;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * replay 클라이언트가 <b>실제로 캡처한</b> 순위표 파일을 읽어 내는지 확인한다.
 *
 * <p>파일 이름 규칙이나 DTO 매핑이 어긋나면 여기서 걸린다 — 서버를 띄워 봐야 알게 되는
 * 종류의 실수를 테스트로 앞당긴다.
 */
@SpringBootTest
@ActiveProfiles("test")
class StandingReplayTest {

	@Autowired ApiFootballClient client;

	@Test
	@DisplayName("캡처한 2023-24 프리미어리그 순위표를 20줄로 읽는다")
	void readsTheCapturedTable() {
		var fetch = client.fetchStandings(39L, 2023);

		assertThat(fetch.requestCount()).as("replay 는 할당량을 쓰지 않는다").isZero();
		assertThat(fetch.items()).hasSize(1);

		var league = fetch.items().get(0).league();
		assertThat(league.name()).isEqualTo("Premier League");
		assertThat(league.standings()).as("정규 리그는 표가 하나").hasSize(1);

		var table = league.standings().get(0);
		assertThat(table).hasSize(20);

		var top = table.get(0);
		assertThat(top.rank()).isEqualTo(1);
		assertThat(top.team().name()).isEqualTo("Manchester City");
		assertThat(top.points()).isEqualTo(91);
		assertThat(top.all().played()).isEqualTo(38);
		// for 는 자바 예약어라 이름을 바꿔 받는다 — 그게 실제로 매핑되는지 본다
		assertThat(top.all().goals().forGoals()).isEqualTo(96);
	}
}
