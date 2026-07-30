package page.usetaehwan.gak.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import page.usetaehwan.gak.domain.Prediction;

public interface PredictionRepository extends JpaRepository<Prediction, Long> {

	List<Prediction> findByFixtureId(Long fixtureId);

	/** 채점 완료된 예측만(적중률 집계용). */
	List<Prediction> findByIsHitNotNull();
}
