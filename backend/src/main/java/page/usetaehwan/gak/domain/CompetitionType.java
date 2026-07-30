package page.usetaehwan.gak.domain;

/**
 * 대회 성격. API-Football은 이 값을 직접 주지 않으므로
 * league.standings(순위표 존재 여부)와 round 문자열로 판별해서 채운다.
 * <ul>
 *   <li>{@link #LEAGUE} — 순위표 기반 정규 리그 (EPL, 라리가 …)</li>
 *   <li>{@link #CUP} — 토너먼트/컵 (FA컵, 챔피언스리그 녹아웃 …)</li>
 *   <li>{@link #HYBRID} — 조별리그 + 토너먼트 혼합 (챔피언스리그 전체 …)</li>
 * </ul>
 */
public enum CompetitionType {
	LEAGUE,
	CUP,
	HYBRID
}
