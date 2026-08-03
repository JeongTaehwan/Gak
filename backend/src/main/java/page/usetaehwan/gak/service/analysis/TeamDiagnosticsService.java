package page.usetaehwan.gak.service.analysis;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import page.usetaehwan.gak.domain.Absence;
import page.usetaehwan.gak.domain.AbsenceReason;
import page.usetaehwan.gak.domain.Competition;
import page.usetaehwan.gak.domain.Fixture;
import page.usetaehwan.gak.domain.Pick;
import page.usetaehwan.gak.domain.Score;
import page.usetaehwan.gak.domain.Team;
import page.usetaehwan.gak.domain.Venue;
import page.usetaehwan.gak.dto.analysis.AbsenceSummary;
import page.usetaehwan.gak.dto.analysis.AnalysisWindow;
import page.usetaehwan.gak.dto.analysis.CongestionReport;
import page.usetaehwan.gak.dto.analysis.CongestionSpanView;
import page.usetaehwan.gak.dto.analysis.FormSummary;
import page.usetaehwan.gak.dto.analysis.MatchLoad;
import page.usetaehwan.gak.dto.analysis.Omission;
import page.usetaehwan.gak.dto.analysis.OpponentStrength;
import page.usetaehwan.gak.dto.analysis.SampleConfidence;
import page.usetaehwan.gak.dto.analysis.TeamDiagnostics;
import page.usetaehwan.gak.dto.analysis.TravelSummary;
import page.usetaehwan.gak.repository.AbsenceRepository;
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

	/** 결장 상위 명단에 몇 명까지 실을지. 화면 한 블록에 들어가는 만큼만. */
	private static final int TOP_ABSENTEES = 5;

	private final FixtureRepository fixtureRepository;
	private final TeamRepository teamRepository;
	private final AbsenceRepository absenceRepository;
	private final OpponentStrengthService opponentStrengthService;
	private final Clock clock;

	public TeamDiagnosticsService(FixtureRepository fixtureRepository,
	                              OpponentStrengthService opponentStrengthService,
	                              TeamRepository teamRepository,
	                              AbsenceRepository absenceRepository,
	                              Clock clock) {
		this.fixtureRepository = fixtureRepository;
		this.teamRepository = teamRepository;
		this.absenceRepository = absenceRepository;
		this.opponentStrengthService = opponentStrengthService;
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

		// 경기별 확정 결장 인원. 결장 데이터가 없는 경기는 이 맵에 아예 없다 —
		// "0명"과 "모름"을 가르는 유일한 장치라 getOrDefault(…, 0) 로 접으면 안 된다.
		List<Absence> absences = absenceRepository.findTeamAbsencesWithPlayer(teamId);
		Map<Long, Integer> outByFixture = outCountByFixture(absences);

		List<IndexSpan> spans = schedule.size() >= options.minMatches()
				? CongestionDetector.detect(schedule.epochDays(), options.windowDays(), options.minMatches())
				: List.of();

		FormResult form = toFormSummary(teamId, all, options.formSize(), omissions);
		// 경기별 상대 순위 — 타임라인이 "vs 아스날 (2위)"를 그릴 수 있게
		Map<Long, Integer> rankByFixture = opponentStrengthService.ranksByFixture(teamId, all);
		// 상대 강도는 폼과 **같은 경기 목록**을 본다. 각자 고르면 분모가 어긋난다.
		OpponentStrength opponents = opponentStrengthService.of(teamId, form.recentFixtures());
		noteOpponentLimits(opponents, omissions);

		return new TeamDiagnostics(
				team.getId(),
				team.displayName(),
				team.getCode(),
				Instant.now(clock),
				schedule.window(all.size()),
				toMatchLoads(teamId, schedule, spans, outByFixture, rankByFixture),
				toCongestionReport(schedule, spans, options, omissions),
				form.summary(),
				opponents,
				toTravelSummary(schedule, options, omissions),
				toAbsenceSummary(schedule, absences, omissions),
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

	private List<MatchLoad> toMatchLoads(Long teamId, Schedule schedule, List<IndexSpan> spans,
	                                     Map<Long, Integer> outByFixture,
	                                     Map<Long, Integer> rankByFixture) {
		Map<Integer, Integer> spanIdByIndex = indexToSpanId(spans);
		List<MatchLoad> loads = new ArrayList<>(schedule.size());

		for (int i = 0; i < schedule.size(); i++) {
			Fixture fixture = schedule.at(i);
			boolean home = schedule.home()[i];
			Team opponent = home ? fixture.getAwayTeam() : fixture.getHomeTeam();
			Competition competition = fixture.getCompetition();

			// 득점은 확정된 경기에서만 준다. 진행 중(LIVE)의 중간 스코어를 실어 보내면
			// 화면이 그걸 최종 결과처럼 그린다 — 폼에서 LIVE를 빼는 이유와 같다.
			boolean resolved = SchedulePolicy.countsForForm(fixture);
			Integer goalsFor = resolved ? ourSide(fixture.getGoalsHome(), fixture.getGoalsAway(), home) : null;
			Integer goalsAgainst = resolved ? ourSide(fixture.getGoalsAway(), fixture.getGoalsHome(), home) : null;

			Score shootout = fixture.getPenalty();
			boolean hasShootout = resolved && shootout != null && shootout.isPresent();

			loads.add(new MatchLoad(
					fixture.getId(),
					fixture.getKickoff(),
					competition.getId(),
					competition.displayName(),
					competition.displayShortName(),
					competition.getType(),
					opponent.getId(),
					opponent.displayName(),
					// 컵이거나 시즌 초라 순위를 말할 수 없으면 null — 0으로 채우지 않는다
					rankByFixture.get(fixture.getId()),
					home,
					fixture.getStatus(),
					fixture.resultFor(teamId),
					goalsFor,
					goalsAgainst,
					hasShootout ? ourSide(shootout.getHome(), shootout.getAway(), home) : null,
					hasShootout ? ourSide(shootout.getAway(), shootout.getHome(), home) : null,
					schedule.gapDays()[i] == NO_GAP ? null : schedule.gapDays()[i],
					spanIdByIndex.get(i),
					SchedulePolicy.extraMinutes(fixture),
					schedule.travelKm()[i],
					outByFixture.get(fixture.getId())));
		}
		return List.copyOf(loads);
	}

	// --- 결장 ----------------------------------------------------------------

	/** 경기 → 확정 결장 인원. 결장 데이터가 있는 경기만 키로 들어간다("0명"과 "모름"의 구분). */
	private Map<Long, Integer> outCountByFixture(List<Absence> absences) {
		Map<Long, Integer> byFixture = new HashMap<>();
		for (Absence absence : absences) {
			Long fixtureId = absence.getFixture().getId();
			// 데이터가 있다는 사실 자체를 먼저 기록한다 — 불투명(DOUBTFUL)만 있는 경기도
			// "그 경기는 확인했고 확정 결장이 0명"이다.
			byFixture.merge(fixtureId, absence.countsAsOut() ? 1 : 0, Integer::sum);
		}
		return byFixture;
	}

	/**
	 * 결장 요약. 분모를 조심해야 한다 — API가 우리 경기 전부의 결장을 주지 않는다
	 * (맨유 2023 시즌은 52경기 중 44경기만 있었다). 그래서 "데이터가 있는 경기 수"를
	 * 함께 실어 보내고, 데이터가 아예 없으면 0으로 채우는 대신 covered=false 로 말한다.
	 */
	private AbsenceSummary toAbsenceSummary(Schedule schedule, List<Absence> absences,
	                                        List<Omission> omissions) {
		if (absences.isEmpty()) {
			omissions.add(Omission.of("absences",
					"결장(부상·징계) 데이터를 아직 동기화하지 않았습니다. "
							+ "POST /api/admin/sync/injuries/{teamId}?season= 으로 받을 수 있습니다."));
			return AbsenceSummary.notCovered(schedule.size());
		}

		// 진단이 보는 경기(연기·취소 제외) 안의 결장만 센다 — 화면에 없는 경기의 결장을
		// 합계에 넣으면 "경기당 평균"이 화면과 안 맞는다.
		Set<Long> visible = new HashSet<>();
		for (int i = 0; i < schedule.size(); i++) {
			visible.add(schedule.at(i).getId());
		}

		Map<Long, Integer> outPerFixture = new HashMap<>();
		Map<AbsenceReason, Integer> byReason = new EnumMap<>(AbsenceReason.class);
		Map<Long, PlayerTally> tallyByPlayer = new HashMap<>();
		Set<Long> coveredFixtures = new HashSet<>();

		for (Absence absence : absences) {
			Long fixtureId = absence.getFixture().getId();
			if (!visible.contains(fixtureId)) {
				continue;
			}
			coveredFixtures.add(fixtureId);
			outPerFixture.putIfAbsent(fixtureId, 0);
			if (!absence.countsAsOut()) {
				continue; // 불투명(Questionable)은 세지 않는다
			}
			outPerFixture.merge(fixtureId, 1, Integer::sum);
			byReason.merge(absence.getReason(), 1, Integer::sum);
			tallyByPlayer
					.computeIfAbsent(absence.getPlayer().getId(),
							id -> new PlayerTally(id, absence.getPlayer().getName()))
					.add(absence.getReason());
		}

		if (coveredFixtures.isEmpty()) {
			omissions.add(Omission.of("absences",
					"받아 둔 결장 데이터가 이 팀의 현재 일정과 맞는 경기를 하나도 갖고 있지 않습니다(시즌 불일치)."));
			return AbsenceSummary.notCovered(schedule.size());
		}
		if (coveredFixtures.size() < schedule.size()) {
			omissions.add(Omission.of("absences", String.format(
					"경기 %d건 중 %d건만 결장 데이터가 있습니다. 나머지는 '결장 0명'이 아니라 '모름'입니다.",
					schedule.size(), coveredFixtures.size())));
		}

		int totalOut = byReason.values().stream().mapToInt(Integer::intValue).sum();
		int maxOut = outPerFixture.values().stream().mapToInt(Integer::intValue).max().orElse(0);
		List<AbsenceSummary.AbsentPlayer> top = tallyByPlayer.values().stream()
				.sorted(Comparator.comparingInt(PlayerTally::matches).reversed()
						.thenComparing(PlayerTally::name))
				.limit(TOP_ABSENTEES)
				.map(PlayerTally::toView)
				.toList();

		return new AbsenceSummary(true, coveredFixtures.size(), schedule.size(),
				totalOut, tallyByPlayer.size(), maxOut, Map.copyOf(byReason), top);
	}

	/** 선수별 결장 집계 — 가장 잦았던 사유까지 함께 센다. */
	private static final class PlayerTally {
		private final long id;
		private final String name;
		private final Map<AbsenceReason, Integer> reasons = new EnumMap<>(AbsenceReason.class);
		private int matches;

		PlayerTally(long id, String name) {
			this.id = id;
			this.name = name;
		}

		void add(AbsenceReason reason) {
			matches++;
			reasons.merge(reason, 1, Integer::sum);
		}

		int matches() {
			return matches;
		}

		String name() {
			return name;
		}

		AbsenceSummary.AbsentPlayer toView() {
			AbsenceReason main = reasons.entrySet().stream()
					.max(Map.Entry.comparingByValue())
					.map(Map.Entry::getKey)
					.orElse(AbsenceReason.OTHER);
			return new AbsenceSummary.AbsentPlayer(id, name, matches, main);
		}
	}

	/** 홈/원정 한 쌍에서 "우리 쪽" 값을 고른다. */
	private static Integer ourSide(Integer homeValue, Integer awayValue, boolean weAreHome) {
		return weAreHome ? homeValue : awayValue;
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

	/**
	 * 폼 요약과 <b>그 계산에 쓰인 경기 목록</b>을 함께 돌려준다.
	 *
	 * <p>상대 강도는 "이 6경기의 상대가 누구였나"를 묻는 것이라 <b>폼과 정확히 같은 목록</b>을
	 * 봐야 한다. 각자 다시 고르면 "6경기 4패"와 "5경기 상대 평균 8위"처럼 분모가 어긋난다.
	 */
	private record FormResult(FormSummary summary, List<Fixture> recentFixtures) {
	}

	private FormResult toFormSummary(Long teamId, List<Fixture> all, int formSize,
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

		// FormSummary.opponentStrength(Double) 는 "상대 순위 평균" 한 칸짜리 옛 자리다.
		// 이제 OpponentStrength 가 그것보다 많은 걸 담으므로(상위권/그 외 분리, 분모, 한계)
		// 여기서는 채우지 않고 TeamDiagnostics 최상위에 따로 싣는다.
		return new FormResult(
				new FormSummary(formSize, sampleSize, List.copyOf(recent),
						wins, draws, losses, points, maxPoints, pointsRate, null, confidence),
				recentFixtures);
	}


	/**
	 * 상대 강도가 <b>말하지 못하는 것</b>을 남긴다.
	 *
	 * <p>값이 나왔다고 끝이 아니다. 컵 경기에는 순위가 없고, 시즌 초 상대는 경기 수가 얇아
	 * 순위를 매기지 않는다. 그 경기들이 분모에서 빠졌다는 사실을 밝히지 않으면 "6경기 상대
	 * 평균 8위"가 실제로는 4경기 기준이라는 걸 아무도 모른다.
	 */
	private void noteOpponentLimits(OpponentStrength o, List<Omission> omissions) {
		if (!o.available()) {
			omissions.add(Omission.of("opponentStrength",
					"상대 강도를 낼 수 없습니다. 최근 경기가 컵 대회이거나(순위표가 없습니다) "
							+ "상대의 경기 수가 적어 순위를 말할 수 없는 시점입니다."));
			return;
		}
		if (o.unmeasured() > 0) {
			omissions.add(Omission.of("opponentStrength",
					"최근 %d경기 중 %d경기는 상대 순위를 매기지 못했습니다(컵 대회이거나 시즌 초). "
							.formatted(o.measured() + o.unmeasured(), o.unmeasured())
							+ "아래 상대 강도는 나머지 %d경기 기준입니다.".formatted(o.measured())));
		}
		if (!o.deductionsKnown()) {
			omissions.add(Omission.of("opponentStrength",
					"순위표를 동기화하지 않아 승점 삭감을 확인하지 못했습니다. "
							+ "삭감된 팀이 있으면 순위가 실제와 다를 수 있습니다."));
		}
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
