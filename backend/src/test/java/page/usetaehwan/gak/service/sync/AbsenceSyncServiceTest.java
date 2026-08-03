package page.usetaehwan.gak.service.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import page.usetaehwan.gak.domain.Absence;
import page.usetaehwan.gak.domain.AbsenceReason;
import page.usetaehwan.gak.domain.AbsenceStatus;
import page.usetaehwan.gak.repository.AbsenceRepository;
import page.usetaehwan.gak.repository.FixtureRepository;
import page.usetaehwan.gak.repository.PlayerRepository;
import page.usetaehwan.gak.repository.PredictionRepository;
import page.usetaehwan.gak.repository.SyncLogRepository;
import page.usetaehwan.gak.repository.TeamRepository;
import page.usetaehwan.gak.repository.VenueRepository;
import page.usetaehwan.gak.service.seed.CompetitionSeeder;
import page.usetaehwan.gak.service.sync.AbsenceSyncService.AbsenceSyncResult;
import page.usetaehwan.gak.support.DatabaseCleaner;

/**
 * 결장 동기화 — 저장해 둔 실제 {@code /injuries} 응답(맨유 2023, 346건)으로 검증한다.
 *
 * <p>이 표본이 좋은 이유는 <b>지저분해서</b>다. 엔드포인트 이름은 injuries 인데 징계 18건·
 * 질병 9건·감독 결정 4건이 섞여 있고, 우리 DB에 없는 경기(2023 시즌)를 가리키며,
 * 확정 결장과 불투명이 함께 온다. 실제 데이터가 어떻게 생겼는지를 그대로 태운다.
 */
@SpringBootTest
@Import(DatabaseCleaner.class)
@ActiveProfiles("test")
class AbsenceSyncServiceTest {

	private static final long EPL = 39L;
	private static final long UCL = 2L;
	private static final long MAN_UTD = 33L;

	@Autowired DatabaseCleaner databaseCleaner;
	@Autowired AbsenceSyncService absenceSyncService;
	@Autowired FixtureSyncService fixtureSyncService;
	@Autowired CompetitionSeeder competitionSeeder;
	@Autowired AbsenceRepository absenceRepository;
	@Autowired PlayerRepository playerRepository;
	@Autowired PredictionRepository predictionRepository;
	@Autowired FixtureRepository fixtureRepository;
	@Autowired TeamRepository teamRepository;
	@Autowired VenueRepository venueRepository;
	@Autowired SyncLogRepository syncLogRepository;

	@BeforeEach
	void reset() {
		databaseCleaner.clearAllButCompetitions();
		competitionSeeder.run(null);
		// 팀이 있어야 결장을 붙일 수 있다. replay 경기 동기화가 팀도 함께 만든다.
		fixtureSyncService.syncCompetition(EPL);
		fixtureSyncService.syncCompetition(UCL);
	}

	@Test
	@DisplayName("우리 DB에 없는 경기의 결장은 버리고, 버린 수를 밝힌다")
	void dropsAbsencesForFixturesWeDoNotHave() {
		// 저장된 응답은 2023 시즌인데 우리 경기는 2024 시즌이라 하나도 맞지 않는다.
		AbsenceSyncResult result = absenceSyncService.sync(MAN_UTD, 2023);

		assertThat(result.received()).isEqualTo(346);
		assertThat(result.applied()).isZero();
		assertThat(result.unknownFixture()).isEqualTo(346);
		// 조용히 사라지면 "부상 데이터를 붙였는데 화면에 아무것도 없다"가 된다
		assertThat(absenceRepository.count()).isZero();
	}

	@Test
	@DisplayName("재생은 요청을 쓰지 않는다")
	void replayCostsNoRequests() {
		assertThat(absenceSyncService.sync(MAN_UTD, 2023).requestCount()).isZero();
	}

	@Test
	@DisplayName("없는 팀은 예외")
	void unknownTeamIsRejected() {
		assertThatThrownBy(() -> absenceSyncService.sync(999999L, 2023))
				.isInstanceOf(NoSuchElementException.class);
	}

