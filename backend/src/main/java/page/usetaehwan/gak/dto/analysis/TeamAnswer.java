// requirements.md 1·5장 — 자유 질문은 전부 진단 경로로, 답 못 할 때는 이유를 갈라 말한다
package page.usetaehwan.gak.dto.analysis;

import java.time.Instant;
import java.util.List;

/**
 * 자유 질문 하나에 대한 답.
 *
 * <h2>못 답하는 이유를 갈라 말한다</h2>
 * <p>"답변할 수 없습니다" 한 줄은 네 가지 다른 상황을 같은 말로 덮는다. 사용자가 다음에
 * 무엇을 해야 하는지가 전부 다르다 — 데이터를 기다려야 하는지, 다른 걸 물어야 하는지,
 * 다시 눌러 보면 되는지, 말을 바꿔야 하는지. {@link Status}가 그 넷을 나눈다.
 *
 * <h2>분모는 모델이 아니라 우리가 적는다</h2>
 * <p>{@link #basis}는 응답 조립 단계에서 <b>계산된 값 그대로</b> 채운다. 모델에게
 * "분모를 밝히라"고 부탁하면 대개는 하지만 가끔 빠뜨리고, 빠뜨린 날 화면은 부분합을
 * 전체처럼 말한다. 부탁이 아니라 구조로 막는다.
 *
 * @param status        답을 냈는가, 못 냈다면 왜
 * @param statusMessage 화면에 그대로 띄울 한국어 한 줄. ⚠️ <b>잠정 문구</b>다 —
 *                      최종 문구는 open-questions.md IN-OQ-06에서 아직 {@code [미정]}이다
 * @param answer        {@link Status#ANSWERED}일 때의 답변. 아니면 null
 * @param evidence      답의 근거가 된 계산 지표. 답을 냈다면 반드시 하나 이상 있다
 * @param unknowns      이 답을 더 확실히 하려면 필요하지만 갖고 있지 않은 정보
 * @param basis         이 답이 무엇을 세고 무엇을 뺐는지
 */
public record TeamAnswer(
		Status status,
		String statusMessage,
		String answer,
		List<Evidence> evidence,
		List<String> unknowns,
		Basis basis
) {

	/**
	 * 답변 상태.
	 *
	 * <p>{@link #INSUFFICIENT_DATA}와 {@link #OUT_OF_SCOPE}를 헷갈리면 사용자가 잘못된 행동을
	 * 한다 — 전자는 "기다리거나 다른 시즌을 보라"이고 후자는 "이 앱은 그걸 모른다"이다.
	 * 전자를 후자로 말하면 될 일을 포기하게 하고, 후자를 전자로 말하면 영영 오지 않을
	 * 데이터를 기다리게 한다.
	 */
	public enum Status {
		/** 계산된 지표를 근거로 답했다. */
		ANSWERED,
		/** 질문은 이해했고 지원 범위 안이지만, <b>그 답을 낼 데이터가 없다</b>. */
		INSUFFICIENT_DATA,
		/** 질문은 이해했지만 <b>우리가 애초에 수집하지 않는</b> 영역이다(이적·전술·라커룸). */
		OUT_OF_SCOPE,
		/** 분석 도중 실패했다. 성공한 척하지 않는다. */
		ANALYSIS_FAILED,
		/** 질문의 뜻을 판별하지 못했다. 범위 밖이나 데이터 부족으로 접지 않는다. */
		UNINTELLIGIBLE
	}

	/**
	 * 근거 한 줄. 세 칸이 모두 차 있어야 근거다 — "지표 이름 없는 값"이나 "값 없는 지표"는
	 * 검증할 수 없는 문장일 뿐이다.
	 */
	public record Evidence(String metric, String value, String claim) {
	}

	/**
	 * 이 답이 선 자리.
	 *
	 * <p>{@link #analyzedFixtures}와 {@link #seasonFixtures}를 <b>따로</b> 준다. 하나로 합치면
	 * "52경기 기준"이라고 적어 놓고 실제로는 44경기를 센 상태가 만들어진다.
	 *
	 * @param season             이 답이 본 시즌. 다른 시즌 이야기는 들어 있지 않다
	 * @param calendarSeason     한 해 안에서 끝나는 시즌인가(표기용)
	 * @param analyzedFixtures   실제로 계산에 들어간 경기 수 = 분모
	 * @param seasonFixtures     그 시즌 전체 경기 수(예정·연기 포함)
	 * @param upcomingFixtures   아직 치르지 않아 계산에서 뺀 수. <b>무승부로 접지 않았다</b>
	 * @param excludedFixtures   연기·취소·중단이라 뺀 수
	 * @param from               계산에 넣은 첫 경기 킥오프. 없으면 null
	 * @param to                 계산에 넣은 마지막 경기 킥오프. 없으면 null
	 * @param leagueRecord       그 시즌에 선택 기준 리그 기록이 있었는가. false면 컵만 있는 시즌이다
	 */
	public record Basis(
			Integer season,
			boolean calendarSeason,
			int analyzedFixtures,
			int seasonFixtures,
			int upcomingFixtures,
			int excludedFixtures,
			Instant from,
			Instant to,
			boolean leagueRecord
	) {
	}

	public static TeamAnswer answered(String answer, List<Evidence> evidence,
	                                  List<String> unknowns, Basis basis) {
		return new TeamAnswer(Status.ANSWERED, null, answer,
				List.copyOf(evidence), List.copyOf(unknowns), basis);
	}

	/** 답을 내지 못했다. 근거 목록은 비운다 — 근거만 남기면 답한 것처럼 읽힌다. */
	public static TeamAnswer unanswered(Status status, String message, Basis basis) {
		return new TeamAnswer(status, message, null, List.of(), List.of(), basis);
	}
}
