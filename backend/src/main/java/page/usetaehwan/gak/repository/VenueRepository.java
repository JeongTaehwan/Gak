package page.usetaehwan.gak.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import page.usetaehwan.gak.domain.Venue;

public interface VenueRepository extends JpaRepository<Venue, Long> {
}
