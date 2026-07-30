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
