package page.usetaehwan.gak.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 시즌 번호 판정. API에 넘길 {@code season} 파라미터가 틀리면 응답이 통째로 비거나
 * 엉뚱한 시즌을 저장하게 되는데, 요청 하나가 아까운 상황이라 미리 못 박아 둔다.
 */
class CompetitionSeasonTest {

	@Test
	@DisplayName("걸침 시즌(유럽) — 7월부터 새 시즌")
	void splitYearSeason() {
		Competition epl = competition(false);

		assertThat(epl.seasonFor(LocalDate.of(2025, 7, 1))).isEqualTo(2025);
		assertThat(epl.seasonFor(LocalDate.of(2025, 12, 31))).isEqualTo(2025);
		// 1~6월은 아직 지난해에 시작한 시즌이 진행 중이다.
		assertThat(epl.seasonFor(LocalDate.of(2026, 5, 20))).isEqualTo(2025);
		assertThat(epl.seasonFor(LocalDate.of(2026, 6, 30))).isEqualTo(2025);
	}

	@Test
	@DisplayName("한 해 시즌(브라질·아르헨티나·K리그) — 언제나 그 해 연도")
	void calendarYearSeason() {
		Competition kLeague = competition(true);

		assertThat(kLeague.seasonFor(LocalDate.of(2026, 3, 1))).isEqualTo(2026);
		assertThat(kLeague.seasonFor(LocalDate.of(2026, 11, 30))).isEqualTo(2026);
		// 여기서 걸침 시즌 규칙을 쓰면 3월에 2025 시즌을 요청하게 된다.
		assertThat(kLeague.seasonFor(LocalDate.of(2026, 6, 30))).isEqualTo(2026);
	}

	private Competition competition(boolean calendarSeason) {
		return Competition.builder()
				.id(1L)
				.name("test")
				.type(CompetitionType.LEAGUE)
				.calendarSeason(calendarSeason)
				.displayed(true)
				.build();
	}
}
