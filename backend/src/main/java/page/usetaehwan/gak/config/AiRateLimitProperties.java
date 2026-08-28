// requirements.md DG 8절 — 호출 한도와 남용 방어
package page.usetaehwan.gak.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 경로(AI 진단·자유 질문) 호출 한도. 유료 API 호출 경로라서 한도는
 * <b>전역 상한 + IP 단위 상한의 조합</b>으로 센다 — 앱 전체 상한이 지출을 막고,
 * IP당 상한이 개인 남용을 막는다. 로그인은 도입하지 않는다 (DG-OQ-20).
 *
 * <p>한도값은 확정(DG-OQ-21, 2026-08-25 오너 위임): 전역 200/일 · IP당 20/일.
 * 운영하며 조정할 일이 생기면 설정만 바꾼다.
 *
 * @param enabled        한도 적용 on/off. 끄면 완전 통과(테스트·로컬 편의). 기본 true
 * @param globalDaily    하루 전역 상한(모든 IP 합산). 잠정 200
 * @param perIpDaily     하루 IP당 상한. 잠정 20
 * @param trustedProxies X-Forwarded-For 를 인정할 프록시 주소 목록. 여기 없는
 *                       주소에서 온 요청의 XFF 는 위조로 간주하고 소켓 IP 로 센다.
 *                       기본은 루프백(로컬의 Next.js 프록시) — 배포 토폴로지가 정해지면
 *                       프록시 주소를 추가한다
 */
@ConfigurationProperties(prefix = "gak.ai-limit")
public record AiRateLimitProperties(
		Boolean enabled,
		Integer globalDaily,
		Integer perIpDaily,
		java.util.List<String> trustedProxies
) {

	public AiRateLimitProperties {
		enabled = enabled == null || enabled;
		globalDaily = (globalDaily == null || globalDaily < 1) ? 200 : globalDaily;
		perIpDaily = (perIpDaily == null || perIpDaily < 1) ? 20 : perIpDaily;
		trustedProxies = (trustedProxies == null || trustedProxies.isEmpty())
				? java.util.List.of("127.0.0.1", "0:0:0:0:0:0:0:1", "::1")
				: java.util.List.copyOf(trustedProxies);
	}
}
