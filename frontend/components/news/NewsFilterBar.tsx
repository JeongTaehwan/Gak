"use client";

import { cn } from "@usetaehwan/ui";
import type { NewsCategory } from "@/lib/api/types";
import { NEWS_CATEGORIES } from "@/lib/news/attach";

/**
 * 갈래 필터 칩.
 *
 * ## 개수를 함께 보여 준다
 * 칩만 있으면 눌렀을 때 비는 게 필터 탓인지 데이터 탓인지 알 수 없다. 개수를 미리
 * 적어 두면 0인 칩은 애초에 안 누른다.
 *
 * ## 태그 없는 소식은 어떤 칩에도 안 걸린다
 * 태거가 아직 못 붙인 것을 "기타"에 밀어 넣지 않는다 — 모델이 기타라고 **판단한** 것과
 * 아직 **안 본** 것은 다른 상태다. 필터를 끄면 전부 보인다.
 */
export function NewsFilterBar({
  counts,
  active,
  onChange,
  total,
  untagged,
}: {
  counts: Partial<Record<NewsCategory, number>>;
  active: NewsCategory | null;
  onChange: (next: NewsCategory | null) => void;
  total: number;
  untagged: number;
}) {
  return (
    <div className="flex flex-wrap items-center gap-1.5">
      <Chip active={active === null} onClick={() => onChange(null)}>
        전체 {total}
      </Chip>

      {NEWS_CATEGORIES.map(({ key, label }) => {
        const count = counts[key] ?? 0;
        return (
          <Chip
            key={key}
            active={active === key}
            disabled={count === 0}
            onClick={() => onChange(active === key ? null : key)}
          >
            {label} {count}
          </Chip>
        );
      })}

      {untagged > 0 && (
        <span className="ml-1 text-[10px] font-bold text-text-low">
          갈래 미부여 {untagged}건
        </span>
      )}
    </div>
  );
}

function Chip({
  active,
  disabled,
  onClick,
  children,
}: {
  active: boolean;
  disabled?: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      aria-pressed={active}
      className={cn(
        "rounded-badge border px-2 py-1 text-[11px] font-extrabold tracking-wide transition-colors",
        // 소식 층은 volt 를 쓰지 않는다 — volt 는 우리가 낸 값의 색이다.
        // 선택 상태도 무채색 대비로만 표현한다.
        active
          ? "border-line-strong bg-card-hi text-text-hi"
          : "border-line text-text-low hover:border-line-strong hover:text-text-mid",
        disabled && "cursor-not-allowed opacity-35 hover:border-line hover:text-text-low",
      )}
    >
      {children}
    </button>
  );
}
