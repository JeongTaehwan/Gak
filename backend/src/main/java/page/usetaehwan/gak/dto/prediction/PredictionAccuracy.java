package page.usetaehwan.gak.dto.prediction;

import java.util.List;
import java.util.Map;
import page.usetaehwan.gak.domain.Pick;
import page.usetaehwan.gak.dto.analysis.SampleConfidence;

/**
 * 한 팀에 대한 예측 적중률.
 *
 * <h2>표본이 작으면 비율을 내지 않는다</h2>
 * <p>{@link FormSummary 승점률}과 같은 규칙이다. 2번 예측해서 1번 맞힌 걸 "적중률 50%"로
 * 적으면, 소수점이 주는 정밀함이 표본의 빈약함을 가린다. 적중률은 이 앱이 파는 숫자라
 * 특히 그렇다 — 3번 맞히고 "적중률 100%"라고 적는 순간 앱이 스스로를 과장하기 시작한다.
 * {@link SampleConfidence#MIN_SAMPLE_FOR_RATE}경기 미만이면 {@link #hitRate}는 null이고,
 * 대신 {@link #hits}/{@link #scored} 같은 원래의 개수는 항상 채운다.
 *
 * <h2>분모는 채점된 것만</h2>
 * <p>{@link #scored}에는 아직 결과가 안 나온 예측이 들어가지 않는다. 예측을 많이 남긴다고
 * 적중률이 흔들리면 안 되기 때문이다. 대신 {@link #pending}으로 "채점을 기다리는 게
 * 몇 건인지"를 따로 알린다 — 그게 0인데 예측이 있으면 채점이 멈춘 것이다.
 *
 * <h2>예측별 적중률</h2>
 * <p>{@link #byPick}은 "무승부를 유난히 못 맞힌다" 같은 편향을 드러낸다. 전체 적중률
 * 하나만 보면 그게 안 보인다. 여기도 표본이 작으면 비율은 null 이고 개수만 남는다.
 *
 * @param teamId     대상 팀
 * @param teamName   표기명(한글 우선)
 * @param scored     채점 완료된 예측 수 = 비율의 분모
 * @param pending    아직 채점되지 않은 예측 수(경기 전이거나 결과 대기)
 * @param hits       적중
 * @param misses     빗나감
 * @param hitRate    적중률(0.0~1.0). 표본 부족 시 null
 * @param confidence 표본 크기 등급
 * @param byPick     예측(W/D/L)별 성적
 * @param recent     최근 기록(킥오프 내림차순)
 */
public record PredictionAccuracy(
		long teamId,
		String teamName,
		int scored,
		int pending,
		int hits,
		int misses,
		Double hitRate,
		SampleConfidence confidence,
		Map<Pick, PickAccuracy> byPick,
		List<PredictionRecord> recent
) {

	/**
	 * 특정 예측값을 골랐을 때의 성적.
	 *
	 * @param predicted 그 값으로 예측한 횟수
	 * @param hits      그중 맞힌 횟수
	 * @param hitRate   적중률. 표본 부족 시 null
	 */
	public record PickAccuracy(int predicted, int hits, Double hitRate) {
	}

	/** 예측이 하나도 없을 때. "적중률 0%"와 구분된다. */
	public static PredictionAccuracy empty(long teamId, String teamName) {
		return new PredictionAccuracy(teamId, teamName, 0, 0, 0, 0,
				null, SampleConfidence.NONE, Map.of(), List.of());
	}
}
