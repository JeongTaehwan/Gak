package page.usetaehwan.gak.controller;

import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import page.usetaehwan.gak.domain.SyncLog;
import page.usetaehwan.gak.dto.SyncLogResponse;
import page.usetaehwan.gak.dto.SyncReportResponse;
import page.usetaehwan.gak.external.apifootball.ApiFootballClient;
import page.usetaehwan.gak.repository.SyncLogRepository;
import page.usetaehwan.gak.service.sync.AbsenceSyncService;
import page.usetaehwan.gak.service.sync.FixtureSyncService;
import page.usetaehwan.gak.service.sync.RequestBudget;

/**
 * 동기화 수동 실행·확인용 개발 도구. 스케줄러가 다음 정각까지 기다리는 걸
 * 앉아서 볼 수는 없으니, 지금 당장 돌려 보는 통로를 둔다.
 *
 * <p><b>{@code local} 프로파일에서만 빈으로 올라온다.</b> 이 엔드포인트는 real 모드에서
 * 누르면 진짜로 일일 요청을 소모하므로, 배포 프로파일에는 아예 존재하지 않는 편이 안전하다.
 * (인증을 붙이는 것보다 "없는 것"이 확실하다.)
 */
@RestController
@RequestMapping("/api/admin/sync")
@Profile("local")
public class SyncAdminController {

	private final FixtureSyncService syncService;
	private final AbsenceSyncService absenceSyncService;
	private final SyncLogRepository syncLogRepository;
	private final RequestBudget requestBudget;
	private final ApiFootballClient client;

	public SyncAdminController(FixtureSyncService syncService,
	                           AbsenceSyncService absenceSyncService,
	                           SyncLogRepository syncLogRepository,
	                           RequestBudget requestBudget,
	                           ApiFootballClient client) {
		this.syncService = syncService;
		this.absenceSyncService = absenceSyncService;
		this.syncLogRepository = syncLogRepository;
		this.requestBudget = requestBudget;
		this.client = client;
	}

	/** 지금 상태 — 실 호출/재생 중 무엇인지, 오늘 요청을 얼마나 썼는지. */
	@GetMapping("/status")
	public SyncStatusResponse status() {
		return new SyncStatusResponse(
				client.source(),
				requestBudget.spentToday(),
				requestBudget.remainingToday());
	}

	public record SyncStatusResponse(
			page.usetaehwan.gak.domain.SyncSource source,
			int requestsSpentToday,
			int requestsRemainingToday) {
	}

	/** 스케줄러가 하는 일을 지금 한 번 실행 — 갱신 주기가 지난 대회들을 동기화한다. */
	@PostMapping
	public SyncReportResponse syncDue() {
		FixtureSyncService.SyncReport report = syncService.syncDueCompetitions();
		return SyncReportResponse.from(report, client.source(), requestBudget.remainingToday());
	}

	/**
	 * 대회 하나만 지금 동기화한다. 갱신 주기를 무시하므로 방금 돌린 대회도 다시 돌아간다.
	 * real 모드에서는 호출 1회당 요청 1회를 소모한다.
	 */
	@PostMapping("/{competitionId}")
	public SyncLogResponse syncOne(@PathVariable Long competitionId) {
		return SyncLogResponse.from(syncService.syncCompetition(competitionId));
	}

	/**
	 * 한 팀의 결장 기록을 동기화한다({@code /injuries}).
	 *
	 * <p>스케줄러가 없어 이 통로가 유일한 실행 수단이다 — 팀 단위 호출만 검증돼 있어
	 * 주기 실행에 넣기 전에 범위를 정해야 한다({@code AbsenceSyncService} 주석 참고).
	 * real 모드에서는 호출 1회당 요청 1회를 소모한다.
	 */
	@PostMapping("/injuries/{teamId}")
	public AbsenceSyncService.AbsenceSyncResult syncInjuries(
			@PathVariable Long teamId,
			@RequestParam int season) {
		return absenceSyncService.sync(teamId, season);
	}

	/** 최근 이력 50건 — 무엇이 언제 돌았고 왜 실패했는지. */
	@GetMapping("/logs")
	public List<SyncLogResponse> recentLogs() {
		return syncLogRepository.findTop50ByOrderByStartedAtDesc().stream()
				.map(SyncLogResponse::from)
				.toList();
	}

	/** 특정 대회의 이력만. */
	@GetMapping("/logs/{competitionId}")
	public ResponseEntity<List<SyncLogResponse>> logsFor(@PathVariable Long competitionId) {
		List<SyncLog> logs = syncLogRepository.findByCompetitionIdOrderByStartedAtDesc(competitionId);
		return ResponseEntity.ok(logs.stream().map(SyncLogResponse::from).toList());
	}
}
