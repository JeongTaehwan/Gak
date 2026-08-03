package page.usetaehwan.gak.service.news;

import java.util.List;
import java.util.Map;
import page.usetaehwan.gak.domain.NewsCategory;
import page.usetaehwan.gak.domain.NewsItem;

/**
 * 갈래 태거 — 게이트를 통과한 소식에 {@link NewsCategory} 하나를 붙인다.
 *
 * <h2>이 인터페이스의 모양이 곧 안전 장치다</h2>
 * <p>돌려주는 값이 {@code Map<Long, NewsCategory>} 다. 문자열이 아니라 <b>닫힌 열거형</b>이라,
 * 구현체가 무엇이든(모델이든 규칙이든) 뱉을 수 있는 값이 다섯 개로 묶인다.
 * TS로 치면 {@code => string} 이 아니라 {@code => 'TRANSFER' | 'SQUAD' | ...} 인 셈이다.
 *
 * <p>그리고 <b>입력에 없는 항목을 만들어 낼 수 없다.</b> 넘긴 id 밖의 키는 호출부가 버린다.
 * 태거는 이름을 다시 붙일 뿐 무엇이 피드에 들어올지는 결정하지 않는다 — 그건 이미
 * {@link TeamNewsMatcher} 가 끝냈다.
 *
 * @see LlmNewsCategoryTagger  실제 모델 호출
 * @see DisabledNewsCategoryTagger  키가 없거나 꺼져 있을 때
 */
public interface NewsCategoryTagger {

	/**
	 * @param items 갈래가 아직 없는 소식들
	 * @return id → 갈래. <b>일부만 담겨 있어도 정상이다</b> — 모델이 모르겠다고 한 항목,
	 *         응답에서 빠진 항목은 그냥 태그 없이 남는다. 실패를 예외로 던지지 않는다
	 */
	Map<Long, NewsCategory> tag(List<NewsItem> items);

	/** 지금 태깅을 할 수 있는 상태인가. false 면 스케줄러가 아예 부르지 않는다. */
	boolean available();
}
