package page.usetaehwan.gak.external.apifootball;

/**
 * API-Football 호출/재생 실패. 네트워크 오류·타임아웃·HTTP 오류뿐 아니라
 * <b>HTTP 200인데 body에 errors가 실린 경우</b>도 여기로 모은다.
 *
 * <p>동기화 서비스는 이 예외를 잡아 이력에 FAILED로 남기고 다음 대회로 넘어간다.
 * 한 대회의 실패가 그날 동기화 전체를 멈추게 두지 않는다.
 */
public class ApiFootballException extends RuntimeException {

	/** 이 실패가 요청 할당량을 실제로 소모했는가(타임아웃·오류 응답도 대개 소모된다). */
	private final int consumedRequests;

	public ApiFootballException(String message, int consumedRequests) {
		super(message);
		this.consumedRequests = consumedRequests;
	}

	public ApiFootballException(String message, int consumedRequests, Throwable cause) {
		super(message, cause);
		this.consumedRequests = consumedRequests;
	}

	public int consumedRequests() {
		return consumedRequests;
	}
}
