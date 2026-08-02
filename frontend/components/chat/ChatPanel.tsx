"use client";

import { useEffect, useRef } from "react";
import { QUESTIONS } from "@/lib/chat/script";
import type { HighlightTag } from "@/lib/timeline/types";
import { TeamMark } from "@/components/timeline/TeamMark";
import { ChatBubble, type ChatMessage } from "@/components/chat/ChatBubble";

/**
 * 좌측 대화 패널 — 가이드된 질문 버튼(하드코딩 답변) + 근거 클릭 강조.
 * 자유 입력은 "준비 중"으로 비활성.
 */
export function ChatPanel({
  messages,
  onAsk,
  onHighlight,
}: {
  messages: ChatMessage[];
  onAsk: (key: string) => void;
  onHighlight: (tag: HighlightTag) => void;
}) {
  const scrollRef = useRef<HTMLDivElement>(null);

  // 새 메시지가 붙으면 맨 아래로.
  useEffect(() => {
    const el = scrollRef.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, [messages]);

  return (
    <div className="flex w-2/5 shrink-0 flex-col border-r border-line bg-panel">
      {/* 헤더 */}
      <div className="flex items-center justify-between border-b border-line px-6 py-5">
        <div className="font-display text-[26px] font-black leading-none tracking-tight text-volt">
          각
          <span className="ml-1.5 text-[13px] font-extrabold tracking-widest text-text-hi">
            GAK
          </span>
        </div>
        <div className="flex items-center gap-2 rounded-sm border border-line-strong bg-card px-3 py-2">
          <TeamMark code="MUN" size={22} />
          <span className="text-[13px] font-bold text-text-hi">
            맨체스터 유나이티드
          </span>
          <span className="text-[10px] text-text-low">▼</span>
        </div>
      </div>

      {/* 메시지 */}
      <div
        ref={scrollRef}
        className="flex flex-1 flex-col gap-4 overflow-y-auto p-6"
      >
        {messages.map((m, i) => (
          <ChatBubble key={i} message={m} onHighlight={onHighlight} />
        ))}
      </div>

      {/* 가이드 질문 + 비활성 입력 */}
      <div className="flex flex-col gap-3 border-t border-line px-6 pb-5 pt-4">
        <div className="text-[11px] font-extrabold tracking-wider text-text-low">
          무슨 각인지 물어보기
        </div>
        <div className="grid grid-cols-2 gap-2">
          {QUESTIONS.map((q) => (
            <button
              key={q.key}
              type="button"
              onClick={() => onAsk(q.key)}
              className="rounded-card border border-line-strong bg-card px-3.5 py-3 text-left text-sm font-bold text-text-hi transition-colors hover:border-volt hover:text-volt"
            >
              {q.question}
            </button>
          ))}
        </div>
        <div className="flex items-center gap-2.5 rounded-card border border-line bg-panel px-4 py-3">
          <span className="flex-1 text-sm text-text-low">
            직접 입력은 준비 중 — 위 질문으로 시작
          </span>
          <span className="text-base text-text-low">↵</span>
        </div>
      </div>
    </div>
  );
}
