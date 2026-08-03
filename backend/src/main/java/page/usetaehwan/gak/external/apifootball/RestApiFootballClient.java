package page.usetaehwan.gak.external.apifootball;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import page.usetaehwan.gak.config.ApiFootballProperties;
import page.usetaehwan.gak.domain.SyncSource;
import page.usetaehwan.gak.external.apifootball.dto.ApiFootballEnvelope;
import page.usetaehwan.gak.external.apifootball.dto.FixtureItem;
import page.usetaehwan.gak.external.apifootball.dto.InjuryItem;
import page.usetaehwan.gak.external.apifootball.dto.StandingItem;

/**
 * 실제 API-Football 호출. {@code gak.api-football.mode=real} 일 때만 빈으로 올라온다.
 *
 * <p>세 가지를 신경 쓴다.
 * <ol>
 *   <li><b>타임아웃</b> — 응답 없는 커넥션이 스케줄러 스레드를 붙잡으면 그날 동기화가 통째로 멈춘다.</li>
 *   <li><b>errors 필드</b> — HTTP 200에도 본문에 오류가 실린다. 파서가 잡아 예외로 바꾼다.</li>
 *   <li><b>capture</b> — 받은 원본을 그대로 파일로 떨어뜨린다. 추가 요청 없이 replay 파일이 쌓인다.</li>
 * </ol>
 */
@Component
@ConditionalOnProperty(prefix = "gak.api-football", name = "mode", havingValue = "real")
public class RestApiFootballClient implements ApiFootballClient {

	private static final Logger log = LoggerFactory.getLogger(RestApiFootballClient.class);

	/** 남은 일일 할당량이 실려 오는 응답 헤더. 우리 예산 계산과 대조할 수 있다. */
	private static final String REMAINING_HEADER = "x-ratelimit-requests-remaining";

	private final RestClient restClient;
	private final ApiFootballResponseParser parser;
	private final ApiFootballProperties properties;

	public RestApiFootballClient(RestClient apiFootballRestClient,
	                             ApiFootballResponseParser parser,
	                             ApiFootballProperties properties) {
		this.restClient = apiFootballRestClient;
		this.parser = parser;
		this.properties = properties;
		log.warn("API-Football: REAL 모드 — 호출마다 일일 할당량(무료 100)을 소모합니다.");
	}

	@Override
	public SyncSource source() {
		return SyncSource.REAL;
	}

	@Override
	public FixturesFetch fetchFixtures(long leagueId, int season) {
		String context = "fixtures league=" + leagueId + " season=" + season;
		String body = get("/fixtures", Map.of("league", leagueId, "season", season), context,
				ReplayResources.fixturesFileName(leagueId, season));

		// errors 필드 확인은 파서가 한다(실 호출/재생 공통 경로).
		ApiFootballEnvelope<FixtureItem> envelope = parser.parse(body, FixtureItem.class, context, 1);
		return new FixturesFetch(envelope.responseOrEmpty(), 1);
	}

	@Override
	public StandingsFetch fetchStandings(long leagueId, int season) {
		String context = "standings league=" + leagueId + " season=" + season;
		String body = get("/standings", Map.of("league", leagueId, "season", season), context,
				ReplayResources.standingsFileName(leagueId, season));
		ApiFootballEnvelope<StandingItem> envelope = parser.parse(body, StandingItem.class, context, 1);
		return new StandingsFetch(envelope.responseOrEmpty(), 1);
	}

	@Override
	public InjuriesFetch fetchInjuries(long teamId, int season) {
		String context = "injuries team=" + teamId + " season=" + season;
		String body = get("/injuries", Map.of("team", teamId, "season", season), context,
				ReplayResources.injuriesFileName(teamId, season));

		ApiFootballEnvelope<InjuryItem> envelope = parser.parse(body, InjuryItem.class, context, 1);
		return new InjuriesFetch(envelope.responseOrEmpty(), 1);
	}

	/**
	 * GET 한 번 — 오류 처리와 capture 를 한곳에 모은다.
	 *
	 * <p>엔드포인트가 늘 때마다 이 30줄을 복사하면, 언젠가 한 곳에서만 타임아웃 처리를
	 * 빠뜨린다. 그 빠뜨림은 "스케줄러 스레드가 붙잡혀 그날 동기화가 통째로 멈추는" 방식으로만
	 * 드러난다.
	 *
	 * @param captureFileName 받은 원본을 저장할 파일명(replay 규약)
	 */
	private String get(String path, Map<String, Object> params, String context,
	                   String captureFileName) {
		ResponseEntity<String> entity;
		try {
			entity = restClient.get()
					.uri(uri -> {
						uri.path(path);
						params.forEach(uri::queryParam);
						return uri.build();
					})
					.retrieve()
					// 4xx/5xx 도 할당량은 이미 나간 것으로 본다 → consumed = 1
					.onStatus(HttpStatusCode::isError, (request, response) -> {
						throw new ApiFootballException(
								"API-Football HTTP 오류 (" + context + "): " + response.getStatusCode(), 1);
					})
					.toEntity(String.class);
		} catch (ApiFootballException e) {
			throw e;
		} catch (ResourceAccessException e) {
			// 커넥션 실패·타임아웃. 요청이 나갔는지 알 수 없으므로 보수적으로 소모로 계산한다.
			throw new ApiFootballException(
					"API-Football 통신 실패/타임아웃 (" + context + "): " + e.getMessage(), 1, e);
		} catch (RuntimeException e) {
			throw new ApiFootballException(
					"API-Football 호출 중 예외 (" + context + "): " + e.getMessage(), 1, e);
		}

		String body = entity.getBody();
		logRemainingQuota(entity);
		capture(captureFileName, body);
		return body;
	}

	private void logRemainingQuota(ResponseEntity<String> entity) {
		String remaining = entity.getHeaders().getFirst(REMAINING_HEADER);
		if (remaining != null) {
			log.info("API-Football 잔여 일일 요청: {}", remaining);
		}
	}

	/**
	 * 받은 원본을 replay 파일명 규약대로 저장한다. 실패해도 동기화를 막지 않는다
	 * — 이미 할당량을 쓴 응답이므로 파일 저장 실패로 버릴 이유가 없다.
	 */
	private void capture(String fileName, String body) {
		String dir = properties.captureDir();
		if (dir == null || dir.isBlank() || body == null) {
			return;
		}
		Path target = Path.of(dir).resolve(fileName);
		try {
			Files.createDirectories(target.getParent());
			Files.writeString(target, body, StandardCharsets.UTF_8);
			log.info("응답 원본 저장: {}", target.toAbsolutePath());
		} catch (IOException e) {
			log.warn("응답 원본 저장 실패({}): {}", target, e.getMessage());
		}
	}
}
