package page.usetaehwan.gak.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.assertj.core.api.InstanceOfAssertFactories;
import page.usetaehwan.gak.domain.Absence;
import page.usetaehwan.gak.domain.AbsenceSyncLog;
import page.usetaehwan.gak.domain.AbsenceReason;
import page.usetaehwan.gak.domain.AbsenceStatus;
import page.usetaehwan.gak.domain.Competition;
import page.usetaehwan.gak.domain.CompetitionType;
import page.usetaehwan.gak.domain.Fixture;
import page.usetaehwan.gak.domain.FixtureStatus;
import page.usetaehwan.gak.domain.Pick;
import page.usetaehwan.gak.domain.Player;
import page.usetaehwan.gak.domain.SyncLog;
import page.usetaehwan.gak.domain.SyncSource;
import page.usetaehwan.gak.domain.Team;
import page.usetaehwan.gak.domain.Venue;
import page.usetaehwan.gak.dto.analysis.CompetitionSyncStatus;
import page.usetaehwan.gak.dto.analysis.CongestionSpanView;
import page.usetaehwan.gak.dto.analysis.MatchAbsentee;
import page.usetaehwan.gak.dto.analysis.MatchLoad;
import page.usetaehwan.gak.dto.analysis.Omission;
import page.usetaehwan.gak.dto.analysis.SampleConfidence;
import page.usetaehwan.gak.dto.analysis.TeamDiagnostics;
import page.usetaehwan.gak.repository.AbsenceRepository;
import page.usetaehwan.gak.repository.AbsenceSyncLogRepository;
import page.usetaehwan.gak.repository.CompetitionRepository;
import page.usetaehwan.gak.repository.FixtureRepository;
import page.usetaehwan.gak.repository.PlayerRepository;
import page.usetaehwan.gak.repository.PredictionRepository;
import page.usetaehwan.gak.repository.SyncLogRepository;
import page.usetaehwan.gak.repository.TeamRepository;
import page.usetaehwan.gak.repository.VenueRepository;
import page.usetaehwan.gak.service.seed.CompetitionSeeder;
import page.usetaehwan.gak.service.sync.FixtureSyncService;
import page.usetaehwan.gak.support.DatabaseCleaner;

/**
 * 진단 계산 통합 테스트 — 인메모리 DB + 저장된 응답 파일(실제 API 호출 없음).
 *
 * <p>두 종류의 데이터를 쓴다.
 * <ul>
 *   <li><b>replay</b>(EPL 6 + UCL 4) — 실제와 같은 모양이지만 <b>표본이 아주 작다</b>.
 *       "경기 3건일 때 무엇을 내려보내는가"를 여기서 못 박는다.</li>
 *   <li><b>합성 일정</b> — replay에는 밀집 구간이 될 만큼 촘촘한 일정이 없다.
 *       밀집 탐지·이동거리 누적은 직접 만든 일정으로 검증한다.</li>
 * </ul>
 */
@SpringBootTest
@Import(DatabaseCleaner.class)
@ActiveProfiles("test")
class TeamDiagnosticsServiceTest {

	private static final long EPL = 39L;
	private static final long UCL = 2L;

	private static final long MAN_UTD = 33L;
	private static final long MAN_CITY = 50L;
	private static final long ARSENAL = 42L;
	private static final long CHELSEA = 49L;

	@Autowired DatabaseCleaner databaseCleaner;
	@Autowired TeamDiagnosticsService diagnosticsService;
	@Autowired FixtureSyncService syncService;
	@Autowired CompetitionSeeder competitionSeeder;
	@Autowired CompetitionRepository competitionRepository;
	@Autowired FixtureRepository fixtureRepository;
	@Autowired PredictionRepository predictionRepository;
	@Autowired TeamRepository teamRepository;
	@Autowired VenueRepository venueRepository;
	@Autowired SyncLogRepository syncLogRepository;
	@Autowired AbsenceRepository absenceRepository;
	@Autowired AbsenceSyncLogRepository absenceSyncLogRepository;
	@Autowired PlayerRepository playerRepository;

	@BeforeEach
	void reset() {
		// prediction 이 fixture 를 참조하므로 반드시 먼저 지운다. 이 테스트가 예측을 만들지
		// 않더라도 필요하다 — 같은 컨텍스트를 쓰는 다른 테스트가 남긴 행이 여기 걸린다.
		databaseCleaner.clearAllButCompetitions();
		competitionSeeder.run(null);
	}

	private void syncReplayData() {
		syncService.syncCompetition(EPL);
		syncService.syncCompetition(UCL);
	}

	// =========================================================================
	// replay 데이터 — 표본이 작을 때의 태도
	// =========================================================================

	@Nested
	@DisplayName("replay 데이터(표본 소)")
	class SmallSample {

		@BeforeEach
		void sync() {
			syncReplayData();
		}

		@Test
		@DisplayName("리그와 챔스를 가로질러 한 일정으로 합친다")
		void mergesSchedulesAcrossCompetitions() {
			TeamDiagnostics d = diagnosticsService.diagnose(MAN_UTD);

			// 8/16 리그(홈), 8/24 리그(원정), 9/17 챔스(홈)
			assertThat(d.matches()).extracting(MatchLoad::competitionId)
					.containsExactly(EPL, EPL, UCL);
			assertThat(d.matches()).extracting(MatchLoad::kickoff).isSorted();
			assertThat(d.teamName()).isEqualTo("맨유");
		}

		@Test
		@DisplayName("경기 간격을 날짜 차이로 센다 — 첫 경기는 값 없음")
		void computesGapsBetweenConsecutiveMatches() {
			TeamDiagnostics d = diagnosticsService.diagnose(MAN_UTD);

			assertThat(d.matches()).extracting(MatchLoad::gapDays)
					.containsExactly(null, 8, 24);   // 8/16 → 8/24 → 9/17
			assertThat(d.congestion().shortestGapDays()).isEqualTo(8);
			assertThat(d.congestion().medianGapDays()).isEqualTo(16.0);
		}

