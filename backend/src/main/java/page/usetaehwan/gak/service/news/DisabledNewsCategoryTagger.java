package page.usetaehwan.gak.service.news;

import java.util.List;
import java.util.Map;
import page.usetaehwan.gak.domain.NewsCategory;
import page.usetaehwan.gak.domain.NewsItem;

/**
 * 태깅이 꺼져 있을 때 — 아무것도 하지 않고 빈 결과를 돌려준다.
 *
 * <p>키가 없는 것, 설정으로 끈 것 모두 <b>정상 상태</b>다. 그때 소식은 배지 없이 그대로
 * 뜬다. 화면에서 필터 칩이 하나 사라질 뿐 아무것도 깨지지 않는다.
 *
 * <p>{@code DisabledAnthropicClient} 와 같은 발상이다 — "AI가 없을 때 어떻게 되는가"의
 * 판단을 서비스 로직에 if 로 흩뿌리지 않고 구현체 교체로 끝낸다.
 */
public class DisabledNewsCategoryTagger implements NewsCategoryTagger {

	@Override
	public Map<Long, NewsCategory> tag(List<NewsItem> items) {
		return Map.of();
	}

	@Override
	public boolean available() {
		return false;
	}
}
