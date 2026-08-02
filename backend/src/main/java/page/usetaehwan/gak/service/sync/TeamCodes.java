package page.usetaehwan.gak.service.sync;

/**
 * 팀 코드 보정. {@code /fixtures} 응답의 team에는 code가 없고 시드에도 없는 팀이 있는데,
 * 프론트가 링 컬러를 code 해시로 계산하므로 빈 값이면 색이 안 정해진다.
 *
 * <p>규칙: 팀명에서 <b>자음만</b> 뽑아 앞 3글자. 모음이 빠지면 서로 다른 팀이 같은 코드로
 * 뭉칠 확률이 낮아진다("Real"/"Roma" → REL/RM). 3글자가 안 되면 원래 글자로 채운다.
 * 어디까지나 표시용 fallback이라 유일성을 보장하지는 않는다.
 */
final class TeamCodes {

	private static final String VOWELS = "AEIOU";

	private TeamCodes() {
	}

	static String derive(String name) {
		if (name == null || name.isBlank()) {
			return null;
		}
		String upper = name.toUpperCase().replaceAll("[^A-Z]", "");
		if (upper.isEmpty()) {
			return null; // 비라틴 문자 팀명 — 코드 없이 둔다.
		}

		StringBuilder consonants = new StringBuilder();
		for (char c : upper.toCharArray()) {
			if (VOWELS.indexOf(c) < 0) {
				consonants.append(c);
			}
			if (consonants.length() == 3) {
				return consonants.toString();
			}
		}
		// 자음이 3개가 안 되면(짧은 이름) 원래 글자로 마저 채운다.
		StringBuilder padded = new StringBuilder(consonants);
		for (int i = 0; i < upper.length() && padded.length() < 3; i++) {
			padded.append(upper.charAt(i));
		}
		return padded.toString();
	}
}
