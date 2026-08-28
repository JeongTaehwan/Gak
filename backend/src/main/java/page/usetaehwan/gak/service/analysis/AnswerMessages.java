// requirements.md 5장 — 답변 상태별 문구
package page.usetaehwan.gak.service.analysis;

import page.usetaehwan.gak.dto.analysis.TeamAnswer.Status;

/**
 * 답을 내지 못했을 때 화면에 띄울 문구.
 *
 * <h2>⚠️ 여기 문구는 전부 잠정이다</h2>
 * <p>{@code docs/open-questions.md} IN-OQ-06(등급 LATER)이 아직 {@code [미정]}이다. 확정되면
 * <b>이 파일만</b> 고치면 되도록 한곳에 모아 뒀다 — 서비스 곳곳에 흩어 두면 같은 실패 원인에
 * 화면마다 다른 문구가 뜨는 상태가 되고, 그게 이 미정 항목이 막고 있는 바로 그 증상이다.
 *
 * <h2>왜 백엔드가 이 문장을 갖는가</h2>
 * <p>규칙 기반 <b>진단 문장</b>은 프론트에만 두기로 했다(같은 문장이 양쪽에 있으면 갈라지므로).
 * 이건 그것과 다른 종류다 — "왜 답을 못 했나"는 서버만 아는 사실이고, 이미
 * {@code AiDiagnosis.unavailable} · {@code StandingsTable.unavailable} 이 같은 방식으로
 * 사유를 한국어로 실어 보낸다. 판정을 아는 쪽이 말한다.
 */
final class AnswerMessages {

	private AnswerMessages() {
	}

	// ── 코드 게이트가 먼저 잡는 것들 (모델을 부르기 전) ──────────────────────────

	/** 그 시즌에 이 팀의 경기 데이터가 아예 없다. */
	static final String NO_SEASON_DATA =
			"이 시즌에는 이 팀의 경기 데이터가 없습니다.";

	/** 경기는 있는데 아직 하나도 치르지 않았다. "성적이 나쁘다"가 아니라 "잴 것이 없다". */
	static final String NOTHING_PLAYED =
			"이 시즌에 아직 치른 경기가 없어 판정할 수 없습니다.";

	/**
	 * 확정 경기가 표본 기준(5건) 미만 — 진단 블록의 AI 게이트와 같은 기준으로 막는다.
	 * {@code %d} 두 자리: 현재 확정 경기 수, 필요 건수.
	 */
	static final String INSUFFICIENT_SAMPLE =
			"결과가 확정된 경기가 %d건뿐이라 답하지 않습니다 (%d건 이상 필요).";

	/**
	 * 컵 경기만 있는 시즌 — 선택 기준 리그 기록이 없다.
	 *
	 * <p>2부라거나 동기화가 빠졌다고 <b>단정하지 않는다</b>. 우리가 아는 건 "우리 DB의
	 * 선택 기준 대회에 이 팀 경기가 없다"까지다.
	 */
	static final String NO_LEAGUE_RECORD =
			"이 시즌에는 선택 대상 1부 리그 기록이 없어 판정 불가입니다.";

	// ── 모델이 돌려준 상태 ────────────────────────────────────────────────────

	static final String INSUFFICIENT_DATA =
			"이 질문에 답할 근거 데이터가 없습니다.";

	static final String OUT_OF_SCOPE =
			"이 앱이 다루지 않는 내용입니다. 계산한 일정·폼·결장·이동거리로만 답합니다.";

	static final String UNINTELLIGIBLE =
			"질문의 뜻을 판별하지 못했습니다. 다시 표현해 주세요.";

	// ── 분석 자체가 실패했을 때 ────────────────────────────────────────────────

	/**
	 * 내부 사유(설정 없음·타임아웃·전송·거부·형식 오류)는 전부 이 한 문구다
	 * (DG-OQ-16 확정, 2026-08-25 오너 위임). 원인 구분은 사용자가 할 수 있는 일을
	 * 바꾸지 않는다 — 어느 쪽이든 재시도 버튼은 없고 기다리면 되는 것도 아니다.
	 * 반면 데이터 사유(표본 부족 등)는 "왜"가 사용자에게 의미가 있어 구체적으로 남긴다.
	 * 내부 세부 원인은 서비스가 로그로 남긴다.
	 */
	static final String ANALYSIS_UNAVAILABLE =
			"질문 분석에 실패했습니다. 답을 지어내는 대신 실패로 남깁니다.";

	/** 형식은 맞는데 내용이 빈 응답. 성공한 척하는 쪽이 실패보다 나쁘다. */
	static final String EMPTY_ANSWER =
			"근거 없는 답이 돌아와 사용하지 않습니다.";

	static String of(Status status) {
		return switch (status) {
			case INSUFFICIENT_DATA -> INSUFFICIENT_DATA;
			case OUT_OF_SCOPE -> OUT_OF_SCOPE;
			case UNINTELLIGIBLE -> UNINTELLIGIBLE;
			case ANALYSIS_FAILED -> ANALYSIS_UNAVAILABLE;
			case ANSWERED -> null;
		};
	}
}
