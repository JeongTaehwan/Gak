// requirements.md TL 8절 — 경기 자체의 부재는 (대회, 시즌) 단위 동기화 이력으로만 안다
package page.usetaehwan.gak.dto.analysis;

import java.time.Instant;
import page.usetaehwan.gak.domain.CompetitionType;

/**
 * 조회 시즌에 대한 한 대회의 동기화 상태. 타임라인이 "이 대회 일정은 아직 수집
 * 전입니다"를 <b>대회 단위로만</b> 말할 수 있게 하는 근거다.
 *
 * <p>백엔드는 존재하지 않는 경기를 개별적으로 알 수 없다 — 아는 것은 (대회, 시즌)별
 * 마지막 성공 동기화 시각뿐이다. 그래서 경기 단위 "수집 대기중" 마커는 계약에 없다.
 * 화면은 이력이 보장하는 수준까지만 말한다.
 *
 * @param competitionId 대회 id
 * @param name          대회 표기명(한글 우선)
 * @param shortName     좁은 자리용 짧은 표기명
 * @param type          대회 성격(LEAGUE/CUP/HYBRID)
 * @param lastSuccessAt 이 시즌을 마지막으로 성공 동기화한 시각. <b>null이면 수집 전</b>
 */
public record CompetitionSyncStatus(
		long competitionId,
		String name,
		String shortName,
		CompetitionType type,
		Instant lastSuccessAt
) {
}
