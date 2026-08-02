package page.usetaehwan.gak.dto;

import java.util.List;
import page.usetaehwan.gak.domain.SyncSource;
import page.usetaehwan.gak.service.sync.FixtureSyncService.SyncReport;

/**
 * 동기화 한 회차의 결과 요약.
 *
 * @param source             이번 회차가 실 호출이었는지 파일 재생이었는지
 * @param attempted          시도한 대회 수
 * @param succeeded          성공한 대회 수
 * @param requestsSpent      이번 회차에 소모한 API 요청 수
 * @param requestsRemaining  오늘 남은 요청 예산
 * @param logs               대회별 결과
 */
public record SyncReportResponse(
		SyncSource source,
		int attempted,
		int succeeded,
		int requestsSpent,
		int requestsRemaining,
		List<SyncLogResponse> logs
) {

	public static SyncReportResponse from(SyncReport report, SyncSource source, int remaining) {
		return new SyncReportResponse(
				source,
				report.attempted(),
				report.succeeded(),
				report.spent(),
				remaining,
				report.logs().stream().map(SyncLogResponse::from).toList());
	}
}
