package page.usetaehwan.gak.external.apifootball.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

/**
 * API-Football의 공통 응답 봉투. 모든 엔드포인트가 같은 껍데기를 쓴다.
 *
 * <p><b>errors 필드가 이 타입의 존재 이유다.</b> 이 API는 플랜 제한·잘못된 파라미터에도
 * HTTP 200을 주고, 대신 body의 {@code errors} 에 사유를 담아 보낸다.
 * <pre>
 * 성공: {"errors": [],                 "results": 380, "response": [ ... ]}
 * 실패: {"errors": {"plan": "Free plans do not have access to this season..."},
 *        "results": 0, "response": []}
 * </pre>
 * 같은 키가 <b>빈 배열</b>이 되기도 하고 <b>객체</b>가 되기도 해서 {@code Map}으로 못 받는다.
 * 그래서 {@link JsonNode}로 열어 두고 {@link #errorMessage()}에서 판정한다.
 *
 * @param <T> response 배열의 원소 타입
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ApiFootballEnvelope<T>(
		String get,
		JsonNode errors,
		Integer results,
		Paging paging,
		List<T> response
) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Paging(Integer current, Integer total) {
	}

	public List<T> responseOrEmpty() {
		return response == null ? List.of() : response;
	}

	/** 응답에 실린 오류 사유. 오류가 없으면 null. */
	public String errorMessage() {
		if (errors == null || errors.isNull() || errors.isEmpty()) {
			return null; // [] 또는 {} 또는 누락 → 정상
		}
		if (errors.isTextual()) {
			return errors.asText();
		}
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, JsonNode> entry : errors.properties()) {
			if (!sb.isEmpty()) {
				sb.append("; ");
			}
			sb.append(entry.getKey()).append(": ").append(entry.getValue().asText());
		}
		return sb.isEmpty() ? errors.toString() : sb.toString();
	}

	public boolean hasError() {
		return errorMessage() != null;
	}

	/** 결과가 여러 페이지인가. fixtures는 보통 1페이지지만 방어적으로 확인한다. */
	public boolean hasMorePages() {
		return paging != null
				&& paging.current() != null
				&& paging.total() != null
				&& paging.total() > paging.current();
	}
}
