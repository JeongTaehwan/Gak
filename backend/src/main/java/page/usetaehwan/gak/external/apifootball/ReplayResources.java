package page.usetaehwan.gak.external.apifootball;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import page.usetaehwan.gak.config.ApiFootballProperties;

/**
 * 저장해 둔 응답 파일을 찾아 읽는다. 설정된 위치를 <b>앞에서부터</b> 시도하므로
 * 테스트(test classpath)와 {@code bootRun}(작업 디렉터리 상대 경로)이 같은 파일을 본다.
 *
 * <p>파일명 규약: {@code fixtures-league{리그id}-season{시즌}.json}.
 * 실제 응답을 그대로 저장한 것이라, 재생 경로가 실 호출과 같은 파서를 타면
 * "개발 중엔 되는데 실 호출에서 깨지는" 상황이 생기지 않는다.
 */
@Component
public class ReplayResources {

	private final ResourceLoader resourceLoader;
	private final List<String> locations;

	public ReplayResources(ResourceLoader resourceLoader, ApiFootballProperties properties) {
		this.resourceLoader = resourceLoader;
		this.locations = properties.replayLocations();
	}

	/** 대회·시즌에 대응하는 응답 파일명. 저장(capture)과 재생이 이 규약을 공유한다. */
	public static String fixturesFileName(long leagueId, int season) {
		return "fixtures-league" + leagueId + "-season" + season + ".json";
	}

	public Optional<Resource> find(String fileName) {
		for (String location : locations) {
			Resource resource = resourceLoader.getResource(location + fileName);
			if (resource.exists() && resource.isReadable()) {
				return Optional.of(resource);
			}
		}
		return Optional.empty();
	}

	/**
	 * 파일 내용.
	 *
	 * <p>파일이 <b>없는</b> 경우와 <b>있는데 못 읽는</b> 경우를 다른 예외로 구분한다.
	 * 전자는 "아직 안 받아 둔 대회"라 이력에 남기지 않고, 후자는 권한·손상 등 진짜 문제라
	 * 실패로 기록한다.
	 */
	public String read(String fileName) {
		Resource resource = find(fileName).orElseThrow(() -> new ReplayDataMissingException(
				"재생할 응답 파일이 없습니다: " + fileName
						+ " (찾아본 위치: " + String.join(", ", locations) + "). "
						+ "gak.api-football.mode=real 로 한 번 호출하면서 capture-dir 을 지정하면 이 파일이 생성됩니다."));
		try {
			return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			// 파일은 있는데 읽히지 않는다 — 이건 진짜 오류다.
			throw new ApiFootballException("응답 파일을 읽지 못했습니다: " + fileName, 0, e);
		}
	}

	public List<String> locations() {
		return locations;
	}
}
