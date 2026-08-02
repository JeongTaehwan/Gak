package page.usetaehwan.gak.dto;

import java.time.Instant;
import page.usetaehwan.gak.domain.SyncLog;
import page.usetaehwan.gak.domain.SyncSource;
import page.usetaehwan.gak.domain.SyncStatus;

/**
 * 동기화 이력 한 건의 응답 형태. 엔티티를 그대로 노출하지 않는다.
 *
 * @param competitionId 대상 대회(API-Football league id)
 * @param season        동기화한 시즌
 * @param status        SUCCESS / FAILED / SKIPPED
 * @param source        REAL(실 호출) / REPLAY(파일 재생)
 * @param recorded      이력 테이블에 저장됐는지. 재생 파일이 없어 건너뛴 경우 false
 *                      (그 사실은 매 주기 반복되므로 쌓지 않고 로그로만 남긴다)
 * @param requestCount  이 시도가 소모한 API 요청 수(REPLAY면 0)
 * @param fixtureCount  반영한 경기 수
 * @param newTeamCount  새로 만든 팀 수
 * @param newVenueCount 새로 만든 경기장 수
 * @param message       실패 사유(성공이면 null)
 */
public record SyncLogResponse(
		Long id,
		boolean recorded,
		Long competitionId,
		Integer season,
		SyncStatus status,
		SyncSource source,
		Instant startedAt,
		Instant finishedAt,
		long elapsedMillis,
		int requestCount,
		int fixtureCount,
		int newTeamCount,
		int newVenueCount,
		String message
) {

	public static SyncLogResponse from(SyncLog log) {
		return new SyncLogResponse(
				log.getId(),
				log.isRecorded(),
				log.getCompetitionId(),
				log.getSeason(),
				log.getStatus(),
				log.getSource(),
				log.getStartedAt(),
				log.getFinishedAt(),
				log.elapsed().toMillis(),
				log.getRequestCount(),
				log.getFixtureCount(),
				log.getNewTeamCount(),
				log.getNewVenueCount(),
				log.getMessage());
	}
}
