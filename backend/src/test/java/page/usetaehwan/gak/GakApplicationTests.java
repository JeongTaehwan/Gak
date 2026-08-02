package page.usetaehwan.gak;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 컨텍스트 기동 확인. test 프로파일은 인메모리 DB + 재생 클라이언트라
 * 실제 DB나 API 키 없이도 뜬다.
 */
@SpringBootTest
@ActiveProfiles("test")
class GakApplicationTests {

	@Test
	void contextLoads() {
	}

}
