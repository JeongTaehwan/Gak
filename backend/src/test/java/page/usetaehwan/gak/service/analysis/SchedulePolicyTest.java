package page.usetaehwan.gak.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import page.usetaehwan.gak.domain.Fixture;
import page.usetaehwan.gak.domain.FixtureStatus;

/**
 * "어떤 경기가 어떤 계산에 들어가는가"를 못 박는 테스트.
 * 이 규칙이 조용히 바뀌면 밀집도와 폼이 서로 다른 경기를 세게 되므로 표로 고정해 둔다.
 */
class SchedulePolicyTest {

	private static final Instant KICKOFF = Instant.parse("2025-01-01T15:00:00Z");

	private static Fixture fixture(FixtureStatus status, Integer goalsHome, Integer goalsAway) {
		return Fixture.builder()
				.id(1L)
				.kickoff(KICKOFF)
				.status(status)
				.goalsHome(goalsHome)
				.goalsAway(goalsAway)
				.build();
	}

	@ParameterizedTest
	@CsvSource({
			"NS,   true",    // 예정 — 다가올 부하도 진단 대상이다
			"LIVE, true",    // 진행 중 — 이미 킥오프했으므로 부하는 발생한 사실
			"FT,   true",
			"AET,  true",
			"PEN,  true",
			"PST,  false",   // 연기 — 그날 뛰지 않았다
			"CANC, false",
			"ABD,  false",
	})
	@DisplayName("일정 부하: 예정·진행 중·종료만 센다")
	void scheduleInclusion(FixtureStatus status, boolean expected) {
		assertThat(SchedulePolicy.countsForSchedule(fixture(status, 1, 0))).isEqualTo(expected);
	}

	@ParameterizedTest
	@CsvSource({
			"NS,   false",
			"LIVE, false",   // 결과가 아직 사실이 아니다 — 후반 40분 1-0은 1-1이 될 수 있다
			"FT,   true",
			"AET,  true",
			"PEN,  true",
			"PST,  false",
			"CANC, false",
			"ABD,  false",
	})
	@DisplayName("폼: 결과가 확정된 경기만 센다")
	void formInclusion(FixtureStatus status, boolean expected) {
		assertThat(SchedulePolicy.countsForForm(fixture(status, 1, 0))).isEqualTo(expected);
	}

	@ParameterizedTest
	@EnumSource(value = FixtureStatus.class, names = {"FT", "AET", "PEN"})
	@DisplayName("종료 상태여도 득점이 비어 있으면 폼에서 뺀다 — 0-0으로 읽으면 없던 무승부가 생긴다")
	void finishedWithoutGoalsIsNotCounted(FixtureStatus status) {
		assertThat(SchedulePolicy.countsForForm(fixture(status, null, null))).isFalse();
		assertThat(SchedulePolicy.countsForForm(fixture(status, 1, null))).isFalse();
	}

	@Test
	@DisplayName("킥오프가 없으면 일정에 넣을 수 없다")
	void kickoffIsRequiredForSchedule() {
		Fixture noKickoff = Fixture.builder().id(1L).status(FixtureStatus.NS).build();

		assertThat(SchedulePolicy.countsForSchedule(noKickoff)).isFalse();
		assertThat(SchedulePolicy.countsForSchedule(null)).isFalse();
	}

	@ParameterizedTest
	@CsvSource({
			"FT,   0",
			"NS,   0",
			"LIVE, 0",     // 연장 진행 중이어도 끝나 봐야 안다
			"AET,  30",
			"PEN,  30",    // 연장 30분을 뛴 뒤 갈렸다. 승부차기 자체는 뛰는 시간이 아니다
	})
	@DisplayName("정규시간 초과 소화 시간")
	void extraMinutes(FixtureStatus status, int expected) {
		assertThat(SchedulePolicy.extraMinutes(fixture(status, 2, 2))).isEqualTo(expected);
	}

	@Test
	@DisplayName("연장 여부는 초과 시간이 있는지로 판단한다")
	void wentToExtraTime() {
		assertThat(SchedulePolicy.wentToExtraTime(fixture(FixtureStatus.AET, 2, 1))).isTrue();
		assertThat(SchedulePolicy.wentToExtraTime(fixture(FixtureStatus.PEN, 2, 2))).isTrue();
		assertThat(SchedulePolicy.wentToExtraTime(fixture(FixtureStatus.FT, 2, 1))).isFalse();
		assertThat(SchedulePolicy.wentToExtraTime(null)).isFalse();
	}
}
