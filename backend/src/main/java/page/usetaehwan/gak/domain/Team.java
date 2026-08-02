package page.usetaehwan.gak.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 팀. API-Football의 team에 대응한다.
 *
 * <p>표기 규칙
 * <ul>
 *   <li>{@code name} — API가 준 영문 원본(항상 존재).</li>
 *   <li>{@code nameKo} — 한글명(시드 매핑). 없으면 화면에서 {@code name}으로 fallback.</li>
 *   <li>{@code shortNameKo} — 짧은 한글명("울버햄튼 원더러스" → "울버햄튼"). nullable.</li>
 *   <li>{@code code} — API 3글자 코드(예 "MUN"). 없을 수 있음.</li>
 * </ul>
 *
 * <p>주의:
 * <ul>
 *   <li>{@code code}가 없으면 팀명 첫 3자음으로 자동 생성한다 — 서비스 계층 책임(엔티티엔 저장 필드만).</li>
 *   <li>링(테두리) 컬러는 {@code code} 해시로 프론트에서 계산한다 — 저장하지 않는다.</li>
 * </ul>
 */
@Entity
@Table(name = "team")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Team {

	/** API-Football team id를 PK로 사용. */
	@Id
	private Long id;

	/** 영문 원본명(항상 존재). */
	@Column(nullable = false)
	private String name;

	/** 한글명. 없으면 null → 화면에서 name으로 대체. */
	private String nameKo;

	/** API 3글자 코드. 없을 수 있음(없으면 서비스에서 자음 3자로 채움). */
	private String code;

	/** 짧은 한글명. nullable. */
	private String shortNameKo;

	/** 홈 구장. API/시드 상황에 따라 없을 수 있어 nullable + LAZY. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "home_venue_id")
	private Venue homeVenue;

	@Builder
	private Team(Long id, String name, String nameKo, String code,
	            String shortNameKo, Venue homeVenue) {
		this.id = id;
		this.name = name;
		this.nameKo = nameKo;
		this.code = code;
		this.shortNameKo = shortNameKo;
		this.homeVenue = homeVenue;
	}

	/**
	 * fixture 응답이 준 사실만 갱신한다(팀명, 홈 구장).
	 *
	 * <p><b>한글명 계열은 절대 건드리지 않는다.</b> nameKo/shortNameKo/code는 시드가 원본이라,
	 * 동기화가 이 값을 덮으면 매 동기화마다 한글 표기가 사라졌다 살아나기를 반복한다.
	 * 어떤 필드의 원본이 어디인지를 메서드 단위로 갈라 두는 게 이 규칙의 강제 수단이다.
	 *
	 * @param name      API가 준 영문 원본명. null/공백이면 기존 값을 유지한다
	 * @param homeVenue 이 경기의 홈 경기장. null이면 기존 값을 유지한다
	 */
	public void applyApiFacts(String name, Venue homeVenue) {
		if (name != null && !name.isBlank()) {
			this.name = name;
		}
		if (homeVenue != null) {
			this.homeVenue = homeVenue;
		}
	}

	/** 시드(team-names-ko.json)가 주는 표기 정보를 채운다. API 동기화 경로와 분리한다. */
	public void applySeedNames(String nameKo, String shortNameKo, String code) {
		if (nameKo != null && !nameKo.isBlank()) {
			this.nameKo = nameKo;
		}
		if (shortNameKo != null && !shortNameKo.isBlank()) {
			this.shortNameKo = shortNameKo;
		}
		if (code != null && !code.isBlank()) {
			this.code = code;
		}
	}

	/**
	 * 화면 표기명 계산(파생값 — 저장하지 않음).
	 * 짧은 한글명 → 한글명 → 영문 원본 순으로 fallback.
	 */
	public String displayName() {
		if (shortNameKo != null && !shortNameKo.isBlank()) {
			return shortNameKo;
		}
		if (nameKo != null && !nameKo.isBlank()) {
			return nameKo;
		}
		return name;
	}
}
