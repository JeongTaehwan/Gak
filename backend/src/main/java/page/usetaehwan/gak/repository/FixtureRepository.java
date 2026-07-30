package page.usetaehwan.gak.repository;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import page.usetaehwan.gak.domain.Fixture;

public interface FixtureRepository extends JpaRepository<Fixture, Long> {

	/**
	 * 한 팀이 홈/원정 어느 쪽이든 참여한 경기를 킥오프 순으로.
	 * 리그·컵·유럽대항전을 가로지른 "통합 일정"의 기초 질의다.
	 */
	List<Fixture> findByHomeTeamIdOrAwayTeamIdOrderByKickoffAsc(Long homeTeamId, Long awayTeamId);

	/** 기간 내 경기(밀집도·간격 계산의 입력). */
	List<Fixture> findByKickoffBetweenOrderByKickoffAsc(Instant from, Instant to);
}
