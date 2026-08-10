// requirements.md 5장 — 팀 목록 로딩 상태
import { LogoLockup } from "@/components/brand/Logo";

/**
 * 첫 응답을 기다리는 동안.
 *
 * <h2>왜 빈 화면이 아니라 이 한 줄인가</h2>
 * 팀 목록을 아직 못 받은 상태와 **선택 가능 팀이 0개인 상태**는 화면상 똑같이 비어 있다.
 * 요청 중이라는 말을 하지 않으면 사용자는 후자로 읽고, "이 시즌엔 고를 팀이 없구나"라는
 * 틀린 사실을 갖게 된다.
 *
 * <p>⚠️ 최종 표현은 `[미정]`이다(open-questions IN-OQ-07). 지금은 "요청 중"이라는 사실만
 * 정직하게 적는 최소 형태로 둔다 — 스켈레톤으로 목록 모양을 미리 그리면 개수까지
 * 짐작하게 만든다.
 */
export default function Loading() {
  return (
    <main className="flex h-dvh w-full items-center justify-center bg-canvas px-6">
      <div className="flex flex-col items-center gap-4">
        <LogoLockup size={24} />
        <p className="text-sm font-bold text-text-mid">
          팀 목록과 시즌을 불러오는 중입니다…
        </p>
      </div>
    </main>
  );
}