		@Test
		@DisplayName("표본이 기준보다 적으면 밀집 판정을 하지 않고 그 사실을 밝힌다")
		void reportsThatCongestionIsNotDetectable() {
			TeamDiagnostics d = diagnosticsService.diagnose(MAN_UTD);

			assertThat(d.congestion().detectable()).isFalse();
			assertThat(d.congestion().spans()).isEmpty();
			assertThat(d.congestion().analyzedMatchCount()).isEqualTo(3);
			// "여유로운 일정"이 아니라 "판정 불가"임을 구분할 수 있어야 한다
			assertThat(d.congestion().busiestWindowMatchCount()).isEqualTo(2);
			assertThat(d.omissions()).extracting(Omission::metric).contains("congestion");
		}

		@Test
		@DisplayName("진행 중(LIVE) 경기는 일정에는 넣고 폼에서는 뺀다")
		void liveMatchCountsForLoadButNotForForm() {
			TeamDiagnostics d = diagnosticsService.diagnose(MAN_UTD);

			assertThat(d.matches()).extracting(MatchLoad::status)
					.containsExactly(FixtureStatus.FT, FixtureStatus.LIVE, FixtureStatus.FT);
			// 확정된 두 경기만 폼에 들어간다 (8/16 패, 9/17 승)
			assertThat(d.form().sampleSize()).isEqualTo(2);
			assertThat(d.form().recent()).containsExactly(Pick.L, Pick.W);
		}

		@Test
		@DisplayName("연기(PST) 경기는 일정에서 뺀다 — 뺀 개수를 함께 알린다")
		void postponedMatchesAreExcludedAndCounted() {
			TeamDiagnostics d = diagnosticsService.diagnose(CHELSEA);

			assertThat(d.window().seasonFixtures()).isEqualTo(2);
			assertThat(d.window().analyzedFixtures()).isEqualTo(1);
			assertThat(d.window().excludedFixtures()).isEqualTo(1);
			assertThat(d.matches()).extracting(MatchLoad::status).containsExactly(FixtureStatus.FT);
		}

		@Test
		@DisplayName("표본이 5경기 미만이면 승점률을 내지 않고 개수만 준다")
		void suppressesRatesOnSmallSamples() {
			TeamDiagnostics d = diagnosticsService.diagnose(MAN_UTD);

			assertThat(d.form().confidence()).isEqualTo(SampleConfidence.LOW);
			assertThat(d.form().pointsRate()).isNull();
			// 비율은 감춰도 원래의 사실은 그대로 준다
			assertThat(d.form().wins()).isEqualTo(1);
			assertThat(d.form().losses()).isEqualTo(1);
			assertThat(d.form().points()).isEqualTo(3);
			assertThat(d.form().maxPoints()).isEqualTo(6);
			assertThat(d.omissions()).extracting(Omission::metric).contains("pointsRate");
		}

		@Test
		@DisplayName("승부차기로 갈린 컵경기는 무승부로 센다")
		void penaltyShootoutCountsAsDraw() {
			TeamDiagnostics d = diagnosticsService.diagnose(ARSENAL);

			// 8/17 원정 2-2 무, 8/17 홈 1-0 승, 3/11 챔스 2-2 승부차기 → 무
			assertThat(d.form().recent()).containsExactly(Pick.D, Pick.W, Pick.D);
			assertThat(d.form().draws()).isEqualTo(2);
		}

		@Test
		@DisplayName("승부차기는 무승부로 집계하되, PK 스코어는 따로 실어 보낸다")
		void shootoutScoreTravelsAlongsideTheDrawResult() {
			MatchLoad shootout = diagnosticsService.diagnose(ARSENAL).matches().get(2);

			// 집계는 무승부 — 120분을 뛴 부하가 폼에서 지워지면 안 된다
			assertThat(shootout.result()).isEqualTo(Pick.D);
			assertThat(shootout.goalsFor()).isEqualTo(2);
			assertThat(shootout.goalsAgainst()).isEqualTo(2);
			// 표시는 별개 — 화면이 "PK 패"를 붙일 수 있도록 스코어를 그대로 준다
			assertThat(shootout.shootoutFor()).isEqualTo(4);
			assertThat(shootout.shootoutAgainst()).isEqualTo(5);
		}

		@Test
		@DisplayName("승부차기가 없던 경기는 PK 스코어가 null이다 — 0-0으로 채우지 않는다")
		void matchesWithoutShootoutCarryNullScores() {
			assertThat(diagnosticsService.diagnose(MAN_UTD).matches())
					.allSatisfy(m -> {
						assertThat(m.shootoutFor()).isNull();
						assertThat(m.shootoutAgainst()).isNull();
					});
		}

		@Test
		@DisplayName("결과가 확정되지 않은 경기는 승패도 스코어도 null이다")
		void unresolvedMatchesCarryNoResult() {
			// 8/24 토트넘전은 진행 중(LIVE) — 중간 스코어 1-1을 최종처럼 내려보내지 않는다
			MatchLoad live = diagnosticsService.diagnose(MAN_UTD).matches().get(1);

			assertThat(live.status()).isEqualTo(FixtureStatus.LIVE);
			assertThat(live.result()).isNull();
			assertThat(live.goalsFor()).isNull();
			assertThat(live.goalsAgainst()).isNull();
		}

