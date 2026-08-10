// requirements.md 2~4장 — 팀 선택 · 시즌과 회고 · 선택 대상이 아닌 시즌
package page.usetaehwan.gak.dto.analysis;

import java.util.List;

/**
 * 입력 화면이 읽는 <b>단 하나의 선행 응답</b> — "지금 어느 시즌을 보고 있고, 그 시즌에
 * 고를 수 있는 팀은 누구인가".
 *
 * <h2>시즌이 팀 목록의 선행 입력이다</h2>
 * <p>조회 필터가 아니다. 승격·강등이 있으므로 <b>그 시즌에 1부였던 팀</b>이 그 시즌의
 * 선택 대상이고, 시즌이 정해지기 전에는 목록을 만들 수 없다. 그래서 시즌 판정과 팀 목록이
 * 한 응답으로 함께 온다 — 따로 부르면 그 사이에 서로 다른 시즌을 보는 순간이 생긴다.
 *
 * <h2>고른 팀을 조용히 바꾸지 않는다</h2>
 * <p>URL의 {@code teamId} 가 그 시즌의 선택 대상이 아니어도 목록의 다른 팀으로 대체하지
 * 않는다. {@link #selected} 가 그 팀을 그대로 담고 {@link Selected#eligible} 이 false 로
 * 온다. 화면은 팀을 유지한 채 "이 시즌에는 선택 대상 1부 리그 기록이 없습니다"라고 말한다.
 * 대체해 버리면 사용자는 자기가 고른 적 없는 팀의 숫자를 자기 팀의 숫자로 읽는다.
 *
 * <h2>회고 이동은 한 칸씩이다</h2>
 * <p>{@link #previousSeason}·{@link #nextSeason} 이 그 한 칸이다. 목록을 통째로 내려
 * 드롭다운을 만들지 않는다 — 시즌 목록 화면은 만들지 않기로 했고, 무엇보다 화면이
 * "다음 칸이 있는가"만 알면 버튼 두 개로 충분하다. 없으면 null 이고, 그때 화면은
 * 버튼을 비활성화한다("이전 시즌 데이터가 없습니다").
 *
 * @param season          이번에 보고 있는 시즌. URL이 고정할 값. 조회 가능 시즌이 하나도
 *                        없으면 null
 * @param calendarSeason  한 해 안에서 끝나는 시즌인가(K리그). 표기가 "2025"인지 "23/24"인지를
 *                        가른다. 판정은 대회 시드가 하고 문자열은 화면이 만든다
 * @param currentSeason   자동 판정한 현재 시즌 — 치른 경기가 있는 시즌 중 가장 큰 값
 * @param current         지금 보는 시즌이 그 현재 시즌인가. true면 "다음 시즌"이 없다
 * @param previousSeason  한 칸 이전 조회 가능 시즌. 없으면 null
 * @param nextSeason      한 칸 이후 조회 가능 시즌(현재 시즌을 넘지 않는다). 없으면 null
 * @param teams           그 시즌 선택 가능 팀. <b>파생 계산 결과</b>이고 저장된 목록이 아니다
 * @param restricted      출시 단계 제한이 걸려 있는가(1차 비공개 검증). 목록이 하나뿐인 것이
 *                        데이터가 없어서인지 단계 제한 때문인지를 화면이 갈라 말할 수 있게 한다
 * @param selected        요청이 지정한 팀. 지정하지 않았으면 null
 */
public record TeamSelection(
		Integer season,
		boolean calendarSeason,
		Integer currentSeason,
		boolean current,
		Integer previousSeason,
		Integer nextSeason,
		List<Option> teams,
		boolean restricted,
		Selected selected
) {

	/**
	 * 목록 한 줄.
	 *
	 * @param teamId 이후 모든 화면이 함께 받는 그 id
	 * @param name   표기명(한글 우선)
	 * @param code   3글자 코드. 팀 마크가 쓴다. 없을 수 있다
	 */
	public record Option(long teamId, String name, String code) {
	}

	/**
	 * 지금 고른 팀.
	 *
	 * @param eligible 이 시즌의 선택 대상인가. false면 <b>선택 기준 리그 기록이 없다</b>는
	 *                 뜻일 뿐이다 — 2부라거나 동기화가 빠졌다고 단정하지 않는다
	 */
	public record Selected(long teamId, String name, String code, boolean eligible) {
	}

	/** 조회 가능 시즌이 하나도 없을 때. "선택 가능 팀 0개"와 구분된다. */
	public static TeamSelection noSeasons(boolean restricted) {
		return new TeamSelection(null, false, null, false, null, null, List.of(), restricted, null);
	}
}
