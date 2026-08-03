package page.usetaehwan.gak.external.rss;

import java.time.Instant;

/**
 * 피드에서 읽은 항목 하나. <b>필드가 네 개뿐인 것이 설계다.</b>
 *
 * <p>RSS 는 {@code description}, {@code content:encoded}, {@code media:thumbnail} 등을
 * 함께 실어 보낸다. 우리는 그걸 <b>파싱 단계에서 아예 읽지 않는다</b>. 받아 두고 안 쓰는
 * 것과 애초에 안 받는 것은 다르다 — 받아 두면 언젠가 화면에 붙이고 싶어지고, 그 순간
 * 우리는 원문을 대체하기 시작한다.
 *
 * <p>썸네일도 마찬가지다. 사진은 기사와 <b>별도의 저작권</b>이 붙고 권리자가 다른 경우가
 * 흔하다. 제목과 링크만으로 충분히 쓸모 있는 화면이 나온다.
 *
 * @param title       헤드라인 원문
 * @param link        원문 URL
 * @param publishedAt 발행 시각. 못 읽으면 null(호출부가 버린다)
 */
public record RssItem(String title, String link, Instant publishedAt) {

	public boolean usable() {
		return title != null && !title.isBlank()
				&& link != null && !link.isBlank()
				&& publishedAt != null;
	}
}