		@Test
		@DisplayName("우리 팀 관점으로 득실을 뒤집어 준다 — 화면이 홈/원정을 다시 따지지 않게")
		void goalsAreGivenFromOurPointOfView() {
			List<MatchLoad> matches = diagnosticsService.diagnose(MAN_UTD).matches();

			// 8/16 홈 1-3 패 (원본 goals 1-3)
			assertThat(matches.get(0).home()).isTrue();
			assertThat(matches.get(0).result()).isEqualTo(Pick.L);
			assertThat(matches.get(0).goalsFor()).isEqualTo(1);
			assertThat(matches.get(0).goalsAgainst()).isEqualTo(3);

			// 9/17 챔스 홈 2-1 승
			assertThat(matches.get(2).result()).isEqualTo(Pick.W);
			assertThat(matches.get(2).goalsFor()).isEqualTo(2);
			assertThat(matches.get(2).goalsAgainst()).isEqualTo(1);
		}

		@Test
		@DisplayName("대회 성격과 짧은 표기명을 함께 준다 — 화면이 대회 id로 라벨을 분기하지 않도록")
		void carriesCompetitionTypeAndShortLabel() {
			List<MatchLoad> matches = diagnosticsService.diagnose(MAN_UTD).matches();

			assertThat(matches).extracting(MatchLoad::competitionType)
					.containsExactly(CompetitionType.LEAGUE, CompetitionType.LEAGUE,
							CompetitionType.HYBRID);
			assertThat(matches).extracting(MatchLoad::competitionShortName)
					.containsExactly("리그", "리그", "챔스");
			// 긴 이름도 그대로 남아 있다(툴팁·진단 문장용)
			assertThat(matches.get(2).competitionName()).isEqualTo("UEFA 챔피언스리그");
		}

		@Test
		@DisplayName("연장·승부차기는 정규시간 초과 소화 시간으로 잡힌다")
		void extraTimeIsRecordedAsAdditionalMinutes() {
			TeamDiagnostics d = diagnosticsService.diagnose(ARSENAL);

			assertThat(d.matches()).extracting(MatchLoad::extraMinutes)
					.containsExactly(0, 0, 30);
		}

		@Test
		@DisplayName("상대 강도는 순위 데이터가 없어 생략하고, 생략했다는 사실을 남긴다")
		void opponentStrengthIsOmittedWithReason() {
			TeamDiagnostics d = diagnosticsService.diagnose(MAN_UTD);

			assertThat(d.form().opponentStrength()).isNull();
			assertThat(d.omissions())
					.filteredOn(o -> o.metric().equals("opponentStrength"))
					.singleElement()
					.satisfies(o -> assertThat(o.reason()).contains("순위"));
		}

		@Test
		@DisplayName("원정 이동거리는 홈 구장↔경기장으로 잰다(홈경기는 0)")
		void measuresAwayTravelFromHomeGround() {
			TeamDiagnostics d = diagnosticsService.diagnose(MAN_UTD);

			// 홈(올드 트래퍼드) → 원정(토트넘, 런던) → 홈
			assertThat(d.matches().get(0).travelKm()).isEqualTo(0.0);
			assertThat(d.matches().get(1).travelKm()).isCloseTo(262.0, within(1.0));
			assertThat(d.matches().get(2).travelKm()).isEqualTo(0.0);

			assertThat(d.travel().awayMatches()).isEqualTo(1);
			assertThat(d.travel().measuredMatches()).isEqualTo(1);
			assertThat(d.travel().totalKm()).isCloseTo(262.0, within(1.0));
		}

		@Test
		@DisplayName("경기장 좌표가 없으면 거리를 null로 두고 부분합임을 알린다 — 0으로 접지 않는다")
		void unknownVenueYieldsNullDistanceNotZero() {
			// 맨시티: 홈(에티하드) / 원정 안필드 / 원정 바르셀로나(경기장 미정 → 좌표 없음)
			TeamDiagnostics d = diagnosticsService.diagnose(MAN_CITY);

			assertThat(d.matches()).extracting(MatchLoad::travelKm)
					.containsExactly(0.0, 50.3, null);

			assertThat(d.travel().awayMatches()).isEqualTo(2);
			assertThat(d.travel().measuredMatches()).isEqualTo(1);
			assertThat(d.travel().unknownCoordinateMatches()).isEqualTo(1);
			assertThat(d.omissions()).extracting(Omission::metric).contains("travelDistance");
		}

		@Test
		@DisplayName("결과를 저장하지 않는다 — 계산해도 테이블 어디에도 행이 늘지 않는다")
		void diagnosisWritesNothing() {
			long fixturesBefore = fixtureRepository.count();
			long teamsBefore = teamRepository.count();
			long venuesBefore = venueRepository.count();

			diagnosticsService.diagnose(MAN_UTD);
			diagnosticsService.diagnose(MAN_UTD);

			assertThat(fixtureRepository.count()).isEqualTo(fixturesBefore);
			assertThat(teamRepository.count()).isEqualTo(teamsBefore);
			assertThat(venueRepository.count()).isEqualTo(venuesBefore);
		}

		@Test
		@DisplayName("같은 데이터를 두 번 계산하면 같은 결과가 나온다")
		void isDeterministic() {
			TeamDiagnostics first = diagnosticsService.diagnose(MAN_UTD);
			TeamDiagnostics second = diagnosticsService.diagnose(MAN_UTD);

			assertThat(second.matches()).isEqualTo(first.matches());
			assertThat(second.congestion()).isEqualTo(first.congestion());
			assertThat(second.form()).isEqualTo(first.form());
			assertThat(second.travel()).isEqualTo(first.travel());
		}

		@Test
		@DisplayName("없는 팀은 404로 이어지는 예외")
		void unknownTeamThrows() {
			assertThatThrownBy(() -> diagnosticsService.diagnose(999999L))
					.isInstanceOf(NoSuchElementException.class);
		}
	}

	// =========================================================================
	// 합성 일정 — 밀집 구간이 실제로 나오는 경우
	// =========================================================================

	@Nested
	@DisplayName("합성 밀집 일정")
	class DenseSchedule {

		static final long TEAM = 900001L;
		static final long OPPONENT = 900002L;
		static final Instant BASE = Instant.parse("2025-01-01T15:00:00Z");

