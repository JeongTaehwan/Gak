// requirements.md 1~5장 — 입력 화면
"use client";

import { useEffect, useRef, useState } from "react";
import type { QA } from "@/lib/chat/script";
import type { HighlightTag } from "@/lib/timeline/types";
import type { TeamSelection } from "@/lib/api/types";
import { LogoLockup } from "@/components/brand/Logo";
import { ChatBubble, type ChatMessage } from "@/components/chat/ChatBubble";
import { TeamPicker } from "@/components/input/TeamPicker";
import { SeasonNav } from "@/components/input/SeasonNav";

/** 자유 입력 상한 — 서버의 `QuestionRequest` 와 같은 값이어야 한다. */
const MAX_QUESTION_LENGTH = 500;

/**
 * 입력 화면 — 팀·시즌을 정하고, 묻는다.
 *
 * <h2>여기서 정한 `teamId + season` 이 다른 네 화면의 입력이다</h2>
 * 팀 선택과 시즌 이동이 이 패널에 함께 있는 이유는 둘이 한 쌍이기 때문이다. 시즌은
 * 조회 필터가 아니라 **팀 목록을 만드는 선행 입력**이고(승격·강등), 정해진 뒤에는
 * 통합 타임라인·진단·예측·순위표가 전부 같은 값을 받는다.
 *
 * <h2>자유 질문은 분기하지 않는다</h2>
 * 가이드 질문은 이미 계산된 진단 결과를 그 자리에서 읽어 답하고(왕복 없음), 자유 입력은
 * 서버의 진단 경로로 간다. 어느 쪽도 예측이나 순위표로 사용자를 보내지 않는다 —
 * 답할 수 없으면 다른 데로 보내는 대신 답할 수 없다고 말한다.
 */
export function ChatPanel({
  selection,
  questions,
  messages,
  asking,
  onAsk,
  onSubmit,
  onHighlight,
  onTeamChange,
  onSeasonChange,
}: {
  selection: TeamSelection;
  questions: QA[];
  messages: ChatMessage[];
  /** 질문 처리 중인가. ⚠️ 이 상태의 최종 화면 표현은 `[미정]`(IN-OQ-07)이다. */
  asking: boolean;
  onAsk: (key: string) => void;
  onSubmit: (question: string) => void;
  onHighlight: (tag: HighlightTag) => void;
  onTeamChange: (teamId: number) => void;
  onSeasonChange: (season: number) => void;
}) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const [draft, setDraft] = useState("");

  // 새 메시지가 붙으면 맨 아래로.
  useEffect(() => {
    const el = scrollRef.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, [messages, asking]);

  const trimmed = draft.trim();
  const canSubmit = trimmed.length > 0 && !asking;

  function submit() {
    if (!canSubmit) return;
    onSubmit(trimmed);
    setDraft("");
  }

  return (
    <div className="flex w-2/5 shrink-0 flex-col border-r border-line bg-panel">
      {/* 헤더 = 입력부. 팀과 시즌이 여기서 정해진다 */}
      <div className="flex flex-col gap-3 border-b border-line px-6 py-5">
        <div className="flex items-center justify-between gap-4">
          <LogoLockup size={26} />
          <div className="min-w-0 flex-1">
            <TeamPicker selection={selection} onChange={onTeamChange} />
          </div>
        </div>
        <SeasonNav selection={selection} onChange={onSeasonChange} />
      </div>

      {/* 메시지 */}
      <div
        ref={scrollRef}
        className="flex flex-1 flex-col gap-4 overflow-y-auto p-6"
      >
        {messages.map((m, i) => (
          <ChatBubble key={i} message={m} onHighlight={onHighlight} />
        ))}
        {asking && (
          <div className="self-start rounded-[10px_10px_10px_2px] border border-dashed border-line-strong bg-panel px-4 py-3 text-[13px] font-bold text-text-low">
            분석 중…
          </div>
        )}
      </div>

      {/* 가이드 질문 + 자유 입력 */}
      <div className="flex flex-col gap-3 border-t border-line px-6 pb-5 pt-4">
        <div className="text-[11px] font-extrabold tracking-wider text-text-low">
          무슨 각인지 물어보기
        </div>
        <div className="grid grid-cols-2 gap-2">
          {questions.map((q) => (
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

        <form
          onSubmit={(e) => {
            e.preventDefault();
            submit();
          }}
          className="flex items-center gap-2.5 rounded-card border border-line bg-panel px-4 py-2.5 focus-within:border-line-strong"
        >
          <input
            type="text"
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            maxLength={MAX_QUESTION_LENGTH}
            disabled={asking}
            placeholder="직접 물어보기 — 예: 왜 부진한가요?"
            aria-label="자유 질문"
            className="min-w-0 flex-1 bg-transparent text-sm text-text-hi placeholder:text-text-low focus:outline-none disabled:cursor-default"
          />
          <button
            type="submit"
            disabled={!canSubmit}
            aria-label="질문 보내기"
            className="text-base font-extrabold text-volt disabled:text-text-low"
          >
            ↵
          </button>
        </form>
      </div>
    </div>
  );
}
