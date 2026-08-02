package page.usetaehwan.gak.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

/**
 * "이건 확실히 새 행이다"를 아는 상황에서 INSERT만 시키는 통로.
 *
 * <p>외부 id를 PK로 그대로 쓰는 설계의 대가가 여기서 드러난다. {@code JpaRepository.save()}는
 * "id가 null이면 신규"로 판단하는데, 우리 엔티티는 <b>신규여도 id가 이미 채워져 있다</b>.
 * 그래서 save()는 전부 merge로 흘러가고, merge는 "혹시 있나" 확인하려 매번 SELECT를 한 방씩
 * 더 날린다. 시즌 첫 동기화에서 경기 380건이면 그 SELECT만 380번이다.
 *
 * <p>동기화는 이미 대상 id를 한 번에 벌크 조회해서 있는 것/없는 것을 갈라 놓은 상태다.
 * 없다고 판명난 엔티티는 확인 없이 바로 persist 하면 된다.
 */
@Repository
public class NewEntityPersister {

	@PersistenceContext
	private EntityManager entityManager;

	/** 호출자가 "DB에 없음"을 이미 확인한 엔티티만 넘길 것. */
	public <T> T persistNew(T entity) {
		entityManager.persist(entity);
		return entity;
	}
}