		/** 0·3·6·9·12일(12일 안에 5경기 → 밀집) + 30일(단독). */
		static final int[] OFFSETS = {0, 3, 6, 9, 12, 30};

		@BeforeEach
		void buildSchedule() {
			Competition epl = competitionRepository.findById(EPL).orElseThrow();

			Venue manchester = venueRepository.save(Venue.builder()
					.id(950001L).name("합성 홈구장").city("Manchester")
					.latitude(53.4808).longitude(-2.2426).build());
			Venue london = venueRepository.save(Venue.builder()
					.id(950002L).name("합성 원정구장").city("London")
					.latitude(51.5074).longitude(-0.1278).build());

			Team team = teamRepository.save(Team.builder()
					.id(TEAM).name("Synthetic FC").nameKo("합성FC").homeVenue(manchester).build());
			Team opponent = teamRepository.save(Team.builder()
					.id(OPPONENT).name("Opponent FC").nameKo("상대FC").homeVenue(london).build());

			// 홈/원정 번갈아. 앞 5경기는 결과 확정, 마지막 1경기는 예정.
			// 결과(우리 관점): 승 · 승 · 무 · 패 · 연장승 → 3승 1무 1패 = 승점 10
			for (int i = 0; i < OFFSETS.length; i++) {
				boolean home = (i % 2 == 0);
				boolean played = i < 5;
				FixtureStatus status = !played ? FixtureStatus.NS
						: (i == 4 ? FixtureStatus.AET : FixtureStatus.FT);

				Integer goalsHome = null;
				Integer goalsAway = null;
				if (played) {
					int[][] goals = {{2, 0}, {0, 2}, {1, 1}, {3, 1}, {2, 1}};
					goalsHome = goals[i][0];
					goalsAway = goals[i][1];
				}

				fixtureRepository.save(Fixture.builder()
						.id(910000L + i)
						.competition(epl)
						.season(2024)
						.round("Regular Season - " + (i + 1))
						.homeTeam(home ? team : opponent)
						.awayTeam(home ? opponent : team)
						.venue(home ? manchester : london)
						.kickoff(BASE.plus(Duration.ofDays(OFFSETS[i])))
						.status(status)
						.goalsHome(goalsHome)
						.goalsAway(goalsAway)
						.build());
			}
		}

		@Test
		@DisplayName("12일 안의 5경기를 하나의 밀집 구간으로 잡는다")
		void detectsAndMergesTheBurst() {
			TeamDiagnostics d = diagnosticsService.diagnose(TEAM);

			assertThat(d.congestion().detectable()).isTrue();
			assertThat(d.congestion().busiestWindowMatchCount()).isEqualTo(5);
			assertThat(d.congestion().spans()).hasSize(1);

			CongestionSpanView span = d.congestion().spans().get(0);
			assertThat(span.matchCount()).isEqualTo(5);
			assertThat(span.spanDays()).isEqualTo(12);
			assertThat(span.awayCount()).isEqualTo(2);
			assertThat(span.extraTimeMatchCount()).isEqualTo(1);
			assertThat(span.extraMinutes()).isEqualTo(30);
			assertThat(span.shortestGapDays()).isEqualTo(3);
			assertThat(span.startFixtureId()).isEqualTo(910000L);
			assertThat(span.endFixtureId()).isEqualTo(910004L);
			assertThat(span.travelKm()).isCloseTo(524.0, within(1.0));
		}

		@Test
		@DisplayName("구간 밖 경기는 밀집 소속이 없다 — 구간 경계는 경기 id로 준다")
		void spanMembershipIsPerMatch() {
			TeamDiagnostics d = diagnosticsService.diagnose(TEAM);

			assertThat(d.matches()).extracting(MatchLoad::congestionSpanId)
					.containsExactly(0, 0, 0, 0, 0, null);
			assertThat(d.matches().get(5).gapDays()).isEqualTo(18);
		}

		@Test
		@DisplayName("간격의 대표값은 평균이 아니라 중앙값 — 긴 공백 하나에 끌려가지 않는다")
		void usesMedianNotMeanForGaps() {
			TeamDiagnostics d = diagnosticsService.diagnose(TEAM);

			// 간격은 3,3,3,3,18. 평균은 6.0이지만 실제로는 다섯 중 넷이 3일이다.
			assertThat(d.congestion().medianGapDays()).isEqualTo(3.0);
			assertThat(d.congestion().shortestGapDays()).isEqualTo(3);
		}

		@Test
		@DisplayName("표본이 5경기가 되면 승점률을 낸다")
		void publishesRateOnceSampleIsBigEnough() {
			TeamDiagnostics d = diagnosticsService.diagnose(TEAM);

			assertThat(d.form().sampleSize()).isEqualTo(5);
			assertThat(d.form().confidence()).isEqualTo(SampleConfidence.MODERATE);
			assertThat(d.form().recent()).containsExactly(Pick.W, Pick.W, Pick.D, Pick.L, Pick.W);
			assertThat(d.form().points()).isEqualTo(10);
			assertThat(d.form().maxPoints()).isEqualTo(15);
			assertThat(d.form().pointsRate()).isCloseTo(0.667, within(0.001));
		}

