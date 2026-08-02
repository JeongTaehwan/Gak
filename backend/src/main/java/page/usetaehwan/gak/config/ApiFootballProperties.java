package page.usetaehwan.gak.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * API-Football 접속 설정.
 *
 * <p>{@link Mode#REPLAY}가 <b>기본값</b>이다. 무료 티어는 하루 100요청이라
 * 실수로 실 호출이 나가는 쪽이 훨씬 비싼 사고다. 실 호출은 명시적으로
 * {@code gak.api-football.mode=real} 을 켠 사람만 하게 한다.
 *
 * @param baseUrl         API 루트(기본 v3.football.api-sports.io)
 * @param key             x-apisports-key 헤더 값. REAL 모드에서만 필요
 * @param mode            REAL(실 호출) / REPLAY(저장된 응답 재생)
 * @param connectTimeout  커넥션 타임아웃
 * @param readTimeout     응답 타임아웃
 * @param replayLocations REPLAY 모드에서 응답 파일을 찾을 위치(앞에서부터 시도).
 *                        {@code classpath:} / {@code file:} 접두어 모두 가능
 * @param captureDir      REAL 모드에서 원본 응답을 떨어뜨릴 디렉터리(비어 있으면 저장 안 함).
 *                        추가 요청을 쓰지 않고 replay 파일을 모으는 통로다
 */
@ConfigurationProperties(prefix = "gak.api-football")
public record ApiFootballProperties(
		String baseUrl,
		String key,
		Mode mode,
		Duration connectTimeout,
		Duration readTimeout,
		List<String> replayLocations,
		String captureDir
) {

	public enum Mode {
		/** 실제 API 호출. 요청 예산을 소모한다. */
		REAL,
		/** 저장된 응답 파일 재생. 요청 예산을 소모하지 않는다. */
		REPLAY
	}

	public ApiFootballProperties {
		baseUrl = orDefault(baseUrl, "https://v3.football.api-sports.io");
		mode = mode == null ? Mode.REPLAY : mode;
		connectTimeout = connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;
		readTimeout = readTimeout == null ? Duration.ofSeconds(20) : readTimeout;
		if (replayLocations == null || replayLocations.isEmpty()) {
			// bootRun(작업 디렉터리 backend/)과 테스트(test classpath) 양쪽에서 같은 파일을 본다.
			replayLocations = List.of(
					"classpath:apifootball/",
					"file:src/test/resources/apifootball/",
					"file:backend/src/test/resources/apifootball/");
		}
		replayLocations = List.copyOf(replayLocations);
	}

	public boolean isReplay() {
		return mode == Mode.REPLAY;
	}

	private static String orDefault(String value, String fallback) {
		return (value == null || value.isBlank()) ? fallback : value;
	}
}
