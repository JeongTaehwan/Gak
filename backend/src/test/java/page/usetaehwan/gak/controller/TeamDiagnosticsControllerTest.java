package page.usetaehwan.gak.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import page.usetaehwan.gak.repository.FixtureRepository;
import page.usetaehwan.gak.repository.SyncLogRepository;
import page.usetaehwan.gak.repository.TeamRepository;
import page.usetaehwan.gak.repository.VenueRepository;
import page.usetaehwan.gak.service.seed.CompetitionSeeder;
import page.usetaehwan.gak.service.sync.FixtureSyncService;
import page.usetaehwan.gak.support.DatabaseCleaner;

/**
 * 진단 조회 엔드포인트의 <b>HTTP 계약</b>을 못박는다. 계산 자체는
 * {@code TeamDiagnosticsServiceTest}가 검증하므로, 여기서는 프론트가 실제로 의존하는
 * 것들만 본다 — 응답에 어떤 키가 있는지, 값이 없을 때 무엇이 오는지, 오류가 몇 번인지.
 *
 * <p>필드 이름을 여기서 한 번 더 확인하는 이유: 서버 record의 필드명을 바꾸면 JSON 키가
 * 조용히 따라 바뀌고, 화면은 {@code undefined}를 그리기 시작한다. 컴파일러가 잡아 주지
 * 않는 유일한 경계라서 테스트가 대신 지킨다.
 */
@SpringBootTest
@Import(DatabaseCleaner.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TeamDiagnosticsControllerTest {

	private static final long MAN_UTD = 33L;
	private static final long EPL = 39L;
	private static final long UCL = 2L;

	@Autowired DatabaseCleaner databaseCleaner;
	@Autowired MockMvc mockMvc;
	@Autowired FixtureSyncService syncService;
	@Autowired CompetitionSeeder competitionSeeder;
	@Autowired FixtureRepository fixtureRepository;
	@Autowired TeamRepository teamRepository;
	@Autowired VenueRepository venueRepository;
	@Autowired SyncLogRepository syncLogRepository;

	@BeforeEach
	void seedReplayData() {
		databaseCleaner.clearAllButCompetitions();
		competitionSeeder.run(null);
		syncService.syncCompetition(EPL);
		syncService.syncCompetition(UCL);
	}

	@Test
	@DisplayName("팀의 전 대회 경기를 날짜순 하나의 목록으로 내려준다")
	void returnsOneScheduleAcrossCompetitions() throws Exception {
		mockMvc.perform(get("/api/teams/{teamId}/diagnostics", MAN_UTD))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.teamName").value("맨유"))
				.andExpect(jsonPath("$.teamCode").value("MUN"))
				.andExpect(jsonPath("$.matches.length()").value(3))
				// 리그 2건 + 챔스 1건이 킥오프 순으로 섞여 있다
				.andExpect(jsonPath("$.matches[0].competitionId").value(EPL))
				.andExpect(jsonPath("$.matches[2].competitionId").value(UCL))
				.andExpect(jsonPath("$.matches[2].competitionType").value("HYBRID"))
				.andExpect(jsonPath("$.matches[2].competitionShortName").value("챔스"));
	}

	@Test
	@DisplayName("화면이 한 줄을 그리는 데 필요한 값이 모두 실려 온다")
	void carriesEverythingOneTimelineRowNeeds() throws Exception {
		mockMvc.perform(get("/api/teams/{teamId}/diagnostics", MAN_UTD))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.matches[0].fixtureId").exists())
				.andExpect(jsonPath("$.matches[0].kickoff").exists())
				.andExpect(jsonPath("$.matches[0].opponentName").value("리버풀"))
				.andExpect(jsonPath("$.matches[0].home").value(true))
				.andExpect(jsonPath("$.matches[0].result").value("L"))
				.andExpect(jsonPath("$.matches[0].goalsFor").value(1))
				.andExpect(jsonPath("$.matches[0].goalsAgainst").value(3))
				// 첫 경기는 앞 경기가 없으므로 간격이 없다 — 0이 아니라 null
				.andExpect(jsonPath("$.matches[0].gapDays").doesNotExist())
				.andExpect(jsonPath("$.matches[1].gapDays").value(8));
	}

	@Test
	@DisplayName("밀집 구간 경계를 인덱스가 아니라 경기 id로 준다")
	void congestionSpansAreKeyedByFixtureId() throws Exception {
		mockMvc.perform(get("/api/teams/{teamId}/diagnostics", MAN_UTD)
						// 경기 3건으로도 구간이 잡히도록 기준을 낮춰 부른다
						.param("windowDays", "40").param("minMatches", "3"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.congestion.spans.length()").value(1))
				.andExpect(jsonPath("$.congestion.spans[0].startFixtureId").value(1208021))
				.andExpect(jsonPath("$.congestion.spans[0].endFixtureId").value(1310001))
				.andExpect(jsonPath("$.congestion.spans[0].startIdx").doesNotExist());
	}

	@Test
	@DisplayName("표본이 적으면 판정 불가임을 응답에 담는다 — 빈 목록으로 얼버무리지 않는다")
	void smallSampleIsReportedNotHidden() throws Exception {
		mockMvc.perform(get("/api/teams/{teamId}/diagnostics", MAN_UTD))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.congestion.detectable").value(false))
				.andExpect(jsonPath("$.congestion.spans").isEmpty())
				.andExpect(jsonPath("$.form.confidence").value("LOW"))
				.andExpect(jsonPath("$.form.pointsRate").doesNotExist())
				.andExpect(jsonPath("$.omissions[?(@.metric=='congestion')]").exists());
	}

	@Test
	@DisplayName("없는 팀은 404")
	void unknownTeamIsNotFound() throws Exception {
		mockMvc.perform(get("/api/teams/{teamId}/diagnostics", 999999))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404));
	}

	@Test
	@DisplayName("말이 안 되는 계산 기준은 400 — 서버가 그대로 믿지 않는다")
	void invalidOptionsAreRejected() throws Exception {
		mockMvc.perform(get("/api/teams/{teamId}/diagnostics", MAN_UTD)
						.param("minMatches", "1"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));
	}
}
