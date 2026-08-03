package page.usetaehwan.gak.service.news;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import page.usetaehwan.gak.domain.NewsItem;
import page.usetaehwan.gak.repository.NewsItemRepository;

/**
 * 쓰기만 하는 빈.
 *
 * <p><b>{@link NewsIngestService} 안의 메서드가 아니라 별도 빈인 이유</b>는 Spring 의
 * 트랜잭션이 프록시로 걸리기 때문이다. 같은 클래스 안에서 자기 메서드를 부르면
 * ({@code this.store(...)}) 프록시를 거치지 않아 {@code @Transactional} 이 <b>조용히
 * 아무 일도 하지 않는다</b>. 컴파일도 되고 테스트도 통과하는데 트랜잭션만 없는,
 * 눈치채기 어려운 종류의 버그다.
 *
 * <p>TS 로 치면 데코레이터가 인스턴스 메서드 호출에는 안 붙는 것과 비슷하다 —
 * 밖에서 부를 때만 걸린다.
 */
@Service
public class NewsItemWriter {

	private final NewsItemRepository repository;

	public NewsItemWriter(NewsItemRepository repository) {
		this.repository = repository;
	}

	/**
	 * 아직 없는 것만 저장한다.
	 *
	 * <p>이미 있는 키를 <b>한 번에</b> 조회해 거른다. 건마다 물으면 100건에 100쿼리다
	 * ({@code AbsenceRepository} 와 같은 판단).
	 *
	 * <p>같은 회차 안에서도 키가 겹칠 수 있다 — <b>소스가 달라도 URL 이 같으면 같은
	 * 기사다.</b> 그래서 DB 조회 전에 한 번 접는다.
	 *
	 * <p>unique 제약이 최후의 방어선으로 남는다. 조회와 저장 사이에 다른 실행이 끼어들어도
	 * 같은 기사가 두 줄이 되지 않는다 — 이 메서드의 트랜잭션이 그걸 막아 주지는 못한다
	 * (트랜잭션은 격리이지 잠금이 아니다). 제약이 실제 보증이고 여기 조회는 최적화다.
	 */
	@Transactional
	public int storeNew(List<NewsIngestService.Candidate> candidates) {
		if (candidates == null || candidates.isEmpty()) {
			return 0;
		}
		Map<String, NewsIngestService.Candidate> byKey = new LinkedHashMap<>();
		candidates.forEach(c -> byKey.putIfAbsent(c.dedupKey(), c));

		Set<String> existing = new LinkedHashSet<>(repository.findExistingKeys(byKey.keySet()));

		List<NewsItem> fresh = byKey.values().stream()
				.filter(c -> !existing.contains(c.dedupKey()))
				.map(NewsIngestService.Candidate::toEntity)
				.toList();

		if (fresh.isEmpty()) {
			return 0;
		}
		repository.saveAll(fresh);
		return fresh.size();
	}

	/** 태거가 매긴 갈래를 반영한다. 이미 갈래가 있는 항목은 건드리지 않는다. */
	@Transactional
	public int applyCategories(Map<Long, page.usetaehwan.gak.domain.NewsCategory> categories) {
		if (categories == null || categories.isEmpty()) {
			return 0;
		}
		List<NewsItem> items = repository.findAllById(categories.keySet());
		int applied = 0;
		for (NewsItem item : items) {
			if (item.needsCategory()) {
				item.applyCategory(categories.get(item.getId()));
				applied++;
			}
		}
		return applied;
	}

	@Transactional
	public int deletePublishedBefore(Instant cutoff) {
		return repository.deletePublishedBefore(cutoff);
	}

	/** 매체가 내려 달라고 했을 때 — 그 소스의 저장분을 전부 지운다. */
	@Transactional
	public int deleteSource(String sourceKey) {
		return repository.deleteBySourceKey(sourceKey);
	}
}