		@Test
		@DisplayName("이동거리는 진단 기간의 원정만 누적한다 — 다른 지표와 같은 분모")
		void accumulatesTravelOverTheSamePeriodAsEveryOtherMetric() {
			TeamDiagnostics all = diagnosticsService.diagnose(TEAM);
			assertThat(all.travel().awayMatches()).isEqualTo(3);
			assertThat(all.travel().totalKm()).isCloseTo(786.0, within(1.5));
			assertThat(all.travel().longestTripKm()).isCloseTo(262.0, within(1.0));
			// 집계 구간은 진단 기간 그대로다 — 이 지표만 다른 기간을 보지 않는다
			assertThat(all.travel().from()).isEqualTo(all.window().from());
			assertThat(all.travel().to()).isEqualTo(all.window().to());

			// 기간을 12일째까지로 좁히면 이동거리도 함께 좁아진다(같은 기간을 보므로)
			TeamDiagnostics burst = diagnosticsService.diagnose(TEAM,
					DiagnosticsOptions.DEFAULTS.withAsOf(BASE.plus(Duration.ofDays(12))));

			assertThat(burst.travel().awayMatches()).isEqualTo(2);
			assertThat(burst.travel().totalKm()).isCloseTo(524.0, within(1.0));
			assertThat(burst.travel().averageKmPerMeasuredMatch()).isCloseTo(262.0, within(1.0));
			// 목록은 좁아지지 않는다 — 화면은 다가올 경기까지 그려야 한다
			assertThat(burst.matches()).hasSize(6);
		}

		@Test
		@DisplayName("기준을 바꾸면 결과도 바뀐다 — 기준은 저장값이 아니라 인자다")
		void thresholdsAreParameters() {
			DiagnosticsOptions strict = new DiagnosticsOptions(7, 5, null, null);
			TeamDiagnostics d = diagnosticsService.diagnose(TEAM, strict);

			// 7일 창에는 최대 3경기(0·3·6일)뿐이라 밀집이 아니다
			assertThat(d.congestion().busiestWindowMatchCount()).isEqualTo(3);
			assertThat(d.congestion().spans()).isEmpty();
			assertThat(d.congestion().detectable()).isTrue();
		}

		@Test
		@DisplayName("경기 목록은 화면이 자기 목록과 맞출 수 있게 경기 id를 준다")
		void exposesFixtureIds() {
			TeamDiagnostics d = diagnosticsService.diagnose(TEAM);

			assertThat(d.matches()).extracting(MatchLoad::fixtureId)
					.containsExactly(910000L, 910001L, 910002L, 910003L, 910004L, 910005L);
			assertThat(d.matches()).extracting(MatchLoad::home)
					.containsExactly(true, false, true, false, true, false);
			assertThat(d.matches()).extracting(MatchLoad::opponentName)
					.containsOnly("상대FC");
		}

		@Test
		@DisplayName("빈 일정도 예외 없이 계산된다")
		void emptyScheduleIsNotAnError() {
			Team lonely = teamRepository.save(Team.builder()
					.id(900099L).name("No Fixtures FC").build());

			TeamDiagnostics d = diagnosticsService.diagnose(lonely.getId());

			assertThat(d.matches()).isEmpty();
			assertThat(d.window().from()).isNull();
			assertThat(d.congestion().busiestWindowMatchCount()).isZero();
			assertThat(d.congestion().medianGapDays()).isNull();
			assertThat(d.form().sampleSize()).isZero();
			assertThat(d.form().confidence()).isEqualTo(SampleConfidence.NONE);
			assertThat(d.travel().totalKm()).isNull();
			assertThat(d.omissions()).extracting(Omission::metric)
					.contains("congestion", "pointsRate", "opponentStrength");
		}
	}

	// =========================================================================
	// 리그만 단면 — "리그만 보기"의 재판정 · 결장 명단 · 동기화 커버리지
	// =========================================================================

	@Nested
	@DisplayName("리그만 단면과 커버리지")
	class LeagueSlice {

		static final long TEAM = 900021L;
		static final long OPPONENT = 900022L;
		static final Instant BASE = Instant.parse("2025-01-01T15:00:00Z");

		/** 리그 5경기(7일 간격) 사이에 컵 4경기가 끼어 전 대회만 밀집이 되는 일정. */
		static final int[] LEAGUE_OFFSETS = {0, 7, 14, 21, 28};
		static final int[] CUP_OFFSETS = {3, 10, 17, 24};

		long leagueFixtureId(int i) {
			return 940000L + i;
		}

		long cupFixtureId(int i) {
			return 941000L + i;
		}

		@BeforeEach
		void buildMixedSchedule() {
			Competition epl = competitionRepository.findById(EPL).orElseThrow();
			Competition ucl = competitionRepository.findById(UCL).orElseThrow();

			Team team = teamRepository.save(Team.builder()
					.id(TEAM).name("Mixed FC").nameKo("혼합FC").build());
			Team opponent = teamRepository.save(Team.builder()
					.id(OPPONENT).name("Opponent FC").nameKo("상대FC").build());

			for (int i = 0; i < LEAGUE_OFFSETS.length; i++) {
				save(leagueFixtureId(i), epl, team, opponent, LEAGUE_OFFSETS[i]);
			}
			for (int i = 0; i < CUP_OFFSETS.length; i++) {
				save(cupFixtureId(i), ucl, team, opponent, CUP_OFFSETS[i]);
			}
		}

		private void save(long id, Competition competition, Team team, Team opponent, int offsetDays) {
			fixtureRepository.save(Fixture.builder()
					.id(id).competition(competition).season(2024).round("R")
					.homeTeam(team).awayTeam(opponent)
					.kickoff(BASE.plus(Duration.ofDays(offsetDays)))
					.status(FixtureStatus.FT).goalsHome(1).goalsAway(0).build());
		}

		@Test
		@DisplayName("리그 밀집은 리그 경기만으로 다시 판정한다 — 전 대회 값을 거른 것이 아니다")
		void leagueCongestionIsRecomputedFromLeagueMatchesOnly() {
			TeamDiagnostics d = diagnosticsService.diagnose(TEAM);

			// 전 대회: 9경기가 3·4일 간격으로 이어져 하나의 밀집 구간
			assertThat(d.congestion().analyzedMatchCount()).isEqualTo(9);
			assertThat(d.congestion().spans()).hasSize(1);
			assertThat(d.congestion().medianGapDays()).isEqualTo(3.5);

			// 리그만: 5경기가 전부 7일 간격 — 밀집 0개. 표본은 충분하므로 판정 불가가 아니다
			assertThat(d.leagueCongestion().analyzedMatchCount()).isEqualTo(5);
			assertThat(d.leagueCongestion().detectable()).isTrue();
			assertThat(d.leagueCongestion().spans()).isEmpty();
			assertThat(d.leagueCongestion().medianGapDays()).isEqualTo(7.0);
			assertThat(d.leagueCongestion().shortestGapDays()).isEqualTo(7);
		}

