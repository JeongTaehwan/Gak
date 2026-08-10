package page.usetaehwan.gak.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 대회(리그·컵·유럽대항전). API-Football의 league에 대응한다.
 *
 * <p><b>{@link CompetitionType}은 API가 알려 주지 않는다.</b> API의 {@code league.type}은
 * League/Cup 두 값뿐이라 챔피언스리그처럼 "조별리그(순위) + 녹아웃(토너먼트)"인 대회를
 * 표현하지 못한다. 그래서 우리 타입은 시드({@code seeds/competitions.json})가 직접 정한다.
 *
 * <p>대회는 <b>id로만</b> 다룬다. 이름은 유일하지 않다 — "Serie A"는 이탈리아(135)와
 * 브라질(71)에 모두 있고, "FA Cup"·"Premier League" 같은 이름은 수십 개 나라에 겹친다.
 * 여자부·유소년·하부 리그도 이름이 거의 같다(예: DFB Pokal 81 / 여자 947 / 유소년 715).
 */
@Entity
@Table(name = "competition", indexes = {
		// 노출 대회 목록 조회 + 스케줄러의 동기화 대상 선정에 함께 쓰인다.
		@Index(name = "idx_competition_displayed_type", columnList = "displayed, type")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Competition {

	/** API-Football league id를 PK로 사용. */
	@Id
	private Long id;

	@Column(nullable = false)
	private String name;

	/** 한글 대회명(시드). 없으면 화면에서 {@link #name}으로 fallback. */
	private String nameKo;

	/**
	 * 짧은 한글 대회명(시드). 타임라인 뱃지처럼 폭이 좁은 자리에 쓴다
	 * ("UEFA 챔피언스리그" → "챔스"). 없으면 {@link #nameKo}로 fallback.
	 *
	 * <p>표기를 프론트가 정하지 않고 여기 두는 이유는 {@code Team.shortNameKo}와 같다 —
	 * 대회 id로 라벨을 분기하는 코드가 화면에 생기면, 대회를 하나 추가할 때마다
	 * 서버와 화면 두 곳을 고쳐야 하고 한쪽만 고친 순간 뱃지가 빈다.
	 */
	private String shortNameKo;

	private String country;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private CompetitionType type;

	/**
	 * 시즌이 <b>한 해 안에서</b> 끝나는 대회인가(브라질·아르헨티나·K리그: 3~12월).
	 * false면 유럽식 걸침 시즌(8월~다음 해 5월)이라 시즌 번호가 시작 연도가 된다.
	 * API에 시즌 번호를 넘겨야 하므로 이 구분이 필요하다.
	 */
	@Column(nullable = false)
	private boolean calendarSeason;

	/** 앱에서 이 대회를 노출할지 여부. 기본 노출. */
	@Column(nullable = false)
	private boolean displayed = true;

	/**
	 * <b>팀 선택 기준 대회</b>인가 — 시드({@code seeds/competitions.json})가 정한다.
	 *
	 * <p>{@link #displayed}·동기화 대상과 <b>다른 개념</b>이다. 컵과 유럽대항전은 동기화도
	 * 하고 화면에도 나오지만 선택 기준이 아니다. 챔피언스리그만 뛴 팀을 목록에 올리면
	 * 그 팀의 순위표도 리그 진단도 없는 채로 화면이 열린다. 반대로 FA컵 예선에서 들어온
	 * 비리그 클럽 711개는 이 플래그 하나로 <b>자연히</b> 빠진다 — 별도 필터가 필요 없다.
	 *
	 * <p>여기 저장하는 것은 "어떤 대회가 선택 기준인가"뿐이고, <b>팀 목록 자체는 저장하지
	 * 않는다.</b> 선택 가능 팀은 조회 시즌에 이 대회의 경기가 있는 팀에서 매번 파생 계산한다
	 * — 저장하면 승격·강등이 일어난 순간 과거 시즌 목록이 조용히 틀려진다.
	 *
	 * <p>{@code columnDefinition} 에 기본값을 박아 둔 건 이미 행이 있는 DB에 컬럼을 더할 때
	 * {@code not null} 만으로는 ALTER 가 실패하기 때문이다. 값의 원본은 어디까지나 시드이고,
	 * 기동할 때마다 {@code applySeed} 가 덮어쓴다.
	 */
	@Column(nullable = false, columnDefinition = "boolean not null default false")
	private boolean selectable;

	@Builder
	private Competition(Long id, String name, String nameKo, String shortNameKo, String country,
	                    CompetitionType type, boolean calendarSeason, boolean displayed,
	                    boolean selectable) {
		this.id = id;
		this.name = name;
		this.nameKo = nameKo;
		this.shortNameKo = shortNameKo;
		this.country = country;
		this.type = type;
		this.calendarSeason = calendarSeason;
		this.displayed = displayed;
		this.selectable = selectable;
	}

	/**
	 * 시드 값으로 갱신한다. 시드가 이 필드들의 원본이므로 매 기동 시 덮어쓴다.
	 * id는 바꾸지 않는다 — 바뀐다면 그건 다른 대회다.
	 */
	public void applySeed(String name, String nameKo, String shortNameKo, String country,
	                      CompetitionType type, boolean calendarSeason, boolean displayed,
	                      boolean selectable) {
		this.name = name;
		this.nameKo = nameKo;
		this.shortNameKo = shortNameKo;
		this.country = country;
		this.type = type;
		this.calendarSeason = calendarSeason;
		this.displayed = displayed;
		this.selectable = selectable;
	}

	/**
	 * 노출·동기화 대상에서 내린다. 시드에서 빠진 대회에 쓴다.
	 *
	 * <p><b>지우는 게 아니다.</b> 이 대회로 이미 받아 둔 경기가 남아 있고, 그 경기들이
	 * 대회를 잃으면 그 팀 진단이 통째로 깨진다. 다시 넣기로 하면 시드에 한 줄
	 * 되돌리는 것으로 원상복구된다.
	 */
	public void hide() {
		this.displayed = false;
		// 선택 기준에서도 함께 내린다. 시드에서 뺐는데 팀 선택 기준으로는 계속 남아 있으면,
		// 목록에는 뜨는데 그 팀의 리그 데이터는 더 이상 들어오지 않는 상태가 된다.
		this.selectable = false;
	}

	/**
	 * 주어진 날짜 기준으로 이 대회의 "현재 시즌" 번호.
	 *
	 * <p>API-Football은 시즌을 시작 연도 정수로 준다. 걸침 시즌 대회는 7월을 경계로
	 * 새 시즌이 시작한다고 본다(프리시즌·예선이 7월에 열리므로 이 시점부터 새 시즌 일정이 열린다).
	 */
	public int seasonFor(LocalDate today) {
		if (calendarSeason) {
			return today.getYear();
		}
		return today.getMonthValue() >= 7 ? today.getYear() : today.getYear() - 1;
	}

	/** 화면 표기명(파생값 — 저장하지 않는다). */
	public String displayName() {
		return (nameKo != null && !nameKo.isBlank()) ? nameKo : name;
	}

	/** 좁은 자리(타임라인 뱃지)용 표기명. 짧은 한글명 → 한글명 → 영문 순 fallback. */
	public String displayShortName() {
		if (shortNameKo != null && !shortNameKo.isBlank()) {
			return shortNameKo;
		}
		return displayName();
	}
}
