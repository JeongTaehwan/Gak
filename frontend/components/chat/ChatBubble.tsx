import type { Evidence, ChatHighlight } from "@/lib/chat/script";
import type { AnswerStatus } from "@/lib/api/types";
import type { HighlightTag } from "@/lib/timeline/types";
import { AuthorBadge } from "@/components/diagnosis/DiagnosisPanel";

export interface ChatMessage {
  role: "user" | "ai";
  text: string;
  /**
   * 이 답을 **누가 썼는가** — 답 말풍선의 배지가 이 값으로 갈린다
   * (requirements.md DG 5절, DG-OQ-07: 답 영역에도 진단 블록과 같은 배지).
   *
   * 구분 규칙:
   * - 가이드 질문의 답(`lib/chat/script.ts`, 로컬 계산) → `'rule'`
   * - 서버 답은 `status === 'ANSWERED'` 일 때만 `'ai'` — 그 외 상태의 문구는
   *   서버가 만든 결정론적 문장이지 모델이 쓴 것이 아니므로 `'rule'`
   *
   * 답이 아닌 말풍선(인사말)은 값을 두지 않고, 그때는 배지를 그리지 않는다.
   */
  authored?: "rule" | "ai";
  evidence?: Evidence[];
  /**
   * 분모 한 줄 — "23/24 시즌 · 전체 52경기 중 44경기 기준".
   *
   * 답변 본문과 **같은 말풍선 안**에 둔다. 따로 떼면 스크롤 위치에 따라 답만 읽히고,
   * 부분합이 전체처럼 읽히는 상태가 그때 생긴다.
   */
  note?: string;
  /**
   * 답을 내지 못한 이유.
   *
   * 있으면 **답이 아니라 상태**다. 문구만으로는 실패한 답변과 성공한 답변이 같은 모양이
   * 되므로, 이 값으로 톤을 갈라 사용자가 눈으로 먼저 구분하게 한다.
   */
  status?: AnswerStatus;
  /** 이 답을 더 확실히 하려면 필요하지만 우리가 갖고 있지 않은 것. */
  unknowns?: string[];
}

/** 대화 말풍선 — 사용자(볼트, 우측) / AI(카드, 좌측 + 근거). */
export function ChatBubble({
  message,
  onHighlight,
}: {
  message: ChatMessage;
  onHighlight: (tag: HighlightTag) => void;
}) {
  if (message.role === "user") {
    return (
      <div className="max-w-[80%] self-end rounded-[10px_10px_2px_10px] bg-volt px-4 py-3 text-[15px] font-extrabold text-canvas">
        {message.text}
      </div>
    );
  }

  const evidence = message.evidence ?? [];
  const unknowns = message.unknowns ?? [];
  // 답을 못 낸 말풍선은 확정 수치와 같은 무게로 보이면 안 된다.
  const unanswered = message.status != null && message.status !== "ANSWERED";

  return (
    <div
      className={
        unanswered
          ? "flex max-w-[92%] flex-col gap-2.5 self-start rounded-[10px_10px_10px_2px] border border-dashed border-line-strong bg-panel px-4 py-3.5"
          : "flex max-w-[92%] flex-col gap-2.5 self-start rounded-[10px_10px_10px_2px] border border-line-strong bg-card px-4 py-3.5"
      }
    >
      {/* 답을 누가 썼는지 — 진단 블록과 같은 배지(AuthorBadge)를 그대로 쓴다 */}
      {message.authored && (
        <div className="flex">
          <AuthorBadge authored={message.authored} />
        </div>
      )}

      <div
        className={
          unanswered
            ? "text-[14px] font-bold leading-snug text-text-mid"
            : "text-[15px] font-extrabold leading-snug text-text-hi"
        }
      >
        {message.text}
      </div>

      {evidence.length > 0 && (
        <div className="flex flex-col gap-1.5 border-t border-line pt-2.5">
          {evidence.map((ev, i) => (
            <div key={i} className="flex items-baseline gap-2">
              <span className="shrink-0 text-[11px] text-volt">▸</span>
              <span className="text-[13px] leading-normal text-text-mid">
                {ev.text}{" "}
                {ev.highlight && (
                  <button
                    type="button"
                    onClick={() =>
                      onHighlight(ev.highlight as Exclude<ChatHighlight, null>)
                    }
                    className="whitespace-nowrap font-extrabold text-volt hover:underline"
                  >
                    보기 →
                  </button>
                )}
              </span>
            </div>
          ))}
        </div>
      )}

      {/* 모르는 것을 스스로 밝히는 자리. 예외 처리가 아니라 본문이다. */}
      {unknowns.length > 0 && (
        <div className="flex flex-col gap-1 border-t border-line pt-2.5">
          <div className="text-[10px] font-extrabold tracking-wider text-text-low">
            이 답이 못 본 것
          </div>
          {unknowns.map((u, i) => (
            <div key={i} className="text-[12px] leading-normal text-text-low">
              · {u}
            </div>
          ))}
        </div>
      )}

      {message.note && (
        <div className="border-t border-line pt-2 text-[11px] font-semibold text-text-low">
          {message.note}
        </div>
      )}
    </div>
  );
}
