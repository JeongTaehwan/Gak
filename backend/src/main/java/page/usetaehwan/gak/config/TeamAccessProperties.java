// requirements.md 2장 — 출시 단계와 팀 선택
package page.usetaehwan.gak.config;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 출시 단계 제한 — <b>1차 비공개 검증에서 다룰 팀</b>.
 *
 * <h2>왜 코드 상수가 아니라 설정인가</h2>
 * <p>다팀 지원은 "만들어야 하는 기능"이 아니라 <b>이미 만들어진 파생 계산을 여는 일</b>이다.
 * 선택 가능 팀은 조회 시즌의 선택 기준 대회 경기에서 계산되고 있고, 이 목록은 그 위에
 * 얹은 <b>출시 단계 게이트</b>일 뿐이다. 그래서 공개 전환이 코드 변경이 아니라 값 하나를
 * 비우는 일이 되어야 한다.
 *
 * <h2>비우면 열린다</h2>
 * <p>{@code allowed-team-ids} 가 비어 있으면 파생 계산 결과가 그대로 선택 목록이 된다.
 * 값이 있으면 그 교집합만 남는다. <b>목록에 있다고 없는 팀이 생기지는 않는다</b> — 33을
 * 적어도 그 시즌에 맨유의 선택 기준 대회 경기가 없으면 목록에 뜨지 않는다. 게이트는
 * 좁히기만 하고 넓히지 않는다.
 *
 * @param allowedTeamIds 이 단계에서 다룰 팀 id. 비면 제한 없음(공개 버전)
 */
@ConfigurationProperties(prefix = "gak.teams")
public record TeamAccessProperties(Set<Long> allowedTeamIds) {

	public TeamAccessProperties {
		allowedTeamIds = allowedTeamIds == null ? Set.of() : Set.copyOf(allowedTeamIds);
	}

	/** 출시 단계 제한이 걸려 있는가. 화면이 "왜 하나뿐인지"를 말할 수 있도록 응답에 실린다. */
	public boolean restricted() {
		return !allowedTeamIds.isEmpty();
	}

	public boolean allows(Long teamId) {
		return allowedTeamIds.isEmpty() || allowedTeamIds.contains(teamId);
	}
}
