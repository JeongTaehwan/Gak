package page.usetaehwan.gak.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class FixtureStatusTest {

	@ParameterizedTest
	@CsvSource({
			"NS,   NS",
			"TBD,  NS",
			"1H,   LIVE",
			"HT,   LIVE",
			"2H,   LIVE",
			"ET,   LIVE",
			"P,    LIVE",
			"SUSP, LIVE",
			"FT,   FT",
			"AET,  AET",
			"PEN,  PEN",
			"PST,  PST",
			"CANC, CANC",
	})
	@DisplayName("API 상태 코드를 우리 상태로 접는다")
	void mapsApiCodes(String apiCode, FixtureStatus expected) {
		assertThat(FixtureStatus.fromApiCode(apiCode)).isEqualTo(expected);
	}

	@ParameterizedTest
	@ValueSource(strings = {"AWD", "WO", "ABD", "SOMETHING_NEW"})
	@DisplayName("모르는 코드는 미확정으로 접는다 — API가 코드를 늘려도 동기화가 멈추지 않게")
	void unknownCodesFallBackInsteadOfThrowing(String apiCode) {
		assertThat(FixtureStatus.fromApiCode(apiCode)).isEqualTo(FixtureStatus.ABD);
	}

	@Test
	@DisplayName("null도 예외 없이 처리한다")
	void nullIsSafe() {
		assertThat(FixtureStatus.fromApiCode(null)).isEqualTo(FixtureStatus.ABD);
	}

	@Test
	@DisplayName("결과가 확정된 상태는 FT/AET/PEN뿐 — 예측 채점의 기준")
	void onlyThreeStatusesAreFinished() {
		for (FixtureStatus status : FixtureStatus.values()) {
			boolean expected = status == FixtureStatus.FT
					|| status == FixtureStatus.AET
					|| status == FixtureStatus.PEN;
			assertThat(status.isFinished()).as("%s", status).isEqualTo(expected);
		}
	}
}
