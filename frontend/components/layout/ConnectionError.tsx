import { LogoLockup } from "@/components/brand/Logo";

/**
 * 백엔드에 닿지 못했을 때의 화면.
 *
 * 여기서 목 데이터로 슬쩍 넘어가지 않는 게 핵심이다 — 화면이 정상으로 보이는데 숫자가
 * 가짜인 상태는 사용자도 개발자도 알아채지 못한다. 대신 "무엇이 안 됐는지"와
 * "뭘 하면 되는지"를 같이 적는다. 목 전환도 사람이 명시적으로 하는 선택지로 보여 준다.
 */
export function ConnectionError({
  kind,
  detail,
}: {
  /** unreachable = 서버가 응답 자체를 안 함 · rejected = 응답은 왔는데 오류였다 */
  kind: "unreachable" | "rejected";
  detail: string;
}) {
  const headline =
    kind === "unreachable"
      ? "백엔드에 연결하지 못했습니다"
      : "백엔드가 요청을 거절했습니다";

  return (
    <main className="flex h-dvh w-full items-center justify-center bg-canvas px-6">
      <div className="flex w-full max-w-lg flex-col gap-6 rounded-panel border border-line bg-panel p-8">
        <LogoLockup size={24} />

        <div className="flex flex-col gap-2">
          <h1 className="font-display text-2xl font-black tracking-tight text-text-hi">
            {headline}
          </h1>
          <p className="text-sm leading-relaxed text-text-mid">
            데이터를 지어내지 않기 위해 화면을 그리지 않았습니다. 숫자가 보이는데
            그게 가짜인 상태가 아무것도 안 보이는 것보다 나쁩니다.
          </p>
          <code className="mt-1 break-all rounded-card border border-line-strong bg-card px-3 py-2 font-display text-[12px] text-text-low">
            {detail}
          </code>
        </div>

        <div className="flex flex-col gap-3 border-t border-line pt-5">
          <div className="text-[11px] font-extrabold tracking-wider text-text-low">
            해볼 것
          </div>
          <Step n={1} label="백엔드를 띄운다">
            <code className="text-volt">./scripts/dev.sh</code> 로 백엔드(:8080)와
            프론트(:3000)를 함께 실행합니다.
          </Step>
          {kind === "rejected" ? (
            <Step n={2} label="동기화를 한 번 돌린다">
              경기 데이터가 아직 없을 수 있습니다 —{" "}
              <code className="text-volt">POST /api/admin/sync</code>
            </Step>
          ) : (
            <Step n={2} label="백엔드 없이 화면만 본다">
              <code className="text-volt">frontend/.env.local</code> 에{" "}
              <code className="text-volt">GAK_DATA_SOURCE=mock</code> 한 줄을 넣고
              다시 실행하면 개발용 스냅샷으로 그립니다.
            </Step>
          )}
        </div>
      </div>
    </main>
  );
}

function Step({
  n,
  label,
  children,
}: {
  n: number;
  label: string;
  children: React.ReactNode;
}) {
  return (
    <div className="flex gap-3">
      <span className="mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-badge bg-line font-display text-[11px] font-black text-text-hi">
        {n}
      </span>
      <div className="flex min-w-0 flex-col gap-0.5">
        <span className="text-sm font-bold text-text-hi">{label}</span>
        <span className="text-[13px] leading-relaxed text-text-mid">
          {children}
        </span>
      </div>
    </div>
  );
}