		@Test
		@DisplayName("리그 간격은 리그 경기 사이로 다시 센다 — 컵 경기는 간격을 만들지도 받지도 않는다")
		void leagueGapsAreCountedBetweenLeagueMatches() {
			TeamDiagnostics d = diagnosticsService.diagnose(TEAM);

			// 킥오프순: L0 C3 L7 C10 L14 C17 L21 C24 L28
			assertThat(d.matches()).extracting(MatchLoad::leagueGapDays)
					.containsExactly(null, null, 7, null, 7, null, 7, null, 7);
			// 전 대회 밀집 구간에는 아홉 경기 전부 속하지만, 리그 기준으로는 아무도 밀집이 아니다
			assertThat(d.matches()).extracting(MatchLoad::congestionSpanId)
					.containsOnly(0);
			assertThat(d.matches()).extracting(MatchLoad::leagueCongestionSpanId)
					.containsOnly((Integer) null);
		}

		@Test
		@DisplayName("리그 기준 밀집이 잡히면 구간 경계도 리그 경기 id다")
		void leagueSpanBoundariesAreLeagueFixtureIds() {
			// 창을 28일로 넓히면 리그 5경기도 한 구간이 된다
			TeamDiagnostics d = diagnosticsService.diagnose(TEAM,
					new DiagnosticsOptions(28, 5, null, null));

			assertThat(d.leagueCongestion().spans()).hasSize(1);
			CongestionSpanView span = d.leagueCongestion().spans().get(0);
			assertThat(span.matchCount()).isEqualTo(5);
			assertThat(span.spanDays()).isEqualTo(28);
			assertThat(span.startFixtureId()).isEqualTo(leagueFixtureId(0));
			assertThat(span.endFixtureId()).isEqualTo(leagueFixtureId(4));
			// 리그 구간 소속도 리그 경기에만 붙는다
			assertThat(d.matches()).extracting(MatchLoad::leagueCongestionSpanId)
					.containsExactly(0, null, 0, null, 0, null, 0, null, 0);
		}

		@Test
		@DisplayName("결장 명단은 확정 결장만, 인원수와 같은 규칙으로 실린다")
		void absenteesTravelWithTheCount() {
			Player out = playerRepository.save(Player.builder().id(960001L).name("Out Player").build());
			Player doubtful = playerRepository.save(Player.builder().id(960002L).name("Doubtful Player").build());
			Team team = teamRepository.findById(TEAM).orElseThrow();
			Fixture fixture = fixtureRepository.findById(leagueFixtureId(1)).orElseThrow();

			absenceRepository.save(Absence.builder()
					.fixture(fixture).player(out).team(team)
					.status(AbsenceStatus.OUT).reason(AbsenceReason.INJURY)
					.reasonRaw("Knee Injury").build());
			absenceRepository.save(Absence.builder()
					.fixture(fixture).player(doubtful).team(team)
					.status(AbsenceStatus.DOUBTFUL).reason(AbsenceReason.OTHER)
					.reasonRaw("Questionable").build());

			TeamDiagnostics d = diagnosticsService.diagnose(TEAM);
			MatchLoad covered = d.matches().stream()
					.filter(m -> m.fixtureId() == leagueFixtureId(1)).findFirst().orElseThrow();
			MatchLoad unknown = d.matches().stream()
					.filter(m -> m.fixtureId() == leagueFixtureId(0)).findFirst().orElseThrow();

			// 확인한 경기: 불투명은 명단에도 인원수에도 넣지 않는다 — 길이가 항상 일치
			assertThat(covered.absentCount()).isEqualTo(1);
			assertThat(covered.absentees()).hasSize(1);
			assertThat(covered.absentees().get(0).playerName()).isEqualTo("Out Player");
			assertThat(covered.absentees().get(0).reason()).isEqualTo(AbsenceReason.INJURY);

			// 데이터가 없는 경기: 빈 목록이 아니라 null — "모름"과 "0명"의 구분
			assertThat(unknown.absentCount()).isNull();
			assertThat(unknown.absentees()).isNull();
		}

		@Test
		@DisplayName("리그 분모를 따로 준다 — 화면은 연기·취소된 리그 경기를 셀 수 없다")
		void leagueDenominatorIsCountedOnTheServer() {
			// 리그 경기 하나를 연기시킨다 — 일정(matches)에서는 빠지지만 시즌 경기이긴 하다
			Competition epl = competitionRepository.findById(EPL).orElseThrow();
			Team team = teamRepository.findById(TEAM).orElseThrow();
			Team opponent = teamRepository.findById(OPPONENT).orElseThrow();
			fixtureRepository.save(Fixture.builder()
					.id(942000L).competition(epl).season(2024).round("R")
					.homeTeam(team).awayTeam(opponent)
					.kickoff(BASE.plus(Duration.ofDays(35)))
					.status(FixtureStatus.PST).build());

			TeamDiagnostics d = diagnosticsService.diagnose(TEAM);

			// 전 대회: 리그 6(연기 1 포함) + 컵 4 = 10경기가 시즌에 있고 9경기가 일정에 남는다
			assertThat(d.window().seasonFixtures()).isEqualTo(10);
			assertThat(d.window().excludedFixtures()).isEqualTo(1);
			// 리그만: 6경기 중 1경기가 연기 — 이 둘을 전 대회 값(10/1)으로 말하면 안 된다
			assertThat(d.window().leagueSeasonFixtures()).isEqualTo(6);
			assertThat(d.window().leagueExcludedFixtures()).isEqualTo(1);
			// 연기된 경기는 일정에도 밀집 판정에도 들어가지 않는다
			assertThat(d.matches()).extracting(MatchLoad::fixtureId).doesNotContain(942000L);
			assertThat(d.leagueCongestion().analyzedMatchCount()).isEqualTo(5);
		}

