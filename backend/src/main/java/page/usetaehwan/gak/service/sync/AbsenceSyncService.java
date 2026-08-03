package page.usetaehwan.gak.service.sync;

import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import page.usetaehwan.gak.external.apifootball.ApiFootballClient;
import page.usetaehwan.gak.repository.TeamRepository;

/**
 * 결장 기록 동기화 유스케이스 — 외부 호출과 DB 반영을 잇는다.
 *
 * <p>여기엔 트랜잭션이 없다. HTTP를 기다리는 동안 트랜잭션을 열어 두면 커넥션 하나가
 * 응답 시간만큼 잠기고, 타임아웃이 길수록 커넥션 풀이 먼저 마른다. DB 반영은
 * {@link AbsenceUpsertService}가 자기 트랜잭션 안에서 한다.
 *
 * <h2>왜 스케줄러가 없나</h2>
 * <p>경기 동기화는 대회 단위(20개)라 주기를 돌릴 수 있지만, 결장은 지금 <b>팀 단위</b>
 * 호출만 실제 응답으로 검증돼 있다. 팀마다 1요청이면 하루 100요청으로는 감당이 안 된다.
 * API에 리그 단위 파라미터가 있으니 그쪽이 답일 텐데, 실 호출로 확인하기 전에 스케줄에
 * 넣으면 예산을 태우면서 되는지도 모르는 상태가 된다. 그래서 지금은 <b>수동 실행만</b>
 * 열어 두고, 범위 결정은 개막 캡처 때 한다(CLAUDE.md 상단 체크리스트).
 */
@Service
public class AbsenceSyncService {

	private final ApiFootballClient client;
	private final AbsenceUpsertService upsertService;
	private final TeamRepository teamRepository;

	public AbsenceSyncService(ApiFootballClient client,
	                          AbsenceUpsertService upsertService,
	                          TeamRepository teamRepository) {
		this.client = client;
		this.upsertService = upsertService;
		this.teamRepository = teamRepository;
	}

	/**
	 * @param received       API가 준 줄 수
	 * @param applied        반영한 결장 수(신규 + 갱신)
	 * @param newPlayers     새로 만든 선수 수
	 * @param unknownFixture 우리 DB에 없는 경기라 버린 수
	 * @param requestCount   소모한 요청 수(REPLAY면 0)
	 */
	public record AbsenceSyncResult(int received, int applied, int newPlayers,
	                                int unknownFixture, int requestCount) {
	}

	/**
	 * 한 팀의 한 시즌 결장 기록을 동기화한다.
	 *
	 * @throws NoSuchElementException 팀이 없을 때
	 */
	public AbsenceSyncResult sync(Long teamId, int season) {
		if (!teamRepository.existsById(teamId)) {
			throw new NoSuchElementException("팀을 찾을 수 없습니다. teamId=" + teamId);
		}
		ApiFootballClient.InjuriesFetch fetch = client.fetchInjuries(teamId, season);
		return upsertService.apply(teamId, season, fetch.items(), fetch.requestCount());
	}
}
