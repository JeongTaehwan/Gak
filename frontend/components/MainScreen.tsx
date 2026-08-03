"use client";

import { useCallback, useMemo, useState } from "react";
import type { HighlightTag, Timeline as TimelineVM } from "@/lib/timeline/types";
import { buildChatScript } from "@/lib/chat/script";
import { ChatPanel } from "@/components/chat/ChatPanel";
import { type ChatMessage } from "@/components/chat/ChatBubble";
import { TeamHeader, type Mode } from "@/components/layout/TeamHeader";
import { Timeline } from "@/components/timeline/Timeline";
import { DiagnosisPanel } from "@/components/diagnosis/DiagnosisPanel";
import { PredictionPanel } from "@/components/prediction/PredictionPanel";
import type { DataSource } from "@/lib/api/client";
import type { PredictionAccuracy } from "@/lib/api/types";

const HIGHLIGHT_LABEL: Record<HighlightTag, string> = {
  congestion: "밀집 구간",
  form: "최근 폼",
  europe: "유럽·컵",
  travel: "원정 이동",
};

/**
 * 메인 화면 — 좌 40% 대화 / 우 60% 데이터(통합 타임라인).
 * 상태(대화 로그·강조)만 여기서 쥐고, 나머지는 프레젠테이션 컴포넌트.
 */
export function MainScreen({
  timeline,
  accuracy,
  source,
}: {
  timeline: TimelineVM;
  accuracy: PredictionAccuracy;
  source: DataSource;
}) {
  // 대화 대본은 진단 결과에서 만들어진다 — 화면과 대화가 다른 숫자를 말하지 않도록.
  const script = useMemo(() => buildChatScript(timeline), [timeline]);

  const [messages, setMessages] = useState<ChatMessage[]>([
    { role: "ai", text: script.intro },
  ]);
  const [highlight, setHighlight] = useState<HighlightTag | null>(null);
  const [mode, setMode] = useState<Mode>("timeline");

  const onAsk = useCallback(
    (key: string) => {
      const qa = script.questions.find((q) => q.key === key);
      if (!qa) return;
      setMessages((prev) => [
        ...prev,
        { role: "user", text: qa.question },
        { role: "ai", text: qa.answer, evidence: qa.evidence },
      ]);
      setHighlight(qa.highlight);
      // 근거가 경기를 가리키면 타임라인을 보여 준다 — 강조만 걸고 다른 화면에
      // 머무르면 사용자는 "보기 →"를 눌렀는데 아무 일도 안 일어난 걸로 읽는다.
      if (qa.highlight) setMode("timeline");
    },
    [script],
  );

  const onHighlight = useCallback((tag: HighlightTag) => {
    setHighlight(tag);
    setMode("timeline");
  }, []);

  return (
    <main className="flex h-dvh w-full overflow-hidden bg-canvas">
      <ChatPanel
        team={timeline.team}
        questions={script.questions}
        messages={messages}
        onAsk={onAsk}
        onHighlight={onHighlight}
      />

      <div className="flex min-w-0 flex-1 flex-col bg-canvas">
        <TeamHeader
          team={timeline.team}
          form={timeline.form}
          mode={mode}
          onModeChange={setMode}
        />

        {/* 목 데이터로 보고 있다면 화면이 먼저 말한다 — 가짜 숫자를 사실로 읽지 않도록 */}
        {source === "mock" && (
          <div className="flex items-center gap-2.5 border-b border-warn/40 bg-warn/8 px-7 py-2">
            <span className="rounded-badge bg-warn/15 px-2 py-0.5 text-[10px] font-black tracking-wider text-warn">
              MOCK
            </span>
            <span className="text-xs font-bold text-text-mid">
              개발용 스냅샷을 보고 있습니다 — 백엔드에 연결하려면{" "}
              <code className="font-display text-text-hi">GAK_DATA_SOURCE</code>{" "}
              를 지우세요
            </span>
          </div>
        )}

        {/* 강조 상태 표시 + 해제 — 강조는 타임라인에서만 의미가 있다 */}
        {highlight && mode === "timeline" && (
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
          {mode === "timeline" && (
            <Timeline timeline={timeline} activeHighlight={highlight} />
          )}
          {mode === "diagnosis" && (
            <DiagnosisPanel timeline={timeline} onInspect={onHighlight} />
          )}
          {mode === "prediction" && (
            <PredictionPanel timeline={timeline} accuracy={accuracy} />
          )}
        </div>
      </div>
    </main>
  );
}
