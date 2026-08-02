package page.usetaehwan.gak.external.apifootball;

/**
 * 재생할 응답 파일이 없다 — <b>실패가 아니라 "가진 데이터가 없다"</b>는 뜻이다.
 *
 * <p>{@link ApiFootballException}과 갈라 둔 이유가 이것이다. 네트워크 오류나 API의
 * errors 응답은 "시도했는데 안 됐다"라서 이력에 남겨야 재시도·원인 추적이 된다.
 * 반면 개발용 재생 파일이 없는 건 시도조차 하지 않은 상태이고, 앞으로도 파일을
 * 채워 넣기 전까지는 계속 없을 것이므로, 매 주기마다 이력에 실패 행을 쌓아 봐야
 * 진짜 장애를 그 안에 묻어 버릴 뿐이다.
 *
 * <p>그래서 이 예외만은 {@code sync_log}에 기록하지 않고 로그로만 남긴다.
 * 이 구분은 재생 모드에서만 발생한다 — 실 호출 클라이언트는 이 예외를 던지지 않는다.
 */
public class ReplayDataMissingException extends ApiFootballException {

	public ReplayDataMissingException(String message) {
		// 재생은 할당량을 쓰지 않으므로 소모 요청 수는 항상 0.
		super(message, 0);
	}
}
