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
 * 선수. 지금은 <b>결장 기록에 이름을 붙이기 위해서만</b> 존재한다.
 *
 * <p>이 앱은 선수 단위 분석을 하지 않는다(라인업·득점자·평점을 동기화하지 않는다).
 * 그런데 "이 경기에 5명이 빠졌다"만 보여 주면 사용자는 곧 "누가?"를 묻고, 그때
 * 선수 id만 있으면 화면에 숫자를 띄울 수밖에 없다. 이름을 담을 최소한의 자리다.
 *
 * <p>한글명은 두지 않았다. 팀·대회와 달리 선수는 수가 많고 이적이 잦아 시드로 관리할
 * 수 없다. 영문 원본을 그대로 쓴다 — 없는 매핑을 흉내 내느니 원본이 정직하다.
 */
@Entity
@Table(name = "player")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Player {

	/** API-Football player id를 그대로 PK로 사용. */
	@Id
	private Long id;

	/** API가 주는 표기명(예 "A. Diallo"). 성만 이니셜로 오는 형태 그대로다. */
	@Column(nullable = false)
	private String name;

	@Builder
	private Player(Long id, String name) {
		this.id = id;
		this.name = name;
	}

	/** 동기화가 가져온 사실만 갱신한다. */
	public void applyApiFacts(String name) {
		if (name != null && !name.isBlank()) {
			this.name = name;
		}
	}
}
