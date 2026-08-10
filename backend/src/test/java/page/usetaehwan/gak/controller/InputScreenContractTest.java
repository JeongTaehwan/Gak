package page.usetaehwan.gak.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import page.usetaehwan.gak.service.seed.CompetitionSeeder;
import page.usetaehwan.gak.service.sync.FixtureSyncService;
import page.usetaehwan.gak.support.DatabaseCleaner;

/**
 * 입력 화면이 의존하는 <b>HTTP 계약</b>.
 *
 * <p>여기서 못박는 것은 하나다 — <b>{@code teamId + season} 없이는 답하지 않는다.</b>
 * 시즌을 빠뜨린 요청에 서버가 알아서 답해 주면, 화면이 보는 시즌과 응답의 시즌이 갈리는
 * 경로가 열린다. 그 어긋남은 에러 없이 다른 해의 숫자를 보여 주는 방식으로만 드러나므로,
 * 요청 단계에서 닫아 둔다.
 *
 * <p>필드 이름을 함께 확인하는 이유는 기존 컨트롤러 테스트와 같다 — 서버 record 를 고치면
 * JSON 키가 조용히 따라 바뀌고, 화면은 {@code undefined} 를 그리기 시작한다.
 */
@SpringBootTest
@Import(DatabaseCleaner.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InputScreenContractTest {

	private static final long MAN_UTD = 33L;
	private static final long EPL = 39L;
	private static final long UCL = 2L;

	/** 재생 데이터(그리고 {@code season-override})가 이 시즌이다. */
	private static final int SEASON = 2024;

	@Autowired DatabaseCleaner databaseCleaner;
	@Autowired MockMvc mockMvc;
	@Autowired FixtureSyncService syncService;
	@Autowired CompetitionSeeder competitionSeeder;

	@BeforeEach
	void seedReplayData() {
		databaseCleaner.clearAllButCompetitions();
		competitionSeeder.run(null);
		syncService.syncCompetition(EPL);
		syncService.syncCompetition(UCL);
	}

	@Test
	@DisplayName("시즌을 생략하면 자동 판정한 현재 시즌과 그 시즌 선택 가능 팀을 준다")
	void selectionResolvesSeasonAndTeams() throws Exception {
		mockMvc.perform(get("/api/teams/selection"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.season").value(SEASON))
				.andExpect(jsonPath("$.currentSeason").value(SEASON))
				.andExpect(jsonPath("$.current").value(true))
				// 현재 시즌이므로 앞으로 갈 곳이 없다.
				.andExpect(jsonPath("$.nextSeason").doesNotExist())
				.andExpect(jsonPath("$.teams").isArray());
	}

	@Test
	@DisplayName("1차 비공개 검증 — 저장된 팀이 아니라 맨유 하나만 선택 대상이다")
	void onlyManchesterUnitedIsOffered() throws Exception {
		mockMvc.perform(get("/api/teams/selection").param("teamId", String.valueOf(MAN_UTD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.restricted").value(true))
				.andExpect(jsonPath("$.teams.length()").value(1))
				.andExpect(jsonPath("$.teams[0].teamId").value(MAN_UTD))
				.andExpect(jsonPath("$.selected.teamId").value(MAN_UTD))
				.andExpect(jsonPath("$.selected.eligible").value(true));
	}

	@Test
	@DisplayName("선택 대상이 아닌 시즌이어도 팀을 바꾸지 않는다")
	void ineligibleSeasonKeepsTheTeam() throws Exception {
		mockMvc.perform(get("/api/teams/selection")
						.param("season", "2019")
						.param("teamId", String.valueOf(MAN_UTD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.season").value(2019))
				.andExpect(jsonPath("$.selected.teamId").value(MAN_UTD))
				.andExpect(jsonPath("$.selected.eligible").value(false))
				.andExpect(jsonPath("$.teams").isEmpty());
	}

	@Test
	@DisplayName("없는 팀은 404 — '선택 대상 아님'과 다르다")
	void unknownTeamIsNotFound() throws Exception {
		mockMvc.perform(get("/api/teams/selection").param("teamId", "999999"))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("순위표는 시즌 없이 답하지 않는다")
	void standingsRequireSeason() throws Exception {
		mockMvc.perform(get("/api/teams/{teamId}/standings", MAN_UTD))
				.andExpect(status().isBadRequest());

		mockMvc.perform(get("/api/teams/{teamId}/standings", MAN_UTD)
						.param("season", String.valueOf(SEASON)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.available").exists());
	}

	@Test
	@DisplayName("적중 기록은 시즌 없이 답하지 않고, 답할 때는 어느 시즌인지 밝힌다")
	void predictionsRequireSeasonAndSayWhichOne() throws Exception {
		mockMvc.perform(get("/api/teams/{teamId}/predictions", MAN_UTD))
				.andExpect(status().isBadRequest());

		mockMvc.perform(get("/api/teams/{teamId}/predictions", MAN_UTD)
						.param("season", String.valueOf(SEASON)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.season").value(SEASON))
				// 기록이 없는 것은 "적중률 0%"가 아니다.
				.andExpect(jsonPath("$.scored").value(0))
				.andExpect(jsonPath("$.hitRate").doesNotExist());
	}

	@Test
	@DisplayName("질문은 시즌 없이 받지 않는다")
	void questionRequiresSeason() throws Exception {
		mockMvc.perform(post("/api/teams/{teamId}/questions", MAN_UTD)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"question\":\"왜 부진한가요?\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("빈 질문은 받지 않는다")
	void blankQuestionIsRejected() throws Exception {
		mockMvc.perform(post("/api/teams/{teamId}/questions", MAN_UTD)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"season\":2024,\"question\":\"   \"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("답하지 못해도 200 — 화면이 네 가지 상태를 갈라 말할 수 있어야 한다")
	void unanswerableIsStillATwoHundred() throws Exception {
		mockMvc.perform(post("/api/teams/{teamId}/questions", MAN_UTD)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"season\":2024,\"question\":\"왜 부진한가요?\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").exists())
				.andExpect(jsonPath("$.statusMessage").exists())
				// 분모는 답을 못 냈을 때도 함께 온다.
				.andExpect(jsonPath("$.basis.season").value(2024))
				.andExpect(jsonPath("$.basis.analyzedFixtures").exists())
				.andExpect(jsonPath("$.basis.seasonFixtures").exists());
	}
}