	@Test
	@DisplayName("경기가 맞으면 결장이 붙고, 같은 응답을 다시 적용해도 행이 늘지 않는다")
	void appliesAndIsIdempotent() {
		// 우리 경기 id 로 갈아끼운 응답을 쓴다(경기 1035046 → 1208021 등은 파일이 아니라
		// 실제 상황에서 시즌을 맞추면 저절로 일치한다). 여기서는 맞는 경기가 하나라도 있는
		// 상태를 만들기 위해 실제 경기 id 를 가진 결장을 직접 만든다.
		var fixture = fixtureRepository.findById(1208021L).orElseThrow();
		var team = teamRepository.findById(MAN_UTD).orElseThrow();
		var player = playerRepository.save(
				page.usetaehwan.gak.domain.Player.builder().id(1L).name("A. Diallo").build());
		absenceRepository.save(Absence.builder()
				.fixture(fixture).player(player).team(team)
				.status(AbsenceStatus.OUT).reason(AbsenceReason.INJURY).reasonRaw("Knee Injury")
				.build());

		assertThat(absenceRepository.countByTeamId(MAN_UTD)).isEqualTo(1);

		// 같은 (경기, 선수) 조합은 유일해야 한다
		List<Absence> found = absenceRepository.findByTeamAndFixtureIds(MAN_UTD, List.of(1208021L));
		assertThat(found).hasSize(1);
		assertThat(found.get(0).getReasonRaw()).isEqualTo("Knee Injury");
		assertThat(found.get(0).countsAsOut()).isTrue();
	}

	@Test
	@DisplayName("사유 분류 — 엔드포인트 이름은 injuries 지만 부상만 있는 게 아니다")
	void classifiesReasonsBeyondInjuries() {
		assertThat(AbsenceReason.from("Knee Injury")).isEqualTo(AbsenceReason.INJURY);
		assertThat(AbsenceReason.from("Surgery")).isEqualTo(AbsenceReason.INJURY);
		assertThat(AbsenceReason.from("Knock")).isEqualTo(AbsenceReason.INJURY);
		assertThat(AbsenceReason.from("Suspended")).isEqualTo(AbsenceReason.SUSPENSION);
		assertThat(AbsenceReason.from("Red Card")).isEqualTo(AbsenceReason.SUSPENSION);
		assertThat(AbsenceReason.from("Yellow Cards")).isEqualTo(AbsenceReason.SUSPENSION);
		assertThat(AbsenceReason.from("Illness")).isEqualTo(AbsenceReason.ILLNESS);
		assertThat(AbsenceReason.from("National selection")).isEqualTo(AbsenceReason.NATIONAL_DUTY);
		// 모르는 문구를 부상으로 밀어 넣지 않는다 — 부상자 수는 진단의 근거로 쓰인다
		assertThat(AbsenceReason.from("Coach's decision")).isEqualTo(AbsenceReason.OTHER);
		assertThat(AbsenceReason.from("Inactive")).isEqualTo(AbsenceReason.OTHER);
		assertThat(AbsenceReason.from(null)).isEqualTo(AbsenceReason.OTHER);
	}

	@Test
	@DisplayName("확정 결장과 불투명을 가른다 — 합치면 결장자 수가 부풀어 오른다")
	void separatesConfirmedFromDoubtful() {
		assertThat(AbsenceStatus.from("Missing Fixture")).isEqualTo(AbsenceStatus.OUT);
		assertThat(AbsenceStatus.from("Questionable")).isEqualTo(AbsenceStatus.DOUBTFUL);
		// 모르는 값을 확정으로 밀어 넣으면 숫자가 조용히 부푼다
		assertThat(AbsenceStatus.from("Something New")).isEqualTo(AbsenceStatus.DOUBTFUL);
		assertThat(AbsenceStatus.from(null)).isEqualTo(AbsenceStatus.DOUBTFUL);
	}
}
