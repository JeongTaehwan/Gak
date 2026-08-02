"use client";

import { useCallback, useState } from "react";
import type { HighlightTag, Timeline as TimelineVM } from "@/lib/timeline/types";
import { INTRO, QUESTIONS } from "@/lib/chat/script";
import { ChatPanel } from "@/components/chat/ChatPanel";
import { type ChatMessage } from "@/components/chat/ChatBubble";
import { TeamHeader } from "@/components/layout/TeamHeader";
import { Timeline } from "@/components/timeline/Timeline";

const HIGHLIGHT_LABEL: Record<HighlightTag, string> = {
  congestion: "밀집 구간",
  form: "최근 폼",
  europe: "유럽·컵",
};

/**
 * 메인 화면 — 좌 40% 대화 / 우 60% 데이터(통합 타임라인).
 * 상태(대화 로그·강조)만 여기서 쥐고, 나머지는 프레젠테이션 컴포넌트.
 */
export function MainScreen({ timeline }: { timeline: TimelineVM }) {
  const [messages, setMessages] = useState<ChatMessage[]>([
    { role: "ai", text: INTRO },
  ]);
  const [highlight, setHighlight] = useState<HighlightTag | null>(null);

  const onAsk = useCallback((key: string) => {
    const qa = QUESTIONS.find((q) => q.key === key);
    if (!qa) return;
    setMessages((prev) => [
      ...prev,
      { role: "user", text: qa.question },
      { role: "ai", text: qa.answer, evidence: qa.evidence },
    ]);
    setHighlight(qa.highlight);
  }, []);

  const onHighlight = useCallback((tag: HighlightTag) => setHighlight(tag), []);

  return (
    <main className="flex h-dvh w-full overflow-hidden bg-canvas">
      <ChatPanel messages={messages} onAsk={onAsk} onHighlight={onHighlight} />

      <div className="flex min-w-0 flex-1 flex-col bg-canvas">
        <TeamHeader form={timeline.form} />

        {/* 강조 상태 표시 + 해제 */}
        {highlight && (
          <div className="flex items-center gap-2.5 border-b border-line bg-panel px-7 py-2.5">
            <span className="text-xs font-bold text-text-mid">
              강조 중:{" "}
              <span className="font-extrabold text-volt">
                {HIGHLIGHT_LABEL[highlight]}
              </span>
            </span>
            <button
              type="button"
              onClick={() => setHighlight(null)}
              className="text-xs font-extrabold text-text-low hover:text-text-hi"
            >
              전체 보기 ✕
            </button>
          </div>
        )}

        <div className="flex-1 overflow-y-auto px-7 pb-10 pt-6">
          <Timeline timeline={timeline} activeHighlight={highlight} />
        </div>
      </div>
    </main>
  );
}
