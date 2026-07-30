package page.usetaehwan.gak.config;

import java.time.Instant;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 공통 예외 → HTTP 응답 변환. 서비스/도메인은 순수한 예외만 던지고,
 * HTTP 상태 코드로의 번역은 여기서 한곳에 모아 한다.
 *
 * <p>특히 "킥오프 이후 예측 시도"는 {@link IllegalArgumentException}으로 올라와
 * 400으로 내려간다 — 이 앱의 핵심 규칙 위반이 클라이언트에게 명확히 전달된다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	public record ApiError(Instant timestamp, int status, String message) {
	}

	/** 잘못된 요청/규칙 위반(킥오프 이후 예측 등). */
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException e) {
		return build(HttpStatus.BAD_REQUEST, e.getMessage());
	}

	/** 대상 리소스 없음. */
	@ExceptionHandler(NoSuchElementException.class)
	public ResponseEntity<ApiError> handleNotFound(NoSuchElementException e) {
		return build(HttpStatus.NOT_FOUND, e.getMessage());
	}

	private ResponseEntity<ApiError> build(HttpStatus status, String message) {
		return ResponseEntity.status(status)
				.body(new ApiError(Instant.now(), status.value(), message));
	}
}