		@Test
		@DisplayName("결장 미수집과 '받았지만 이 경기는 없음'을 수집 이력으로 가른다")
		void absenceNotCollectedIsToldApartFromQuietBySyncHistory() {
			// 이력이 없을 때: 행이 없는 것과 "아무도 안 빠졌다"를 구분할 수 없다
			TeamDiagnostics before = diagnosticsService.diagnose(TEAM);
			assertThat(before.absences().lastSyncedAt()).isNull();
			assertThat(before.absences().covered()).isFalse();
			assertThat(before.omissions())
					.filteredOn(o -> o.metric().equals("absences"))
					.first().extracting(Omission::reason, InstanceOfAssertFactories.STRING)
					.contains("아직 동기화하지 않았습니다");

			// (팀, 시즌) 결장 동기화가 성공했다는 이력만 남긴다 — 결장 행은 0건
			Instant syncedAt = Instant.parse("2025-02-01T03:00:00Z");
			absenceSyncLogRepository.save(AbsenceSyncLog.success(
					TEAM, 2024, syncedAt, syncedAt, SyncSource.REPLAY, 1, 0));

			TeamDiagnostics after = diagnosticsService.diagnose(TEAM);

			// 같은 0건인데 화면이 할 말이 달라진다 — 받은 적 없음 vs 받아 왔음
			assertThat(after.absences().lastSyncedAt()).isEqualTo(syncedAt);
			assertThat(after.omissions())
					.filteredOn(o -> o.metric().equals("absences"))
					.first().extracting(Omission::reason, InstanceOfAssertFactories.STRING)
					.contains("받아 왔지만");

			// ⚠️ 이력이 있어도 경기별 결장을 0으로 채우지 않는다 — API 커버리지가 부분적이라
			// "행이 없다"가 "아무도 안 빠졌다"를 뜻하지 않는다(domain.md).
			assertThat(after.matches()).extracting(MatchLoad::absentCount)
					.containsOnly((Integer) null);
			assertThat(after.matches()).extracting(MatchLoad::absentees)
					.containsOnly((List<MatchAbsentee>) null);
		}

		@Test
		@DisplayName("동기화 커버리지는 (대회, 시즌) 단위다 — 다른 시즌의 성공은 세지 않는다")
		void syncCoverageIsPerCompetitionAndSeason() {
			Instant syncedAt = Instant.parse("2025-01-10T04:00:00Z");
			// 조회 시즌(2024)의 EPL 성공 + 다른 시즌(2023)의 UCL 성공
			syncLogRepository.save(SyncLog.success(
					EPL, 2024, syncedAt, syncedAt, SyncSource.REPLAY, 0, 5, 0, 0));
			syncLogRepository.save(SyncLog.success(
					UCL, 2023, syncedAt, syncedAt, SyncSource.REPLAY, 0, 4, 0, 0));

			TeamDiagnostics d = diagnosticsService.diagnose(TEAM);

			CompetitionSyncStatus epl = coverageOf(d, EPL);
			CompetitionSyncStatus ucl = coverageOf(d, UCL);
			assertThat(epl.lastSuccessAt()).isEqualTo(syncedAt);
			// 2023 시즌의 성공은 2024 시즌 커버리지가 아니다 — 수집 전(null)
			assertThat(ucl.lastSuccessAt()).isNull();
			assertThat(ucl.type()).isEqualTo(CompetitionType.HYBRID);
			assertThat(epl.name()).isNotBlank();
		}

		private CompetitionSyncStatus coverageOf(TeamDiagnostics d, long competitionId) {
			return d.syncCoverage().stream()
					.filter(c -> c.competitionId() == competitionId)
					.findFirst().orElseThrow();
		}
	}

	// =========================================================================
	// 진단 기간 — 최신 시즌의 "치른 경기까지"
	// =========================================================================

	@Nested
	@DisplayName("진단 기간")
	class Period {

		static final long TEAM = 900011L;
		static final long OPPONENT = 900012L;

		/** 지난 시즌(2023) 3경기 — 8월 첫 3주. */
		static final Instant OLD_SEASON = Instant.parse("2023-08-05T15:00:00Z");
		/** 이번 시즌(2024) 6경기 — 0·3·6·9·12·30일. */
		static final Instant NEW_SEASON = Instant.parse("2025-01-01T15:00:00Z");
		static final int[] NEW_OFFSETS = {0, 3, 6, 9, 12, 30};

		/** 이번 시즌 세 번째 경기까지만 치른 시점. */
		static final Instant MIDSEASON = NEW_SEASON.plus(Duration.ofDays(7));

		@BeforeEach
		void buildTwoSeasons() {
			Competition epl = competitionRepository.findById(EPL).orElseThrow();
			Team team = teamRepository.save(Team.builder()
					.id(TEAM).name("Two Seasons FC").nameKo("두시즌FC").build());
			Team opponent = teamRepository.save(Team.builder()
					.id(OPPONENT).name("Opponent FC").nameKo("상대FC").build());

			for (int i = 0; i < 3; i++) {
				save(920000L + i, epl, 2023, team, opponent,
						OLD_SEASON.plus(Duration.ofDays(7L * i)), FixtureStatus.FT);
			}
			for (int i = 0; i < NEW_OFFSETS.length; i++) {
				save(921000L + i, epl, 2024, team, opponent,
						NEW_SEASON.plus(Duration.ofDays(NEW_OFFSETS[i])), FixtureStatus.FT);
			}
		}

