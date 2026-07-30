package page.usetaehwan.gak.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import page.usetaehwan.gak.domain.Team;

public interface TeamRepository extends JpaRepository<Team, Long> {

	Optional<Team> findByCode(String code);
}
