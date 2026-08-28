// requirements.md TL 8절 — 결장 데이터의 not-collected / quiet 는 수집 이력과 대조해 가른다
package page.usetaehwan.gak.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 결장 동기화 이력 — <b>(팀, 시즌)</b>별로 언제 받아 왔는지.
 *
 * <h2>{@link SyncLog}에 얹지 않고 따로 두는 이유</h2>
 * <ul>
 *   <li>단위가 다르다. 경기 동기화는 <b>(대회, 시즌)</b>이고 결장은 <b>(팀, 시즌)</b>이다.
 *       {@code SyncLog.competitionId}는 not null이라 담을 자리가 없고, 억지로 null을
 *       허용하면 대회별 마지막 성공 시각을 뽑는 기존 질의가 조용히 흔들린다.</li>
 *   <li>예산 집계가 달라진다. {@code RequestBudget}은 {@code SyncLog}의 요청 수를 통째로
 *       합산하므로, 결장 행을 그 테이블에 넣으면 <b>오늘 쓴 요청 수의 정의가 바뀐다</b>.
 *       이 사이클이 고치려는 것과 무관한 변경이라 건드리지 않는다.</li>
 * </ul>
 *
 * <h2>이 이력이 답하는 질문</h2>
 * <p>"결장 0명"이 <b>정말 아무도 안 빠진 것</b>인지 <b>아직 받지 않은 것</b>인지는 경기
 * 데이터만 봐서는 알 수 없다. 둘 다 행이 없기 때문이다. 이 이력이 있으면 최소한
 * "이 팀·시즌을 받아 온 적이 있는가"는 답할 수 있다.
 *
 * <p><b>다만 경기 단위까지는 답하지 못한다.</b> API는 한 시즌 요청에도 일부 경기만 준다
 * (맨유 2023 시즌 52경기 중 44경기). 그래서 성공 이력이 있어도 행이 없는 경기는 여전히
 * "모름"이다 — 0명으로 바꾸지 않는다.
 */
@Entity
@Table(name = "absence_sync_log", indexes = {
		// (팀, 시즌)의 마지막 성공 시각: 세 컬럼으로 좁힌 뒤 started_at 최대값
		@Index(name = "idx_absence_sync_team_season",
				columnList = "team_id, season, status, started_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AbsenceSyncLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 대상 팀(API-Football team id). 이력은 읽기 전용 기록이라 연관관계 대신 id만 든다. */
	@Column(name = "team_id", nullable = false)
	private Long teamId;

	@Column(nullable = false)
	private Integer season;

	@Column(name = "started_at", nullable = false)
	private Instant startedAt;

	private Instant finishedAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private SyncStatus status;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private SyncSource source;

	/** 이 시도가 소모한 API 요청 수. REPLAY는 0. */
	@Column(nullable = false)
	private int requestCount;

	/** 반영한 결장 수(신규 + 갱신). 0건 성공도 성공이다 — 그 사실이 이 이력의 요점이다. */
	private int appliedCount;

	@Column(length = 1000)
	private String message;

	private AbsenceSyncLog(Long teamId, Integer season, Instant startedAt, Instant finishedAt,
	                       SyncStatus status, SyncSource source, int requestCount,
	                       int appliedCount, String message) {
		this.teamId = teamId;
		this.season = season;
		this.startedAt = startedAt;
		this.finishedAt = finishedAt;
		this.status = status;
		this.source = source;
		this.requestCount = requestCount;
		this.appliedCount = appliedCount;
		this.message = message;
	}

	public static AbsenceSyncLog success(Long teamId, Integer season, Instant startedAt,
	                                     Instant finishedAt, SyncSource source,
	                                     int requestCount, int appliedCount) {
		return new AbsenceSyncLog(teamId, season, startedAt, finishedAt, SyncStatus.SUCCESS,
				source, requestCount, appliedCount, null);
	}

	public static AbsenceSyncLog failed(Long teamId, Integer season, Instant startedAt,
	                                    Instant finishedAt, SyncSource source,
	                                    int requestCount, String message) {
		return new AbsenceSyncLog(teamId, season, startedAt, finishedAt, SyncStatus.FAILED,
				source, requestCount, 0, truncate(message));
	}

	private static String truncate(String message) {
		if (message == null) {
			return null;
		}
		return message.length() <= 1000 ? message : message.substring(0, 1000);
	}
}
