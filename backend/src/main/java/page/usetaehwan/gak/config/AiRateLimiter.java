// requirements.md DG 8절 — 전역 상한 + IP 단위 상한의 조합
package page.usetaehwan.gak.config;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * AI 경로의 하루 호출 카운터. {@link #tryConsume(String)} 하나로
 * 통과/전역 초과/IP 초과를 판정한다.
 *
 * <p><b>소모 지점은 모델 호출 직전이다</b> — 서비스가 실제로 Anthropic 을 부르기
 * 직전에만 소모한다. 저장분 재사용(DG 7절)·표본 게이트·키 부재로 끝나는 요청은
 * 지출이 없으므로 세지 않는다. 요청 단위(인터셉터)로 세면 저장 히트인 진단 탭
 * 열기가 하루 한도를 태워, 지출 0인데 화면이 잠긴다.
 *
 * <p><b>카운터는 인메모리다 — 재시작하면 리셋된다.</b> 남용 방어 목적에는 수용한다.
 * 정확한 과금 집계가 필요해지면 {@code RequestBudget}처럼 저장소 이력 합산으로 옮긴다.
 */
@Component
public class AiRateLimiter {

	/**
	 * 판정 결과. 초과 사유(전역/IP)가 문구를 가른다 — 다른 실패로 뭉개지 않는다.
	 * 표시 방식은 확정(DG-OQ-21): 새 상태를 만들지 않고 기존 상태(unavailable /
	 * ANALYSIS_FAILED)에 이 사유 문구를 얹는다. 문구 자체의 최종 표현만 IN-OQ-06
	 * (상태별 문구 전반)과 함께 다듬는다.
	 */
	public enum Decision {
		ALLOWED(null),
		GLOBAL_EXCEEDED("오늘 AI 분석 요청이 서비스 전체 한도에 도달했습니다. 내일 다시 시도해 주세요"),
		IP_EXCEEDED("이 네트워크에서 오늘 보낼 수 있는 AI 분석 요청을 모두 사용했습니다. 내일 다시 시도해 주세요");

		private final String message;

		Decision(String message) {
			this.message = message;
		}

		public boolean allowed() {
			return this == ALLOWED;
		}

		/** 사용자에게 보여줄 사유. ALLOWED 에는 없다. */
		public String message() {
			return message;
		}
	}

	private final AiRateLimitProperties properties;
	private final Clock clock;
	private final Object rolloverLock = new Object();

	private volatile Window window;

	public AiRateLimiter(AiRateLimitProperties properties, Clock clock) {
		this.properties = properties;
		this.clock = clock;
		this.window = new Window(today());
	}

	/**
	 * 이 요청이 한도 안인가. 통과면 오늘치 카운터를 1 소모한다.
	 *
	 * @param ip 클라이언트 IP. null(식별 불가 — 신뢰 프록시 뒤 무-XFF)이면 IP 단위
	 *           상한을 건너뛰고 전역 상한만 적용한다. 위조 가능한 값으로 버킷을
	 *           만들지도, 모두를 프록시 IP 한 버킷에 묶지도 않는다
	 */
	public Decision tryConsume(String ip) {
		if (!properties.enabled()) {
			return Decision.ALLOWED;
		}
		Window w = currentWindow();

		// increment-and-check — 읽고-검사하고-쓰는 사이에 다른 요청이 끼어들면 상한을
		// 넘겨 통과시키므로, 원자적으로 올린 결과값으로만 판정한다.
		//
		// 판정 순서는 전역 먼저다. 전역이 초과면 IP 맵을 아예 건드리지 않는다 —
		// 그래야 맵 항목은 전역 상한을 통과한 요청만 만들 수 있어 맵 크기가 전역
		// 상한으로 유계가 된다. IP 를 바꿔 가며 뿌려도 메모리가 자라지 않는다.
		if (w.global.incrementAndGet() > properties.globalDaily()) {
			return Decision.GLOBAL_EXCEEDED;
		}
		if (ip == null) {
			return Decision.ALLOWED;
		}
		int perIpCount = w.perIp.computeIfAbsent(ip, key -> new AtomicInteger()).incrementAndGet();
		if (perIpCount > properties.perIpDaily()) {
			// IP 초과로 거부된 요청이 전역 예산까지 태우면 한 IP 의 남용이 전체를
			// 잠근다 — IP당 상한의 목적(다른 사용자 보호)이 무너지므로 되돌린다.
			w.global.decrementAndGet();
			return Decision.IP_EXCEEDED;
		}
		return Decision.ALLOWED;
	}

	/** 지금 추적 중인 IP 수. 맵 크기가 유계인지 확인하는 테스트·관측용. */
	int trackedIpCount() {
		return window.perIp.size();
	}

	private Window currentWindow() {
		LocalDate today = today();
		Window current = window;
		if (current.date.equals(today)) {
			return current;
		}
		// 하루 전환 — 새 Window 로 통째로 갈아 끼워 "전역은 리셋됐는데 IP 는 어제 것"
		// 같은 어긋난 상태를 만들지 않는다. 경계에 걸친 요청 한둘이 어제 창에 세어질
		// 수 있지만 남용 방어 목적에는 무해하다.
		synchronized (rolloverLock) {
			if (!window.date.equals(today)) {
				window = new Window(today);
			}
			return window;
		}
	}

	private LocalDate today() {
		// 상한의 "하루"는 UTC 날짜 기준 — 서버 타임존 설정에 흔들리지 않는다.
		return LocalDate.ofInstant(Instant.now(clock), ZoneOffset.UTC);
	}

	/** 하루치 카운터 묶음. 날짜가 바뀌면 통째로 교체된다. */
	private static final class Window {
		final LocalDate date;
		final AtomicInteger global = new AtomicInteger();
		final ConcurrentHashMap<String, AtomicInteger> perIp = new ConcurrentHashMap<>();

		Window(LocalDate date) {
			this.date = date;
		}
	}
}
