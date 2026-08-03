import { cn } from "@usetaehwan/ui";
import type { NewsItem } from "@/lib/api/types";
import { newsTimeLabel } from "@/lib/news/attach";

const CATEGORY_LABEL: Record<string, string> = {
  TRANSFER: "이적",
  SQUAD: "선수단",
  MATCH: "경기",
  CLUB: "구단",
  OTHER: "기타",
};

/**
 * 소식 한 줄.
 *
 * ## 확정 수치와 어떻게 구분되나
 *
 * 이 앱의 색은 전부 **우리가 낸 판단**에 붙어 있다 — volt(액센트), win/loss/draw(결과),
 * warn(밀집 경고), comp-*(대회). 그래서 소식은 **그 색을 하나도 쓰지 않는다.**
 * 무채색 텍스트와 점선 테두리만 쓴다. "색이 없다"가 곧 "우리 값이 아니다"라는 신호다.
 *
 * 여기에 세 가지를 더한다.
 *   · 영역 — 경기 카드보다 안쪽으로 들여쓰고 점선 레일 안에 넣는다
 *   · 라벨 — 블록 머리에 "옮긴 말"이라고 적는다(NewsRail)
 *   · 출처 — 매체명을 제목과 같은 줄 무게로 보여 준다. 감추지 않는 것이 이 층의 근거다
 *
 * ## 링크
 * 새 탭으로 열고 `rel="noopener noreferrer"`를 붙인다. `noopener`가 없으면 열린 문서가
 * `window.opener`로 우리 탭을 조작할 수 있다(탭내빙). 외부 링크에는 예외 없이 붙인다.
 */
export function NewsCard({ item }: { item: NewsItem }) {
  const category = item.category ? CATEGORY_LABEL[item.category] : null;

  return (
    <a
      href={item.link}
      target="_blank"
      rel="noopener noreferrer"
      className={cn(
        "group block rounded-card border border-dashed border-line-dashed/70",
        "px-3 py-2.5 transition-colors hover:border-line-strong hover:bg-panel/60",
      )}
    >
      <div className="flex items-baseline gap-2">
        {/* 출처 — 눈에 띄어야 한다. 우리가 쓴 문장이 아니라는 표시이자, 이 층의 법적 근거다 */}
        <span className="shrink-0 text-[11px] font-black tracking-wide text-text-mid">
          {item.sourceName}
        </span>
        {item.tier === "OFFICIAL" && (
          <span className="shrink-0 rounded-badge border border-line-strong px-1 py-px text-[9px] font-black tracking-wider text-text-mid">
            공식
          </span>
        )}
        <span className="shrink-0 text-[11px] font-bold tabular-nums text-text-low">
          {newsTimeLabel(item.publishedAt)}
        </span>
        {category && (
          <span className="ml-auto shrink-0 rounded-badge bg-panel px-1.5 py-px text-[10px] font-extrabold tracking-wider text-text-low">
            {category}
          </span>
        )}
      </div>

      {/* 제목은 원문 그대로. 번역·요약하지 않는다 */}
      <div className="mt-1 text-[13px] font-bold leading-snug text-text-mid group-hover:text-text-hi">
        {item.title}
        <span
          aria-hidden
          className="ml-1 inline-block text-[11px] text-text-low group-hover:text-text-mid"
        >
          ↗
        </span>
      </div>
    </a>
  );
}
