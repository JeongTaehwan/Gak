package page.usetaehwan.gak.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 경기장. id/name/city는 API가 주지만 좌표는 주지 않는다.
 * 위경도는 시드(주요 도시)로만 채우고, 없으면 null로 둔다.
 *
 * <p>좌표가 null이면 이동거리 계산·표기를 "생략"한다(에러가 아니다).
 * 이동거리 자체는 저장하지 않고 두 Venue 좌표 + 하버사인 공식으로 그때그때 계산한다.
 */
@Entity
@Table(name = "venue")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Venue {

	/** API-Football venue id를 그대로 PK로 사용(자체 생성 X). */
	@Id
	private Long id;

	@Column(nullable = false)
	private String name;

	private String city;

	private String country;

	/** 위도. API 미제공 → 시드로만 채움. 없으면 null(이동거리 생략). */
	private Double latitude;

	/** 경도. 위와 동일. */
	private Double longitude;

	@Builder
	private Venue(Long id, String name, String city, String country,
	             Double latitude, Double longitude) {
		this.id = id;
		this.name = name;
		this.city = city;
		this.country = country;
		this.latitude = latitude;
		this.longitude = longitude;
	}

	/** 좌표가 모두 있어야 이동거리 계산 대상이 된다. */
	public boolean hasCoordinates() {
		return latitude != null && longitude != null;
	}

	/** 시드 로딩(다음 단계)에서 좌표를 채울 때 사용. */
	public void assignCoordinates(Double latitude, Double longitude) {
		this.latitude = latitude;
		this.longitude = longitude;
	}
}
