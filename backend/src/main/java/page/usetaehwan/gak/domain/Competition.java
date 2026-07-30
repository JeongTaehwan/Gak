package page.usetaehwan.gak.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 대회(리그·컵·유럽대항전). API-Football의 league에 대응한다.
 *
 * <p>리그/컵 판별은 API가 직접 주지 않으므로 league.standings(순위표 존재)와
 * round 문자열을 근거로 {@link CompetitionType}을 채운다(판별 로직은 서비스 계층).
 */
@Entity
@Table(name = "competition")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Competition {

	/** API-Football league id를 PK로 사용. */
	@Id
	private Long id;

	@Column(nullable = false)
	private String name;

	private String country;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private CompetitionType type;

	/** 앱에서 이 대회를 노출할지 여부. 기본 노출. */
	@Column(nullable = false)
	private boolean displayed = true;

	@Builder
	private Competition(Long id, String name, String country,
	                    CompetitionType type, boolean displayed) {
		this.id = id;
		this.name = name;
		this.country = country;
		this.type = type;
		this.displayed = displayed;
	}
}
