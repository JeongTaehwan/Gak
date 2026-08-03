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
import org.springframework.test.context.ActiveProfiles;
import page.usetaehwan.gak.domain.Competition;
import page.usetaehwan.gak.domain.CompetitionType;
import page.usetaehwan.gak.domain.Fixture;
import page.usetaehwan.gak.domain.FixtureStatus;
import page.usetaehwan.gak.domain.Pick;
import page.usetaehwan.gak.domain.Team;
import page.usetaehwan.gak.domain.Venue;
import page.usetaehwan.gak.dto.analysis.CongestionSpanView;
import page.usetaehwan.gak.dto.analysis.MatchLoad;
import page.usetaehwan.gak.dto.analysis.Omission;
import page.usetaehwan.gak.dto.analysis.SampleConfidence;
import page.usetaehwan.gak.dto.analysis.TeamDiagnostics;
import page.usetaehwan.gak.repository.CompetitionRepository;
import page.usetaehwan.gak.repository.FixtureRepository;
import page.usetaehwan.gak.repository.SyncLogRepository;
import page.usetaehwan.gak.repository.TeamRepository;
import page.usetaehwan.gak.repository.VenueRepository;
import page.usetaehwan.gak.service.seed.CompetitionSeeder;
import page.usetaehwan.gak.service.sync.FixtureSyncService;

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
@ActiveProfiles("test")
class TeamDiagnosticsServiceTest {

	private static final long EPL = 39L;
	private static final long UCL = 2L;

	private static final long MAN_UTD = 33L;
	private static final long MAN_CITY = 50L;
	private static final long ARSENAL = 42L;
	private static final long CHELSEA = 49L;

	@Autowired TeamDiagnosticsService diagnosticsService;
	@Autowired FixtureSyncService syncService;
	@Autowired CompetitionSeeder competitionSeeder;
	@Autowired CompetitionRepository competitionRepository;
	@Autowired FixtureRepository fixtureRepository;
	@Autowired TeamRepository teamRepository;
	@Autowired VenueRepository venueRepository;
	@Autowired SyncLogRepository syncLogRepository;

	@BeforeEach
	void reset() {
		fixtureRepository.deleteAll();
		syncLogRepository.deleteAll();
		teamRepository.deleteAll();
		venueRepository.deleteAll();
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

			assertThat(d.window().totalFixtures()).isEqualTo(2);
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
		@DisplayName("기간을 좁히면 그 기간의 원정만 누적한다")
		void accumulatesTravelForGivenPeriod() {
			TeamDiagnostics all = diagnosticsService.diagnose(TEAM);
			assertThat(all.travel().awayMatches()).isEqualTo(3);
			assertThat(all.travel().totalKm()).isCloseTo(786.0, within(1.5));
			assertThat(all.travel().longestTripKm()).isCloseTo(262.0, within(1.0));

			DiagnosticsOptions burstOnly = DiagnosticsOptions.DEFAULTS
					.withTravelPeriod(BASE, BASE.plus(Duration.ofDays(12)));
			TeamDiagnostics burst = diagnosticsService.diagnose(TEAM, burstOnly);

			assertThat(burst.travel().awayMatches()).isEqualTo(2);
			assertThat(burst.travel().totalKm()).isCloseTo(524.0, within(1.0));
			assertThat(burst.travel().averageKmPerMeasuredMatch()).isCloseTo(262.0, within(1.0));
			// 기간을 좁혀도 일정·밀집 계산은 전체를 본다(이동거리만 기간 지표다)
			assertThat(burst.matches()).hasSize(6);
		}

		@Test
		@DisplayName("기준을 바꾸면 결과도 바뀐다 — 기준은 저장값이 아니라 인자다")
		void thresholdsAreParameters() {
			DiagnosticsOptions strict = new DiagnosticsOptions(7, 5, 6, null, null);
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
