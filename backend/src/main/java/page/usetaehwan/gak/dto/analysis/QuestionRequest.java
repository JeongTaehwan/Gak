// requirements.md 1장 — 질문 처리 경로는 teamId 와 season 을 반드시 함께 받는다
package page.usetaehwan.gak.dto.analysis;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 자유 질문 한 건.
 *
 * <h2>{@code season} 이 필수인 이유</h2>
 * <p>빠뜨리면 서버가 시즌을 골라야 하고, 그러면 회고 중에 던진 질문이 현재 시즌 데이터로
 * 답해진다. 타임라인은 2023-24를 그리는데 대화만 다른 해를 말하는 상태가 되고, 둘 다
 * 화면상으로는 멀쩡하다. 함께 받아야만 답하도록 닫아 둔다.
 *
 * @param season   질문 대상 시즌(API 시즌 번호). 화면이 URL에 고정해 둔 값
 * @param question 사용자가 입력한 질문 전문
 */
public record QuestionRequest(
		@NotNull(message = "질문할 시즌을 함께 보내야 합니다")
		Integer season,

		/*
		 * 상한을 두는 건 프롬프트 비용 때문만이 아니다. 수천 자짜리 입력은 질문이 아니라
		 * 다른 것(붙여넣은 기사 본문 등)이고, 그걸 받아 지표와 함께 모델에 넣으면 우리가
		 * 통제한 재료 밖의 서술이 답에 섞인다.
		 */
		@NotBlank(message = "질문이 비어 있습니다")
		@Size(max = 500, message = "질문은 500자까지 받습니다")
		String question
) {
}
