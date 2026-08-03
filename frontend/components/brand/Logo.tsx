import { cn } from "@usetaehwan/ui";

/**
 * 각(Gak) 로고 — 심볼 단독(`LogoSymbol`)과 가로형 락업(`LogoLockup`) 두 변형.
 *
 * ## 왜 워드마크를 SVG `<text>`로 만들지 않았나
 * SVG 안의 글자는 그 폰트가 **반드시 로드돼 있어야** 시안대로 그려진다. 네트워크가
 * 느리거나 CDN이 막히면 시스템 폰트로 대체되는데, SVG는 `viewBox`에 맞춰 그림을
 * 강제로 늘리므로 글자가 눌리거나 잘려 나온다 — 레이아웃이 조용히 깨지는 방식이다.
 * 그래서 여기서는 **심볼만 SVG(도형뿐)**로 그리고, "각 GAK" 글자는 평범한 HTML 텍스트로
 * 둔다. 폰트가 안 뜨면 대체 글꼴로 흐르기만 하고(모양은 조금 달라도) 배치는 멀쩡하다.
 * 폰트 스택은 `--font-display` 토큰이 잡는다: Archivo → Pretendard Variable → 시스템.
 * (Archivo에는 한글이 없어서 "각"은 원래부터 Pretendard가 그린다 — 정상 동작이다.)
 *
 * ## 왜 톤(tone)이 필요한가
 * volt(#C8FF1E)는 **다크 전용 액센트**다. 밝은 배경 위에서는 대비가 크게 모자라
 * (흰 배경 대비 1.18:1 — WCAG AA 본문 기준은 4.5:1) 글자가 뭉개진다. 그래서 밝은 배경에서는 volt를 쓰지 않고
 * 잉크(#04060A) 단색으로 바꾼다. 색을 "예쁘게" 고르는 문제가 아니라 **읽히느냐**의
 * 문제라, 기본값을 두기보다 쓰는 쪽이 배경을 밝히도록 `tone`을 명시하게 했다.
 *
 * 색값은 전부 토큰(`app/globals.css`의 `--color-*`)을 참조한다. hex를 여기 적지 않는다.
 */

/** 배경 밝기. `dark`=어두운 배경(volt) · `light`=밝은 배경(잉크 단색). */
export type LogoTone = "dark" | "light";

const GLYPH_CLASS: Record<LogoTone, string> = {
  dark: "text-volt",
  light: "text-ink",
};

const WORD_CLASS: Record<LogoTone, string> = {
  dark: "text-volt",
  light: "text-ink",
};

/** 락업의 영문 보조 텍스트("GAK")는 워드마크보다 한 단계 낮은 대비로 둔다. */
const SUB_CLASS: Record<LogoTone, string> = {
  dark: "text-text-hi",
  light: "text-ink/70",
};

/**
 * 심볼 단독 — 각도 기호(∠). 도형만으로 그려 폰트에 의존하지 않는다.
 * 색은 `currentColor`라 부모의 글자색(=톤 클래스)을 그대로 따라간다.
 */
export function LogoSymbol({
  size = 24,
  tone = "dark",
  className,
  title = "각 (Gak)",
}: {
  size?: number;
  tone?: LogoTone;
  className?: string;
  /** 스크린리더용 이름. 옆에 글자가 있는 락업에서는 빈 문자열로 두어 중복을 없앤다. */
  title?: string;
}) {
  const decorative = title === "";
  return (
    <svg
      viewBox="0 0 32 32"
      width={size}
      height={size}
      className={cn("shrink-0", GLYPH_CLASS[tone], className)}
      role={decorative ? undefined : "img"}
      aria-label={decorative ? undefined : title}
      aria-hidden={decorative || undefined}
      focusable="false"
    >
      {/* app/icon.svg 와 같은 도형이다. 한쪽만 고치지 말 것. */}
      <path
        d="M25 24.5 H7.5 L21.5 7.5"
        fill="none"
        stroke="currentColor"
        strokeWidth={4.5}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

/**
 * 가로형 락업 — 심볼 + "각" + "GAK".
 *
 * `size`는 "각"의 글자 크기(px)를 기준으로 잡고, 심볼과 영문은 거기서 비례로 따라간다.
 * 헤더마다 숫자를 따로 맞추지 않아도 균형이 유지된다.
 */
export function LogoLockup({
  size = 26,
  tone = "dark",
  className,
}: {
  size?: number;
  tone?: LogoTone;
  className?: string;
}) {
  return (
    <span
      className={cn("inline-flex items-center gap-2 leading-none", className)}
      aria-label="각 GAK"
      role="img"
    >
      <LogoSymbol size={Math.round(size * 1.08)} tone={tone} title="" />
      <span
        aria-hidden
        className={cn(
          "font-display font-black tracking-tight",
          WORD_CLASS[tone],
        )}
        style={{ fontSize: size }}
      >
        각
      </span>
      <span
        aria-hidden
        className={cn("font-extrabold tracking-widest", SUB_CLASS[tone])}
        style={{ fontSize: Math.round(size * 0.5) }}
      >
        GAK
      </span>
    </span>
  );
}
