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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 소식 한 건. <b>우리는 주장하지 않는다. 옮기기만 한다.</b>
 *
 * <h2>저장하는 것과 저장하지 않는 것</h2>
 * <p>제목 · 링크 · 발행시각 · 출처. 그게 전부다.
 *
 * <p><b>{@code description}/{@code summary} 필드는 받지도 저장하지도 않는다.</b> RSS에
 * 들어 있어도 그건 기자가 쓴 창작 문장이고, 두세 문장이면 원문을 안 읽어도 되게 만든다.
 * 우리의 법적 안전은 "헤드라인은 짧으니 괜찮다"에서 오는 게 아니라 <b>원문을 대체하지
 * 않는다</b>는 데서 온다. 파서가 아예 그 필드를 읽지 않는다({@code RssParser}) — 받아 놓고
 * 안 쓰는 것과 애초에 안 받는 것은 다르다.
 *
 * <p>기사 본문은 말할 것도 없다. 크롤링하지 않는다.
 *
 * <h2>왜 {@code Team} 연관이 아니라 {@code teamId} 인가</h2>
 * <p><b>일부러 FK를 걸지 않았다.</b> 뉴스는 진단과 <b>다른 층</b>이고, 층을 나눈다는 건
 * 서로를 참조하지 않는다는 뜻이다. {@code ManyToOne Team} 을 걸면 뉴스 수집이 동기화
 * 상태에 묶인다 — 팀이 아직 DB에 없으면 소식을 저장조차 못 하게 된다. 뉴스는 우리가
 * 계산하는 값이 아니라 남이 발행한 사실의 사본이라, 우리 DB 상태와 무관하게 들어와야 한다.
 *
 * <h2>진단과 섞이지 않는다</h2>
 * <p>이 엔티티는 {@code TeamDiagnostics} 어디에도 실리지 않고, {@code DiagnosisPromptFactory}
 * 는 이걸 읽지 않는다. 진단의 모든 근거는 우리 DB에서 <b>다시 계산되는</b> 값이어야 하는데,
 * 소식은 다시 계산할 수 있는 종류의 것이 아니다. 한 줄에 섞이는 순간 진단 전체가
 * 검증 불가능해진다.
 */
@Entity
@Table(
		name = "news_item",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_news_item_dedup_key",
				columnNames = "dedup_key"),
		indexes = {
				// "이 팀의 최신 소식" — 화면의 유일한 질의.
				@Index(name = "idx_news_item_team_published", columnList = "team_id, published_at desc"),
				// 소스 내리기(kill switch)와 태깅 대상 조회에 쓴다.
				@Index(name = "idx_news_item_source", columnList = "source_key")
		})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NewsItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/**
	 * 중복 제거 키 — 정규화한 원문 URL({@code ArticleKey}).
	 *
	 * <p>발행자가 준 {@code guid}를 쓰지 않는 이유는 실측 때문이다. BBC는 같은 기사를
	 * 한 피드에 두 번 실으면서 <b>guid 뒤에 다른 조각(#0 / #14)을 붙여</b> 보낸다.
	 * Sky는 guid를 아예 주지 않는다. 링크를 정규화하는 쪽이 둘 다 처리한다.
	 */
	@Column(name = "dedup_key", nullable = false, length = 500)
	private String dedupKey;

	/** 설정에 적힌 소스 식별자(예 {@code bbc-football}). on/off 스위치와 삭제의 단위다. */
	@Column(name = "source_key", nullable = false, length = 64)
	private String sourceKey;

	/** 화면에 그대로 표시할 출처명(예 {@code BBC Sport}). 감추지 않는 것이 안전의 근거다. */
	@Column(name = "source_name", nullable = false, length = 128)
	private String sourceName;

	/** 공식이냐 언론이냐. 지금은 전부 {@link SourceTier#MEDIA}. */
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private SourceTier tier;

	/** 헤드라인 원문. 번역·요약·가공하지 않는다. */
	@Column(nullable = false, length = 500)
	private String title;

	/** 원문 링크. 사용자는 여기서 우리 사이트를 떠난다 — 그게 설계다. */
	@Column(nullable = false, length = 1000)
	private String link;

	/** 발행자가 밝힌 시각. 우리가 받은 시각이 아니다. */
	@Column(name = "published_at", nullable = false)
	private Instant publishedAt;

	/**
	 * 이 소식이 어느 팀 것인가. <b>키워드 게이트가 정한다</b>({@code TeamNewsMatcher}).
	 * FK가 아닌 이유는 클래스 주석 참고.
	 */
	@Column(name = "team_id", nullable = false)
	private Long teamId;

	/**
	 * 갈래. <b>null 이 정상이다</b> — 태거가 아직 안 돌았거나, 못 돌았거나, 키가 없을 때.
	 *
	 * <p>null 이면 화면은 배지 없이 소식만 보여 준다. AI가 없는 건 오류가 아니라 정상
	 * 상태라는 원칙이 여기서도 같다.
	 */
	@Enumerated(EnumType.STRING)
	@Column(length = 16)
	private NewsCategory category;

	/** 우리가 이 줄을 처음 본 시각. 발행 시각과 다르다(피드가 늦게 실어 줄 수 있다). */
	@Column(name = "fetched_at", nullable = false)
	private Instant fetchedAt;

	@Builder
	private NewsItem(Long id, String dedupKey, String sourceKey, String sourceName,
	                 SourceTier tier, String title, String link, Instant publishedAt,
	                 Long teamId, NewsCategory category, Instant fetchedAt) {
		this.id = id;
		this.dedupKey = dedupKey;
		this.sourceKey = sourceKey;
		this.sourceName = sourceName;
		this.tier = tier;
		this.title = title;
		this.link = link;
		this.publishedAt = publishedAt;
		this.teamId = teamId;
		this.category = category;
		this.fetchedAt = fetchedAt;
	}

	/**
	 * 태거가 매긴 갈래를 붙인다.
	 *
	 * <p><b>이미 붙은 갈래는 덮어쓰지 않는다.</b> 같은 헤드라인에 같은 모델을 다시 돌려도
	 * 답이 미묘하게 달라질 수 있는데, 사용자가 어제 본 배지가 오늘 조용히 바뀌는 쪽이
	 * 태그가 하나 없는 것보다 나쁘다. (예측 채점을 한 번만 하는 것과 같은 이유다.)
	 *
	 * <p>{@code null}을 주면 아무것도 하지 않는다 — 태거가 모르겠다고 답한 경우다.
	 */
	public void applyCategory(NewsCategory category) {
		if (this.category == null && category != null) {
			this.category = category;
		}
	}

	/** 아직 갈래가 없는가(= 태깅 대상인가). */
	public boolean needsCategory() {
		return category == null;
	}
}
