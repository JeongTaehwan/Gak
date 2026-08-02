package page.usetaehwan.gak.service.analysis;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import page.usetaehwan.gak.domain.Fixture;
import page.usetaehwan.gak.domain.Pick;
import page.usetaehwan.gak.domain.Team;
import page.usetaehwan.gak.domain.Venue;
import page.usetaehwan.gak.dto.analysis.AnalysisWindow;
import page.usetaehwan.gak.dto.analysis.CongestionReport;
import page.usetaehwan.gak.dto.analysis.CongestionSpanView;
import page.usetaehwan.gak.dto.analysis.FormSummary;
import page.usetaehwan.gak.dto.analysis.MatchLoad;
import page.usetaehwan.gak.dto.analysis.Omission;
import page.usetaehwan.gak.dto.analysis.SampleConfidence;
import page.usetaehwan.gak.dto.analysis.TeamDiagnostics;
import page.usetaehwan.gak.dto.analysis.TravelSummary;
import page.usetaehwan.gak.repository.FixtureRepository;
import page.usetaehwan.gak.repository.TeamRepository;
import page.usetaehwan.gak.service.analysis.CongestionDetector.IndexSpan;

/**
 * 팀 진단 계산의 조립부. 경기 "사실"을 읽어 파생값(간격·밀집도·폼·이동거리)을 만든다.
 *
 * <h2>저장하지 않는다</h2>
 * <p>여기서 나온 어떤 값도 DB에 쓰지 않는다. 파생값을 저장하면 원본이 바뀌는 순간
 * 조용히 틀린 값이 남는다 — 연기된 경기의 새 날짜가 들어오면 밀집 구간·간격·이동거리가
 * 모두 달라져야 하는데, 저장된 값은 아무도 다시 계산해 주지 않는 한 그대로다. 그리고
 * "다시 계산해 주는 사람"을 만드는 일(무효화 처리)이 애초에 계산하는 일보다 훨씬 어렵다.
 *
 * <h2>읽기 한 번</h2>
 * <p>모든 지표가 <b>같은 경기 목록</b>에서 나온다. 질의는
 * {@link FixtureRepository#findTeamScheduleWithDetails(Long)} 한 번뿐이고(N+1 방지),
 * 나머지는 메모리 안에서의 O(n) 계산이다. 지표마다 따로 질의하면 그 사이에 동기화가
 * 끼어들어 밀집도와 폼이 서로 다른 스냅샷을 보게 된다.
 *
 * <h2>상태를 필드에 두지 않는다</h2>
 * <p>스프링 빈은 싱글턴이라 여러 요청이 <b>같은 인스턴스</b>를 동시에 쓴다. 계산 중간값을
 * 필드에 담으면 두 사용자의 진단이 서로 섞인다. 한 번의 계산에 필요한 상태는
 * {@link Schedule}에 모아 지역 변수로만 들고 다닌다.
 *
 * <h2>날짜 경계</h2>
 * <p>간격·창 폭은 <b>UTC 날짜</b> 기준의 일수 차이로 잰다(킥오프가 UTC로 저장돼 있다).
 * 시각 차이(시간 단위)로 재지 않는 이유는, 같은 "이틀 뒤"라도 12:30 킥오프 뒤 20:00
 * 킥오프면 1.3일, 20:00 뒤 12:30이면 1.7일이 되어 반올림 위치에 따라 간격이 들쭉날쭉해지기
 * 때문이다. 사람이 "며칠 만의 경기"라고 말할 때 세는 것도 날짜지 시간이 아니다.
 */
@Service
public class TeamDiagnosticsService {

	private static final int POINTS_FOR_WIN = 3;
	private static final int POINTS_FOR_DRAW = 1;

	/** "값 없음"을 뜻하는 간격(목록의 첫 경기). */
	private static final int NO_GAP = -1;

	private final FixtureRepository fixtureRepository;
	private final TeamRepository teamRepository;
	private final Clock clock;