		private void save(long id, Competition competition, int season, Team team, Team opponent,
		                  Instant kickoff, FixtureStatus status) {
			fixtureRepository.save(Fixture.builder()
					.id(id).competition(competition).season(season).round("R")
					.homeTeam(team).awayTeam(opponent).kickoff(kickoff)
					.status(status).goalsHome(1).goalsAway(0).build());
		}

		@Test
		@DisplayName("지난 시즌 경기는 지표에도 목록에도 넣지 않는다 — 뺀 개수는 밝힌다")
		void olderSeasonsAreLeftOut() {
			TeamDiagnostics d = diagnosticsService.diagnose(TEAM,
					DiagnosticsOptions.DEFAULTS.withAsOf(MIDSEASON));

			assertThat(d.window().season()).isEqualTo(2024);
			assertThat(d.window().otherSeasonFixtures()).isEqualTo(3);
			assertThat(d.matches()).extracting(MatchLoad::fixtureId)
					.allSatisfy(id -> assertThat(id).isGreaterThanOrEqualTo(921000L));
			// 두 시즌을 가로질러 세면 밀집도와 폼이 서로 다른 해를 가리킨다 — 그 사실을 남긴다
			assertThat(d.omissions()).extracting(Omission::metric).contains("period");
		}

		@Test
		@DisplayName("치르지 않은 경기는 계산에서 빠지고 목록에는 남는다")
		void upcomingMatchesStayInTheListButOutOfEveryMetric() {
			TeamDiagnostics d = diagnosticsService.diagnose(TEAM,
					DiagnosticsOptions.DEFAULTS.withAsOf(MIDSEASON));

			// 목록은 시즌 전체 — 타임라인이 다가올 일정을 그리고 예측이 걸린다
			assertThat(d.matches()).hasSize(6);
			assertThat(d.matches()).extracting(MatchLoad::inAnalysis)
					.containsExactly(true, true, true, false, false, false);

			// 지표는 치른 3경기만
			assertThat(d.window().analyzedFixtures()).isEqualTo(3);
			assertThat(d.window().upcomingFixtures()).isEqualTo(3);
			assertThat(d.window().seasonInProgress()).isTrue();
			assertThat(d.window().to()).isEqualTo(NEW_SEASON.plus(Duration.ofDays(6)));
			assertThat(d.form().sampleSize()).isEqualTo(3);
			assertThat(d.travel().to()).isEqualTo(d.window().to());
			assertThat(d.congestion().analyzedMatchCount()).isEqualTo(3);
		}

		@Test
		@DisplayName("경기가 적으면 '밀집 없음'이 아니라 '판정 불가'로 답한다")
		void tooFewMatchesMeansUndecidableNotUncongested() {
			TeamDiagnostics d = diagnosticsService.diagnose(TEAM,
					DiagnosticsOptions.DEFAULTS.withAsOf(MIDSEASON));

			assertThat(d.congestion().detectable()).isFalse();
			assertThat(d.congestion().spans()).isEmpty();
			assertThat(d.omissions()).extracting(Omission::metric).contains("congestion");
		}

		@Test
		@DisplayName("시즌이 끝났으면 그 시즌 전체가 기간이 된다")
		void finishedSeasonMeansTheWholeSeason() {
			TeamDiagnostics d = diagnosticsService.diagnose(TEAM,
					DiagnosticsOptions.DEFAULTS.withAsOf(NEW_SEASON.plus(Duration.ofDays(365))));

			assertThat(d.window().analyzedFixtures()).isEqualTo(6);
			assertThat(d.window().upcomingFixtures()).isZero();
			assertThat(d.window().seasonInProgress()).isFalse();
			assertThat(d.congestion().detectable()).isTrue();
		}

		@Test
		@DisplayName("한 경기도 치르지 않은 시즌으로는 넘어가지 않는다 — 일정만 먼저 들어온 경우")
		void doesNotJumpToASeasonWithNoPlayedMatches() {
			// 2024 시즌 일정은 이미 들어와 있지만 첫 경기 전이다.
			TeamDiagnostics d = diagnosticsService.diagnose(TEAM,
					DiagnosticsOptions.DEFAULTS.withAsOf(NEW_SEASON.minus(Duration.ofDays(1))));

			assertThat(d.window().season()).isEqualTo(2023);
			assertThat(d.window().analyzedFixtures()).isEqualTo(3);
		}

		@Test
		@DisplayName("시즌을 직접 지정하면 그 시즌을 본다 — 기간은 인자다")
		void seasonIsAParameter() {
			TeamDiagnostics d = diagnosticsService.diagnose(TEAM,
					DiagnosticsOptions.DEFAULTS.withSeason(2023).withAsOf(MIDSEASON));

			assertThat(d.window().season()).isEqualTo(2023);
			assertThat(d.matches()).hasSize(3);
			assertThat(d.window().otherSeasonFixtures()).isEqualTo(6);
		}

		@Test
		@DisplayName("걸침 시즌인지 아닌지는 대회 시드가 정한다 — 날짜로 되짚지 않는다")
		void seasonBoundaryComesFromTheCompetitionSeed() {
			TeamDiagnostics d = diagnosticsService.diagnose(TEAM,
					DiagnosticsOptions.DEFAULTS.withAsOf(MIDSEASON));

			// 프리미어리그는 해를 걸치는 시즌이다 → 화면이 "2024-25"로 적을 수 있다
			assertThat(d.window().calendarSeason()).isFalse();
		}
	}

	// =========================================================================

	@Test
	@DisplayName("경기 목록이 하나뿐이면 간격도 중앙값도 없다")
	void singleMatchHasNoGaps() {
		syncReplayData();

		TeamDiagnostics d = diagnosticsService.diagnose(CHELSEA);
		List<MatchLoad> matches = d.matches();

		assertThat(matches).hasSize(1);
		assertThat(matches.get(0).gapDays()).isNull();
		assertThat(d.congestion().shortestGapDays()).isNull();
		assertThat(d.congestion().medianGapDays()).isNull();
	}
}
