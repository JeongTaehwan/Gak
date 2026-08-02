package page.usetaehwan.gak.external.apifootball;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import page.usetaehwan.gak.external.apifootball.dto.ApiFootballEnvelope;

/**
 * 응답 본문(JSON 문자열) → 봉투 객체. 실 호출과 재생이 <b>같은 경로로</b> 파싱하도록
 * 여기 한 곳에 모았다. 재생에서만 통과하고 실 호출에서 깨지는 일이 없어야 한다.
 *
 * <p>여기서 반드시 하는 일: <b>errors 필드 확인</b>. 이 API는 플랜 제한을 HTTP 200 +
 * {@code {"errors":{"plan":"..."}}} 로 알려 주기 때문에, 상태 코드만 보면 빈 배열을
 * 정상 응답으로 오해하고 DB를 "경기 0건"으로 덮어쓸 수 있다.
 */
@Component
public class ApiFootballResponseParser {

	private static final Logger log = LoggerFactory.getLogger(ApiFootballResponseParser.class);

	private final ObjectMapper objectMapper;

	public ApiFootballResponseParser(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	/**
	 * @param body        응답 본문
	 * @param itemType    response 배열 원소 타입
	 * @param context     실패 메시지에 붙일 설명(예 "fixtures league=39 season=2024")
	 * @param consumed    이 응답을 받느라 소모한 요청 수(예외에 실어 이력에 남긴다)
	 * @throws ApiFootballException 파싱 실패 또는 errors 필드가 채워져 있을 때
	 */
	public <T> ApiFootballEnvelope<T> parse(String body, Class<T> itemType, String context, int consumed) {
		if (body == null || body.isBlank()) {
			throw new ApiFootballException("API-Football 응답이 비어 있습니다 (" + context + ")", consumed);
		}

		ApiFootballEnvelope<T> envelope;
		try {
			JavaType type = objectMapper.getTypeFactory()
					.constructParametricType(ApiFootballEnvelope.class, itemType);
			envelope = objectMapper.readValue(body, type);
		} catch (Exception e) {
			throw new ApiFootballException(
					"API-Football 응답 파싱 실패 (" + context + "): " + e.getMessage(), consumed, e);
		}

		String error = envelope.errorMessage();
		if (error != null) {
			// HTTP 200이어도 여기서 끊는다. 빈 response를 정상으로 취급하면 DB가 조용히 비워진다.
			throw new ApiFootballException(
					"API-Football이 오류를 반환했습니다 (" + context + "): " + error, consumed);
		}

		if (envelope.hasMorePages()) {
			// fixtures 는 시즌 전체를 1페이지로 주는 게 정상이다. 그렇지 않다면 알아야 한다.
			log.warn("API-Football 응답이 여러 페이지입니다 ({}) — {}/{}페이지만 반영됩니다.",
					context, envelope.paging().current(), envelope.paging().total());
		}
		return envelope;
	}
}
