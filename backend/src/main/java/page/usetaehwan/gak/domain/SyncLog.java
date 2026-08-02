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
import java.time.Duration;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 동기화 이력 — <b>언제, 무엇을, 몇 요청 써서</b> 동기화했는지.
 *
 * <p>단순한 감사 로그가 아니라 스케줄러의 <b>입력</b>이다. 두 가지를 여기서 읽는다.
 * <ul>
 *   <li>오늘 이미 몇 요청을 썼는가 → 남은 일일 예산 계산({@link #requestCount})</li>
 *   <li>이 대회를 마지막으로 언제 성공적으로 동기화했는가 → 다음 대상 선정</li>
 * </ul>
 * 상태를 대회 테이블 컬럼(lastSyncedAt 등)으로 들고 있지 않은 이유가 이것이다.
 * 이력이 있으면 현재 상태는 이력에서 유도되지만, 컬럼만 있으면 과거를 잃는다.
 *
 * <p>id는 외부 시스템에 대응물이 없는 <b>우리가 만든 기록</b>이라 자체 채번한다.
 * (Competition/Fixture/Team/Venue가 API id를 PK로 쓰는 것과 대비된다.)
 */
@Entity
@Table(name = "sync_log", indexes = {
		// 오늘 소모량 합산: startedAt 범위 스캔
		@Index(name = "idx_sync_log_started_at", columnList = "started_at"),
		// 대회별 마지막 성공 시각: (competition_id, status) 로 좁힌 뒤 started_at 최대값
		@Index(name = "idx_sync_log_competition", columnList = "competition_id, status, started_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SyncLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 대상 대회(API-Football league id). 연관관계 대신 id만 들고 있다 — 이력은 읽기 전용 기록이다. */
	@Column(name = "competition_id", nullable = false)
	private Long competitionId;

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

	/** 이 시도가 소모한 API 요청 수. REPLAY는 0, 실 호출은 1(실패해도 소모했다면 1). */
	@Column(nullable = false)
	private int requestCount;

	/** 반영한 경기 수(신규 + 갱신). */
	private int fixtureCount;

	/** 이 동기화로 <b>새로 만든</b> 팀/경기장 수. 기존 행 갱신은 세지 않는다. */
	private int newTeamCount;

	private int newVenueCount;

	/** 실패 사유 등. 플랜 제한 메시지가 그대로 들어오므로 길이를 넉넉히 잡는다. */
	@Column(length = 1000)
	private String message;

	private SyncLog(Long competitionId, Integer season, Instant startedAt, Instant finishedAt,
	                SyncStatus status, SyncSource source, int requestCount, int fixtureCount,
	                int newTeamCount, int newVenueCount, String message) {
		this.competitionId = competitionId;
		this.season = season;
		this.startedAt = startedAt;
		this.finishedAt = finishedAt;
		this.status = status;
		this.source = source;
		this.requestCount = requestCount;
		this.fixtureCount = fixtureCount;
		this.newTeamCount = newTeamCount;
		this.newVenueCount = newVenueCount;
		this.message = message;
	}

	public static SyncLog success(Long competitionId, Integer season, Instant startedAt,
	                              Instant finishedAt, SyncSource source, int requestCount,
	                              int fixtureCount, int newTeamCount, int newVenueCount) {
		return new SyncLog(competitionId, season, startedAt, finishedAt, SyncStatus.SUCCESS,
				source, requestCount, fixtureCount, newTeamCount, newVenueCount, null);
	}

	public static SyncLog failed(Long competitionId, Integer season, Instant startedAt,
	                             Instant finishedAt, SyncSource source, int requestCount,
	                             String message) {
		return new SyncLog(competitionId, season, startedAt, finishedAt, SyncStatus.FAILED,
				source, requestCount, 0, 0, 0, truncate(message));
	}

	/** 예산 소진 등으로 시도하지 않은 경우. 요청을 쓰지 않았다는 사실 자체가 기록 가치가 있다. */
	public static SyncLog skipped(Long competitionId, Integer season, Instant at,
	                              SyncSource source, String message) {
		return new SyncLog(competitionId, season, at, at, SyncStatus.SKIPPED,
				source, 0, 0, 0, 0, truncate(message));
	}

	/**
	 * 이력 테이블에 실제로 저장된 기록인가.
	 *
	 * <p>"재생 데이터 없음"처럼 <b>기록하지 않기로 한 결과</b>는 저장 없이 이 객체만 만들어
	 * 돌려준다(= id가 없다). 호출부가 저장 여부를 눈으로 구분할 수 있게 열어 둔다.
	 */
	public boolean isRecorded() {
		return id != null;
	}

	public Duration elapsed() {
		return (startedAt == null || finishedAt == null)
				? Duration.ZERO
				: Duration.between(startedAt, finishedAt);
	}

	private static String truncate(String message) {
		if (message == null) {
			return null;
		}
		return message.length() <= 1000 ? message : message.substring(0, 1000);
	}
}