	public TeamDiagnosticsService(FixtureRepository fixtureRepository,
	                              TeamRepository teamRepository,
	                              Clock clock) {
		this.fixtureRepository = fixtureRepository;
		this.teamRepository = teamRepository;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public TeamDiagnostics diagnose(Long teamId) {
		return diagnose(teamId, DiagnosticsOptions.DEFAULTS);
	}

	/**
	 * @throws NoSuchElementException 팀이 없을 때
	 */
	@Transactional(readOnly = true)
	public TeamDiagnostics diagnose(Long teamId, DiagnosticsOptions options) {
		Team team = teamRepository.findById(teamId)
				.orElseThrow(() -> new NoSuchElementException("팀을 찾을 수 없습니다. teamId=" + teamId));

		List<Fixture> all = fixtureRepository.findTeamScheduleWithDetails(teamId);
		Schedule schedule = Schedule.of(team, all);
		List<Omission> omissions = new ArrayList<>();

		List<IndexSpan> spans = schedule.size() >= options.minMatches()
				? CongestionDetector.detect(schedule.epochDays(), options.windowDays(), options.minMatches())
				: List.of();

		return new TeamDiagnostics(
				team.getId(),
				team.displayName(),
				Instant.now(clock),
				schedule.window(all.size()),
				toMatchLoads(schedule, spans),
				toCongestionReport(schedule, spans, options, omissions),
				toFormSummary(teamId, all, options.formSize(), omissions),
				toTravelSummary(schedule, options, omissions),
				List.copyOf(omissions));
	}

	// --- 한 번의 계산이 들고 다니는 상태 --------------------------------------

	/**
	 * 진단 대상 팀 관점으로 정리한 경기 목록. 여러 지표가 같은 배열을 나눠 쓰므로
	 * 한 번만 만들어 넘긴다(간격을 세 번 다시 계산하지 않게).
	 *
	 * <p>배열들은 모두 {@link #fixtures}와 같은 순서·같은 길이다. i번째 원소는 전부
	 * i번째 경기의 값이다.
	 */
	private record Schedule(
			List<Fixture> fixtures,
			/** UTC 에포크 일수(오름차순). 간격·창 계산의 단일 근거. */
			long[] epochDays,
			/** 직전 경기와의 간격(일). 첫 경기는 {@link #NO_GAP}. */
			int[] gapDays,
			/** 진단 대상 팀이 홈이면 true. */
			boolean[] home,
			/** 이동거리(km). 홈경기는 0.0, 원정인데 좌표를 모르면 null. */
			Double[] travelKm
	) {

		static Schedule of(Team team, List<Fixture> all) {
			// 일정에 실재하는 경기만 남긴다(연기·취소·중단 제외). 규칙은 SchedulePolicy에 모여 있다.
			List<Fixture> fixtures = all.stream().filter(SchedulePolicy::countsForSchedule).toList();
			int n = fixtures.size();

			long[] epochDays = new long[n];
			int[] gapDays = new int[n];
			boolean[] home = new boolean[n];
			Double[] travelKm = new Double[n];
			Venue homeVenue = team.getHomeVenue();

			for (int i = 0; i < n; i++) {
				Fixture fixture = fixtures.get(i);
				epochDays[i] = fixture.getKickoff().atZone(ZoneOffset.UTC).toLocalDate().toEpochDay();
				gapDays[i] = (i == 0) ? NO_GAP : (int) (epochDays[i] - epochDays[i - 1]);
				home[i] = fixture.getHomeTeam().getId().equals(team.getId());
				travelKm[i] = travelFor(homeVenue, fixture.getVenue(), home[i]);
			}
			return new Schedule(fixtures, epochDays, gapDays, home, travelKm);
		}

		/**
		 * "홈 구장 → 경기장" 편도 거리. 홈경기는 이동이 없으므로 좌표를 몰라도 0이다
		 * (모르는 게 아니라 없는 것). 원정인데 좌표가 없으면 null — 0과 구분해야 한다.
		 *
		 * <p>여기서 한 번 반올림해 둔다. 도시 좌표로 잰 값에 소수점 13자리를 실어 보내면
		 * 있지도 않은 정밀도를 주장하는 셈이고, 구간 합계가 화면에 보이는 경기별 값의
		 * 합과 미묘하게 어긋나게 된다.
		 */
		private static Double travelFor(Venue homeVenue, Venue venue, boolean isHome) {
			if (isHome) {
				return 0.0;
			}
			Double km = Haversine.distanceKm(homeVenue, venue);
			return km == null ? null : round1(km);
		}

		int size() {
			return fixtures.size();
		}

		Fixture at(int i) {
			return fixtures.get(i);
		}

		AnalysisWindow window(int totalFixtures) {
			return new AnalysisWindow(
					fixtures.isEmpty() ? null : fixtures.get(0).getKickoff(),
					fixtures.isEmpty() ? null : fixtures.get(size() - 1).getKickoff(),
					totalFixtures,
					size(),
					totalFixtures - size());
		}
	}

	// --- 경기별 부하 ---------------------------------------------------------

	private List<MatchLoad> toMatchLoads(Schedule schedule, List<IndexSpan> spans) {
		Map<Integer, Integer> spanIdByIndex = indexToSpanId(spans);
		List<MatchLoad> loads = new ArrayList<>(schedule.size());

		for (int i = 0; i < schedule.size(); i++) {
			Fixture fixture = schedule.at(i);
			Team opponent = schedule.home()[i] ? fixture.getAwayTeam() : fixture.getHomeTeam();
			loads.add(new MatchLoad(
					fixture.getId(),
					fixture.getKickoff(),
					fixture.getCompetition().getId(),
					fixture.getCompetition().displayName(),
					opponent.getId(),
					opponent.displayName(),
					schedule.home()[i],
					fixture.getStatus(),
					schedule.gapDays()[i] == NO_GAP ? null : schedule.gapDays()[i],
					spanIdByIndex.get(i),
					SchedulePolicy.extraMinutes(fixture),
					schedule.travelKm()[i]));
		}
		return List.copyOf(loads);
	}

	// --- 밀집도 --------------------------------------------------------------

	/** 경기 인덱스 → 소속 밀집 구간 id. 구간은 병합돼 서로 겹치지 않으므로 한 경기는 한 구간에만 속한다. */
	private Map<Integer, Integer> indexToSpanId(List<IndexSpan> spans) {
		Map<Integer, Integer> byIndex = new HashMap<>();
		for (int spanId = 0; spanId < spans.size(); spanId++) {
			IndexSpan span = spans.get(spanId);
			for (int i = span.startIdx(); i <= span.endIdx(); i++) {
				byIndex.put(i, spanId);
			}
		}
		return byIndex;
	}

	private CongestionReport toCongestionReport(Schedule schedule, List<IndexSpan> spans,
	                                            DiagnosticsOptions options, List<Omission> omissions) {
		boolean detectable = schedule.size() >= options.minMatches();
		if (!detectable) {
			omissions.add(Omission.of("congestion", String.format(
					"밀집 판정에는 최소 %d경기가 필요한데 이 팀의 경기가 %d건뿐입니다.",
					options.minMatches(), schedule.size())));
		}

		List<CongestionSpanView> views = new ArrayList<>(spans.size());
		for (int spanId = 0; spanId < spans.size(); spanId++) {
			views.add(toSpanView(spanId, spans.get(spanId), schedule));
		}

		return CongestionReport.of(
				options.windowDays(),
				options.minMatches(),
				detectable,
				schedule.size(),
				schedule.size() == 0
						? 0
						: CongestionDetector.busiestWindowMatchCount(schedule.epochDays(), options.windowDays()),
				shortestGap(schedule.gapDays()),
				medianGap(schedule.gapDays()),
				views);
	}

	private CongestionSpanView toSpanView(int spanId, IndexSpan span, Schedule schedule) {
		int away = 0;
		int extraTimeMatches = 0;
		int extraMinutes = 0;
		int shortestGap = Integer.MAX_VALUE;
		double travelSum = 0.0;
		int travelUnknown = 0;
		boolean travelMeasured = false;

		for (int i = span.startIdx(); i <= span.endIdx(); i++) {
			Fixture fixture = schedule.at(i);

			if (!schedule.home()[i]) {
				away++;
				Double km = schedule.travelKm()[i];
				if (km == null) {
					travelUnknown++;
				} else {
					travelSum += km;
					travelMeasured = true;
				}
			}
			if (SchedulePolicy.wentToExtraTime(fixture)) {
				extraTimeMatches++;
				extraMinutes += SchedulePolicy.extraMinutes(fixture);
			}
			// 구간 안의 간격만 본다 — 구간 첫 경기의 gap은 구간 밖(직전 경기)과의 간격이다.
			if (i > span.startIdx()) {
				shortestGap = Math.min(shortestGap, schedule.gapDays()[i]);
			}
		}

		return new CongestionSpanView(
				spanId,
				schedule.at(span.startIdx()).getId(),
				schedule.at(span.endIdx()).getId(),
				schedule.at(span.startIdx()).getKickoff(),
				schedule.at(span.endIdx()).getKickoff(),
				(int) (schedule.epochDays()[span.endIdx()] - schedule.epochDays()[span.startIdx()]),
				span.size(),
				away,
				extraTimeMatches,
				extraMinutes,
				shortestGap == Integer.MAX_VALUE ? 0 : shortestGap,
				travelMeasured ? round1(travelSum) : null,
				travelUnknown);
	}

	private Integer shortestGap(int[] gapDays) {
		int shortest = Integer.MAX_VALUE;
		for (int i = 1; i < gapDays.length; i++) {
			shortest = Math.min(shortest, gapDays[i]);
		}
		return shortest == Integer.MAX_VALUE ? null : shortest;
	}

	/** 간격의 중앙값. 평균을 쓰지 않는 이유는 {@link CongestionReport} 참고. */
	private Double medianGap(int[] gapDays) {
		if (gapDays.length < 2) {
			return null;
		}
		int[] gaps = Arrays.copyOfRange(gapDays, 1, gapDays.length);
		Arrays.sort(gaps);
		int mid = gaps.length / 2;
		double median = (gaps.length % 2 == 1)
				? gaps[mid]
				: (gaps[mid - 1] + gaps[mid]) / 2.0;
		return round1(median);
	}

	// --- 폼 ------------------------------------------------------------------

	private FormSummary toFormSummary(Long teamId, List<Fixture> all, int formSize,
	                                  List<Omission> omissions) {
		// 결과가 확정된 경기만. LIVE는 아직 사실이 아니라 여기서 빠진다(SchedulePolicy 참고).
		List<Fixture> finished = all.stream().filter(SchedulePolicy::countsForForm).toList();
		List<Fixture> recentFixtures = finished.size() <= formSize
				? finished
				: finished.subList(finished.size() - formSize, finished.size());

		List<Pick> recent = new ArrayList<>(recentFixtures.size());
		int wins = 0;
		int draws = 0;
		int losses = 0;
		for (Fixture fixture : recentFixtures) {
			Pick result = fixture.resultFor(teamId);
			if (result == null) {
				continue;
			}
			recent.add(result);
			switch (result) {
				case W -> wins++;
				case D -> draws++;
				case L -> losses++;
			}
		}

		int sampleSize = recent.size();
		int points = wins * POINTS_FOR_WIN + draws * POINTS_FOR_DRAW;
		int maxPoints = sampleSize * POINTS_FOR_WIN;
		SampleConfidence confidence = SampleConfidence.of(sampleSize);

		Double pointsRate = null;
		if (confidence.allowsRates()) {
			pointsRate = round3((double) points / maxPoints);
		} else {
			omissions.add(Omission.of("pointsRate", String.format(
					"승점률은 %d경기 이상일 때만 냅니다. 확정된 경기가 %d건이라 승·무·패 개수만 제공합니다.",
					SampleConfidence.MIN_SAMPLE_FOR_RATE, sampleSize)));
		}

		// 상대 강도 = 붙은 상대들의 순위 평균. 우리는 순위표를 저장하지 않는다(API의 /standings를
		// 아직 동기화하지 않는다). 없는 값을 "평균 10위" 같은 그럴듯한 기본값으로 채우면
		// 다음 단계의 AI 진단이 그걸 사실로 읽는다. 그래서 null + 이유로 남긴다.
		omissions.add(Omission.of("opponentStrength",
				"상대 강도는 순위 데이터가 필요합니다. 현재 순위표(/standings)를 동기화하지 않아 계산을 생략합니다."));

		return new FormSummary(formSize, sampleSize, List.copyOf(recent),
				wins, draws, losses, points, maxPoints, pointsRate, null, confidence);
	}

	// --- 이동거리 ------------------------------------------------------------

	private TravelSummary toTravelSummary(Schedule schedule, DiagnosticsOptions options,
	                                      List<Omission> omissions) {
		int awayMatches = 0;
		int measured = 0;
		int unknown = 0;
		double total = 0.0;
		double longest = 0.0;

		for (int i = 0; i < schedule.size(); i++) {
			Fixture fixture = schedule.at(i);
			if (!options.travelPeriodContains(fixture.getKickoff()) || schedule.home()[i]) {
				continue;
			}
			awayMatches++;
			Double km = schedule.travelKm()[i];
			if (km == null) {
				unknown++;
			} else {
				measured++;
				total += km;
				longest = Math.max(longest, km);
			}
		}

		if (unknown > 0) {
			omissions.add(Omission.of("travelDistance", String.format(
					"원정 %d경기 중 %d경기는 경기장 좌표가 없어 이동거리에 넣지 못했습니다. 합계는 부분합입니다.",
					awayMatches, unknown)));
		}

		if (measured == 0) {
			return new TravelSummary(options.travelFrom(), options.travelTo(),
					awayMatches, 0, unknown, null, null, null);
		}
		return new TravelSummary(options.travelFrom(), options.travelTo(),
				awayMatches, measured, unknown,
				round1(total), round1(total / measured), round1(longest));
	}

	// --- 반올림 --------------------------------------------------------------

	private static double round1(double value) {
		return Math.round(value * 10.0) / 10.0;
	}

	private static double round3(double value) {
		return Math.round(value * 1000.0) / 1000.0;
	}
}
