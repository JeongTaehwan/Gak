import type { Evidence, ChatHighlight } from "@/lib/chat/script";
import type { HighlightTag } from "@/lib/timeline/types";

export interface ChatMessage {
  role: "user" | "ai";
  text: string;
  evidence?: Evidence[];
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
  return (
    <div className="flex max-w-[92%] flex-col gap-2.5 self-start rounded-[10px_10px_10px_2px] border border-line-strong bg-card px-4 py-3.5">
      <div className="text-[15px] font-extrabold leading-snug text-text-hi">
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
    </div>
  );
}
