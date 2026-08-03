package page.usetaehwan.gak.dto.news;

import java.time.Instant;
import java.util.List;

/**
 * 팀 소식 응답.
 *
 * <h2>왜 배열이 아니라 봉투인가</h2>
 * <p>목록만 내려 주면 화면이 <b>빈 배열의 이유를 알 수 없다.</b> 그런데 이유가 넷이고
 * 사용자에게 할 말이 각각 다르다.
 *
 * <ol>
 *   <li>아직 아무것도 수집하지 않았다 → "곧 채워집니다"</li>
 *   <li>수집은 했는데 <b>보고 있는 시즌과 시점이 다르다</b> → 지금 상태가 이것이다.
 *       타임라인은 2023-24, 소식은 2026 이라 겹치는 구간이 없다</li>
 *   <li>보관 기간(180일)보다 오래된 구간이다 → "그때 소식은 보관하지 않습니다"</li>
 *   <li>정말로 그 기간에 소식이 없었다 → "이 기간에는 소식이 없습니다"</li>
 * </ol>
 *
 * <p>빈 화면이 버그로 보이면 안 된다. 넷을 구분하려면 <b>우리가 가진 소식의 시간 범위</b>가
 * 필요하고, 그게 {@link Coverage}다.
 *
 * @param items    소식 목록. 발행 시각 내림차순
 * @param coverage 우리가 가진 것의 범위
 */
public record TeamNewsResponse(List<NewsItemResponse> items, Coverage coverage) {

	/**
	 * @param from          우리가 가진 가장 오래된 소식의 발행 시각. 하나도 없으면 null
	 * @param to            가장 최근 소식의 발행 시각. 하나도 없으면 null
	 * @param totalItems    이 팀에 대해 저장된 전체 건수(limit 적용 전)
	 * @param retentionDays 보관 기간(일). 이보다 오래된 것은 지워졌다 — 없었던 게 아니다
	 */
	public record Coverage(Instant from, Instant to, long totalItems, long retentionDays) {

		/** 수집된 게 하나도 없는 상태인가. */
		public boolean empty() {
			return totalItems == 0;
		}
	}
}
