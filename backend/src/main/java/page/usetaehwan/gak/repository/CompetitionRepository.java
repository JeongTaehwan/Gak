package page.usetaehwan.gak.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import page.usetaehwan.gak.domain.Competition;

public interface CompetitionRepository extends JpaRepository<Competition, Long> {

	/** 앱에 노출할 대회만. */
	List<Competition> findByDisplayedTrue();
}
