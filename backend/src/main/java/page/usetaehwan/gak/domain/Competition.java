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

	@Builder
	private Competition(Long id, String name, String nameKo, String country,
	                    CompetitionType type, boolean calendarSeason, boolean displayed) {
		this.id = id;
		this.name = name;
		this.nameKo = nameKo;
		this.country = country;
		this.type = type;
		this.calendarSeason = calendarSeason;
		this.displayed = displayed;
	}

	/**
	 * 시드 값으로 갱신한다. 시드가 이 필드들의 원본이므로 매 기동 시 덮어쓴다.
	 * id는 바꾸지 않는다 — 바뀐다면 그건 다른 대회다.
	 */
	public void applySeed(String name, String nameKo, String country,
	                      CompetitionType type, boolean calendarSeason, boolean displayed) {
		this.name = name;
		this.nameKo = nameKo;
		this.country = country;
		this.type = type;
		this.calendarSeason = calendarSeason;
		this.displayed = displayed;
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
}
