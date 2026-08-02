package page.usetaehwan.gak.dto.analysis;

/**
 * 계산하지 <b>못한</b> 지표와 그 이유.
 *
 * <p>없는 값을 조용히 0이나 "-"로 채우면, 화면과 (다음 단계의) AI 진단은 그걸 "이동거리가
 * 0km였다", "상대가 약했다"는 <b>사실</b>로 읽는다. 모르는 것은 모른다고 말해야 그 위에
 * 잘못된 결론이 쌓이지 않는다. 그래서 null과 함께 "왜 null인지"를 같이 내려보낸다.
 *
 * @param metric 지표 이름(예: {@code "opponentStrength"})
 * @param reason 사람이 읽을 이유(한국어)
 */
public record Omission(String metric, String reason) {

	public static Omission of(String metric, String reason) {
		return new Omission(metric, reason);
	}
}
