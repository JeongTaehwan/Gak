// requirements.md DG 7절
package page.usetaehwan.gak.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 저장된 AI 진단 서술 한 건. 재방문 시 모델을 기다리지 않게 하고, 데이터가 변하지 않는
 * 회고 시즌에서는 이 저장분이 계속 재사용된다 (DG-OQ-11).
 *
 * <h2>무엇이 이 기록을 낡게 만드나</h2>
 * <p>진단 대상 데이터가 변하면 이 기록은 버려지고 새 호출로 교체된다. "변했다"의 판정은
 * {@link #analyzedFixtures} — 생성 시점에 계산에 들어간 경기 수(분모)다. 저장할 때의
 * 분모와 지금의 분모가 다르면 이 문장은 다른 기간을 말하고 있는 것이다.
 * 판정 기준의 확정은 미정(DG-OQ-19)이라 지금은 분모 변화만 본다 — 비교하는 곳은
 * {@code AiDiagnosisService}.
 *
 * <h2>키는 (팀, 시즌, 밀집 기준) 조합이다</h2>
 * <p>같은 팀·시즌이라도 창 폭({@code windowDays})·밀집 기준({@code minMatches})이 다르면
 * 모델이 본 지표가 다르므로 별개의 기록이다. 유니크 제약이 "조합당 한 행"을 DB 차원에서
 * 강제한다 — 동시 요청 둘이 같이 miss 를 보고 같이 insert 해도 한 행만 남는다
 * (경합 해소는 {@code AiDiagnosisArchive}).
 *
 * <p>id는 API가 주는 값이 아니라 우리가 만드는 기록이므로 자체 생성(IDENTITY)한다.
 *
 * <p>{@code Team} 연관이 아니라 {@code teamId}인 이유는 {@link NewsItem}과 같다 —
 * 이 기록은 진단 계산에 되먹임되지 않는 별도 층이고, FK로 묶으면 저장이 팀 동기화
 * 상태에 묶인다.
 */
@Entity
@Table(
		name = "ai_diagnosis_record",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_ai_diagnosis_record_scope",
				columnNames = {"team_id", "season", "window_days", "min_matches"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiDiagnosisRecord {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 진단 대상 팀(API-Football 팀 id). */
	@Column(name = "team_id", nullable = false)
	private long teamId;

	/** 진단한 시즌(API 시즌 번호). */
	@Column(nullable = false)
	private int season;

	/** 밀집 판정 창 폭(일). 기준이 다르면 다른 지표를 본 것이므로 키의 일부다. */
	@Column(name = "window_days", nullable = false)
	private int windowDays;

	/** 밀집 기준 경기 수. {@link #windowDays}와 같은 이유로 키의 일부다. */
	@Column(name = "min_matches", nullable = false)
	private int minMatches;

	/**
	 * 생성 시점에 계산에 들어간 경기 수(분모). 지금의 분모와 다르면 이 기록은 낡은 것이다.
	 * 판정 기준으로 이 값 하나만 쓰는 건 잠정 결정이다 (DG-OQ-19, 클래스 주석 참고).
	 */
	@Column(name = "analyzed_fixtures", nullable = false)
	private int analyzedFixtures;

	/** 한 줄 결론. 모델이 쓴 문장 그대로 — 저장하면서 가공하지 않는다. */
	@Column(nullable = false, length = 200)
	private String headline;

	/** 뒷받침 문장. */
	@Column(nullable = false, length = 1000)
	private String sub;

	/**
	 * 결론의 근거. <b>비어 있는 채로 저장될 수 없다</b> — 근거 없는 서술은 애초에
	 * {@code AiDiagnosisService.parse}가 버리고, 정적 팩터리가 한 번 더 지킨다.
	 *
	 * <p>순서를 보존한다({@code @OrderColumn}) — 모델은 중요한 근거부터 적고, 화면은
	 * 그 순서대로 싣는다. 저장을 거치며 순서가 섞이면 재방문 화면이 다른 얘기를 한다.
	 *
	 * <p>즉시 로딩인 이유: 근거 몇 건짜리 작은 기록이고, 읽는 쪽({@code AiDiagnosisService})이
	 * 트랜잭션 밖이라 지연 로딩은 LazyInitializationException만 만든다.
	 */
	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "ai_diagnosis_record_evidence",
			joinColumns = @JoinColumn(name = "record_id"))
	@OrderColumn(name = "sort_order")
	private List<Evidence> evidence = new ArrayList<>();

	/** 결론을 더 확실히 하려면 필요하지만 우리에게 없는 정보. 비어 있어도 된다. */
	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "ai_diagnosis_record_unknown",
			joinColumns = @JoinColumn(name = "record_id"))
	@OrderColumn(name = "sort_order")
	@Column(name = "unknown_text", nullable = false, length = 500)
	private List<String> unknowns = new ArrayList<>();

	/** 이 서술을 만든 시각(서버 시계). 교체되면 갱신된다. */
	@Column(name = "generated_at", nullable = false)
	private Instant generatedAt;

	/**
	 * 주장 하나와 그 근거 수치. {@code AiDiagnosis.Evidence}의 저장 형태다.
	 *
	 * <p>세 칸이 모두 차야 근거다 — 응답 검증과 같은 계약을 저장 계층이 다시 지킨다.
	 * 검증을 통과 못 한 값이 저장돼 있으면 재방문 화면이 검증 없이 그걸 싣게 된다.
	 */
	@Embeddable
	@Getter
	@NoArgsConstructor(access = AccessLevel.PROTECTED)
	public static class Evidence {

		/** 근거가 된 지표 이름 ("구간 내 최단 간격"). */
		@Column(nullable = false)
		private String metric;

		/** 그 값 ("2일"). {@code value}는 SQL 예약어라 컬럼명만 피해 둔다. */
		@Column(name = "metric_value", nullable = false)
		private String value;

		/** 이 수치가 무엇을 말하는지 한 문장. */
		@Column(nullable = false, length = 500)
		private String claim;

		private Evidence(String metric, String value, String claim) {
			this.metric = metric;
			this.value = value;
			this.claim = claim;
		}

		public static Evidence of(String metric, String value, String claim) {
			if (isBlank(metric) || isBlank(value) || isBlank(claim)) {
				throw new IllegalArgumentException(
						"근거는 세 칸(metric·value·claim)이 모두 차야 합니다. (metric=%s, value=%s, claim=%s)"
								.formatted(metric, value, claim));
			}
			return new Evidence(metric, value, claim);
		}
	}

	private AiDiagnosisRecord(long teamId, int season, int windowDays, int minMatches,
	                          int analyzedFixtures, String headline, String sub,
	                          List<Evidence> evidence, List<String> unknowns,
	                          Instant generatedAt) {
		this.teamId = teamId;
		this.season = season;
		this.windowDays = windowDays;
		this.minMatches = minMatches;
		this.analyzedFixtures = analyzedFixtures;
		this.headline = headline;
		this.sub = sub;
		this.evidence = new ArrayList<>(evidence);
		this.unknowns = new ArrayList<>(unknowns);
		this.generatedAt = generatedAt;
	}

	/**
	 * 저장용 정적 팩터리. <b>검증을 통과한 서술만</b> 기록이 될 수 있다.
	 *
	 * <p>{@code AiDiagnosisService.parse}가 이미 빈 껍데기를 거르지만, 이 팩터리가 같은
	 * 불변식을 다시 지킨다 — 저장 경로가 하나 늘어날 때마다 검증을 기억해야 하는 구조 대신,
	 * 잘못된 기록은 아예 만들어질 수 없게 한다.
	 *
	 * @throws IllegalArgumentException 결론·부연이 비었거나, 근거가 없거나, 분모가 0 이하면
	 */
	public static AiDiagnosisRecord create(long teamId, int season, int windowDays, int minMatches,
	                                       int analyzedFixtures, String headline, String sub,
	                                       List<Evidence> evidence, List<String> unknowns,
	                                       Instant generatedAt) {
		if (teamId <= 0) {
			throw new IllegalArgumentException("teamId 는 양수여야 합니다. teamId=" + teamId);
		}
		validateContent(analyzedFixtures, headline, sub, evidence, unknowns, generatedAt);
		return new AiDiagnosisRecord(teamId, season, windowDays, minMatches,
				analyzedFixtures, headline, sub, evidence, unknowns, generatedAt);
	}

	/**
	 * 분모가 달라져 새로 받은 서술로 이 행을 교체한다. 키(팀·시즌·기준)는 그대로 두고
	 * 내용만 갈아 끼운다 — 행을 지웠다 다시 넣으면 유니크 제약과 경합하는 창이 생긴다.
	 */
	public void replaceWith(int analyzedFixtures, String headline, String sub,
	                        List<Evidence> evidence, List<String> unknowns,
	                        Instant generatedAt) {
		validateContent(analyzedFixtures, headline, sub, evidence, unknowns, generatedAt);
		this.analyzedFixtures = analyzedFixtures;
		this.headline = headline;
		this.sub = sub;
		// 컬렉션 객체를 갈아 끼우지 않고 내용만 바꾼다 — 영속 컬렉션의 더티 체킹이 안전하게 탄다.
		this.evidence.clear();
		this.evidence.addAll(evidence);
		this.unknowns.clear();
		this.unknowns.addAll(unknowns);
		this.generatedAt = generatedAt;
	}

	private static void validateContent(int analyzedFixtures, String headline, String sub,
	                                    List<Evidence> evidence, List<String> unknowns,
	                                    Instant generatedAt) {
		if (analyzedFixtures <= 0) {
			// 표본 게이트를 지난 결과만 저장된다 — 분모 0짜리 서술은 존재할 수 없다.
			throw new IllegalArgumentException(
					"분모(analyzedFixtures)는 양수여야 합니다. analyzedFixtures=" + analyzedFixtures);
		}
		if (isBlank(headline) || isBlank(sub)) {
			throw new IllegalArgumentException("결론과 부연은 비어 있을 수 없습니다.");
		}
		if (evidence == null || evidence.isEmpty()) {
			// 근거 없는 서술은 표시하지 않는다는 화면 계약(DG 4절)을 저장 계층이 같이 지킨다.
			throw new IllegalArgumentException("근거 없는 서술은 저장할 수 없습니다.");
		}
		if (unknowns == null || generatedAt == null) {
			throw new IllegalArgumentException("unknowns 와 generatedAt 는 필수입니다.");
		}
	}

	private static boolean isBlank(String s) {
		return s == null || s.isBlank();
	}
}
