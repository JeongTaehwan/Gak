package page.usetaehwan.gak.domain;

/**
 * 동기화가 어디서 온 데이터인지. 이력에 남겨 두면 "개발 중 replay로 채운 DB"와
 * "실제 API로 채운 DB"를 나중에 구분할 수 있다.
 */
public enum SyncSource {
	/** 실제 API 호출. 하루 100요청 예산을 소모한다. */
	REAL,
	/** 저장된 응답 파일 재생. 예산을 소모하지 않는다. */
	REPLAY
}
