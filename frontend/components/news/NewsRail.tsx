import type { NewsItem } from "@/lib/api/types";
import { NewsCard } from "@/components/news/NewsCard";

/**
 * 타임라인 한 지점에 얹히는 소식 묶음.
 *
 * ## "옮긴 말"이라는 라벨
 * 경기 카드와 시각적으로 붙어 있으므로, **이게 우리 값이 아니라는 걸 글자로도** 말한다.
 * 색과 점선만으로는 "덜 중요한 우리 데이터"로 읽힐 수 있다. 이 층은 덜 중요한 게
 * 아니라 **종류가 다른** 것이다.
 *
 * ## 인과로 읽히지 않게
 * 밀집 구간 옆에 헤드라인이 놓이면 "이래서 그랬구나"로 읽기 쉽다. 그래서 라벨을
 * "이 시기에 나온 말"로 적는다 — 시점만 말하고 관계는 말하지 않는다.
 */
export function NewsRail({
  items,
  compact = false,
}: {
  items: NewsItem[];
  compact?: boolean;
}) {
  if (items.length === 0) return null;

  return (
    <div className="my-1.5 ml-7 border-l border-dashed border-line-dashed/60 pl-3">
      <div className="mb-1.5 flex items-center gap-2">
        <span className="text-[10px] font-black tracking-[0.14em] text-text-low">
          옮긴 말
        </span>
        <span className="text-[10px] font-bold text-text-low">
          {compact ? "최근" : "이 시기에 나온"} 소식 {items.length}건 · 우리가
          판단하지 않은 층
        </span>
      </div>
      <div className="flex flex-col gap-1.5">
        {items.map((item) => (
          <NewsCard key={item.link} item={item} />
        ))}
      </div>
    </div>
  );
}
