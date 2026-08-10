// requirements.md 2~3장 — 조회 가능 시즌이 하나도 없을 때
import { LogoLockup } from "@/components/brand/Logo";

/**
 * 선택 기준 대회의 경기가 하나도 없을 때.
 *
 * <h2>"선택 가능 팀 0개"와 다른 상태다</h2>
 * 팀이 없는 게 아니라 **볼 시즌이 없다.** 둘을 같은 말로 덮으면 "이 시즌엔 1부 팀이
 * 없었다"로 읽히는데, 실제로는 아직 아무것도 받아 오지 않은 것뿐이다. 요청 실패와도
 * 다르다 — 그건 `ConnectionError` 가 따로 말한다.
 */
export function NoSeasons() {
  return (
    <main className="flex h-dvh w-full items-center justify-center bg-canvas px-6">
      <div className="flex w-full max-w-lg flex-col gap-6 rounded-panel border border-line bg-panel p-8">
        <LogoLockup size={24} />

        <div className="flex flex-col gap-2">
          <h1 className="font-display text-2xl font-black tracking-tight text-text-hi">
            조회할 수 있는 시즌이 없습니다
          </h1>
          <p className="text-sm leading-relaxed text-text-mid">
            선택 기준 대회(유럽 5대 리그 · K리그1)의 경기 데이터가 아직 하나도
            없습니다. 팀이 없는 것이 아니라 볼 시즌이 없는 상태입니다.
          </p>
        </div>

        <div className="flex flex-col gap-2 border-t border-line pt-5">
          <div className="text-[11px] font-extrabold tracking-wider text-text-low">
            해볼 것
          </div>
          <p className="text-[13px] leading-relaxed text-text-mid">
            동기화를 한 번 돌립니다 —{" "}
            <code className="text-volt">POST /api/admin/sync</code>
          </p>
        </div>
      </div>
    </main>
  );
}
