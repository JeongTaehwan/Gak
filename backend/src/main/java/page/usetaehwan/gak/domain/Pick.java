package page.usetaehwan.gak.domain;

/**
 * 예측/결과 표기. 진단 대상 팀 관점의 승·무·패다.
 * <ul>
 *   <li>{@link #W} — 승 (Win)</li>
 *   <li>{@link #D} — 무 (Draw)</li>
 *   <li>{@link #L} — 패 (Loss)</li>
 * </ul>
 * 예측({@code pick})과 실제 결과({@code resolvedResult}) 모두 이 타입을 쓴다.
 * 적중 여부는 {@code pick == resolvedResult}로 계산한다(저장하지 않는 파생값이지만,
 * 채점 시점을 못박기 위해 Prediction에는 스냅샷으로 남긴다).
 */
public enum Pick {
	W,
	D,
	L
}
