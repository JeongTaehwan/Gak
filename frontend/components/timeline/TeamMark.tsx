import { cn } from "@usetaehwan/ui";

/**
 * 로고 대신 3글자 코드 + 팀 대표색 링. (시안 2b)
 * 대표색이 없으면 code 해시 기반 팔레트 — 저장하지 않고 프론트에서 계산.
 */
function ringColor(code: string): string {
  let h = 0;
  for (let i = 0; i < code.length; i++) h = (h * 31 + code.charCodeAt(i)) >>> 0;
  return `hsl(${h % 360} 70% 55%)`;
}

export function TeamMark({
  code,
  size = 40,
  className,
}: {
  code: string;
  size?: number;
  className?: string;
}) {
  return (
    <div
      className={cn(
        "flex shrink-0 items-center justify-center rounded-full bg-line font-display font-black text-text-hi",
        className,
      )}
      style={{
        width: size,
        height: size,
        border: `2px solid ${ringColor(code)}`,
        fontSize: Math.round(size * 0.28),
      }}
    >
      {code}
    </div>
  );
}
