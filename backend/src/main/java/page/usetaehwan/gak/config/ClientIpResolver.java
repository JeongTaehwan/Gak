// requirements.md DG 8절 — IP 단위 상한의 식별 재료
package page.usetaehwan.gak.config;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 요청의 클라이언트 IP 판별. 컨트롤러(HTTP 계층)가 여기서 IP 를 뽑아 서비스에
 * 불투명한 식별 문자열로 넘긴다 — 서비스는 서블릿을 모른다.
 *
 * <h2>X-Forwarded-For 는 신뢰 프록시에서 온 것만 인정한다</h2>
 * <p>XFF 는 아무 클라이언트나 쓸 수 있는 평문 헤더다. 무조건 믿으면 호출자가
 * 요청마다 값을 바꿔 IP 상한을 우회한다. 그래서 <b>소켓 주소(remoteAddr)가 신뢰
 * 프록시 목록에 있을 때만</b> XFF 첫 토큰(최초 클라이언트)을 쓰고, 그 외에는 헤더를
 * 무시하고 소켓 주소로 센다 — 백엔드를 직접 때리는 호출자는 자기 IP 로 세어진다.
 *
 * <h2>식별 불가는 null 이다</h2>
 * <p>신뢰 프록시에서 왔는데 XFF 가 없으면(프록시가 전달을 껐거나 로컬 직접 접속)
 * 클라이언트를 식별할 방법이 없다. 그때 프록시 소켓 주소로 세면 모든 사용자가
 * 프록시 IP 하나의 버킷에 묶여 IP당 상한이 사실상 전체 상한이 된다. 그래서
 * <b>null(식별 불가)을 반환하고, 한도는 전역 상한만 적용한다</b> — 모르는 것을
 * 아는 척하지 않는다 (DG-OQ-20).
 */
@Component
public class ClientIpResolver {

	private final Set<String> trustedProxies;

	public ClientIpResolver(AiRateLimitProperties properties) {
		this.trustedProxies = Set.copyOf(properties.trustedProxies());
	}

	/** @return 클라이언트 IP. 식별할 수 없으면 null — 호출자는 전역 상한만 적용한다 */
	public String resolve(HttpServletRequest request) {
		String socket = request.getRemoteAddr();
		if (!trustedProxies.contains(socket)) {
			return socket;
		}
		String forwarded = request.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) {
			String first = forwarded.split(",", 2)[0].trim();
			if (!first.isEmpty()) {
				return first;
			}
		}
		return null;
	}
}
