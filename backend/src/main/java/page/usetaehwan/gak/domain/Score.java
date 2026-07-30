package page.usetaehwan.gak.domain;

import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 홈/원정 득점 한 쌍. API-Football의 score 하위 객체(halftime/fulltime/extratime/penalty)를
 * 표현하는 값 타입(Embeddable)이다. 각 국면이 없을 수 있어(연장/승부차기 미발생) 컴포넌트는 nullable.
 *
 * <p>Fixture에서 네 번 @Embedded되며 컬럼명은 각 국면 접두어로 오버라이드한다.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Score {

	private Integer home;
	private Integer away;

	@Builder
	private Score(Integer home, Integer away) {
		this.home = home;
		this.away = away;
	}

	public static Score of(Integer home, Integer away) {
		return new Score(home, away);
	}

	/** 해당 국면 데이터가 실제로 있는지(둘 중 하나라도 값이 있으면 존재로 본다). */
	public boolean isPresent() {
		return home != null || away != null;
	}
}
