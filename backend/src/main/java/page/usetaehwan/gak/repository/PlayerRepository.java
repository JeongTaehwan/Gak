package page.usetaehwan.gak.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import page.usetaehwan.gak.domain.Player;

public interface PlayerRepository extends JpaRepository<Player, Long> {
}
