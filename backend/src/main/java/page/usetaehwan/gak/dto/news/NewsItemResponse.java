package page.usetaehwan.gak.dto.news;

import java.time.Instant;
import page.usetaehwan.gak.domain.NewsCategory;
import page.usetaehwan.gak.domain.NewsItem;
import page.usetaehwan.gak.domain.SourceTier;

/**
 * 화면으로 내려가는 소식 한 건.
 *
 * <p>엔티티를 그대로 노출하지 않는다(프로젝트 원칙). 그리고 <b>내부 값을 일부러 뺀다</b> —
 * {@code dedupKey}, {@code teamId}, {@code fetchedAt} 은 우리 파이프라인의 사정이지
 * 사용자가 알 바가 아니다.
 *
 * @param title       헤드라인 원문. 번역·요약하지 않는다
 * @param link        원문 링크. 사용자는 여기서 우리 사이트를 떠난다
 * @param publishedAt 발행자가 밝힌 시각
 * @param sourceName  출처명. <b>화면에 반드시 보여야 한다</b> — 감추지 않는 것이
 *                    우리가 이 데이터를 쓸 수 있는 근거다
 * @param tier        공식/언론. 같은 문장이라도 누가 말했는지로 무게가 다르다
 * @param category    갈래. <b>null 이 정상이다</b>(태거가 안 돌았거나 못 돌았을 때).
 *                    화면은 그때 배지 없이 그린다
 */
public record NewsItemResponse(
		String title,
		String link,
		Instant publishedAt,
		String sourceName,
		SourceTier tier,
		NewsCategory category
) {

	public static NewsItemResponse from(NewsItem item) {
		return new NewsItemResponse(
				item.getTitle(),
				item.getLink(),
				item.getPublishedAt(),
				item.getSourceName(),
				item.getTier(),
				item.getCategory());
	}
}
