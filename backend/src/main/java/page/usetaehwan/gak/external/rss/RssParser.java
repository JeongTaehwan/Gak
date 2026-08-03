package page.usetaehwan.gak.external.rss;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * RSS 2.0 / Atom 을 {@link RssItem} 목록으로 바꾼다.
 *
 * <h2>읽는 것만 읽는다</h2>
 * <p>{@code title}, {@code link}, {@code pubDate}({@code updated}/{@code published}).
 * <b>{@code description}·{@code content:encoded}·썸네일은 코드가 손대지 않는다.</b>
 * 저장 단계에서 거르는 게 아니라 파싱 단계에서 존재하지 않는다 — 나중에 누가
 * "이미 받아 왔으니 써 볼까" 할 여지를 남기지 않는다.
 *
 * <h2>XXE 차단</h2>
 * <p>피드는 <b>우리가 통제하지 않는 XML</b>이다. 외부 엔티티를 그대로 처리하면 발행자(또는
 * 발행자를 가로챈 쪽)가 우리 서버의 파일을 읽거나 내부 주소로 요청을 보내게 만들 수 있다.
 * 그래서 DTD·외부 엔티티를 전부 끄고 {@code secure-processing} 을 켠다.
 *
 * <h2>날짜를 못 읽으면 그 줄을 버린다</h2>
 * <p>발행 시각이 없으면 "지금"으로 채우고 싶어지는데, 그러면 3년 전 기사가 방금 올라온
 * 것처럼 화면 맨 위에 뜬다. <b>모르는 값을 0이나 now 로 채우지 않는다</b>는 프로젝트 원칙이
 * 여기에도 그대로 적용된다.
 */
public final class RssParser {

	private static final Logger log = LoggerFactory.getLogger(RssParser.class);

	/**
	 * RSS 의 {@code pubDate} 는 RFC 1123 이다("Mon, 03 Aug 2026 12:47:00 +0000").
	 * Atom 은 ISO-8601 을 쓴다. 둘 다 시도한다.
	 */
	private static final DateTimeFormatter[] DATE_FORMATS = {
			DateTimeFormatter.RFC_1123_DATE_TIME,
			DateTimeFormatter.ISO_OFFSET_DATE_TIME,
			DateTimeFormatter.ISO_ZONED_DATE_TIME
	};

	private RssParser() {
	}

	/**
	 * @param xml 피드 원문
	 * @return 읽어 낸 항목들. 파싱 자체가 실패하면 빈 목록(예외를 던지지 않는다 —
	 *         피드 하나가 깨진 게 수집 전체를 멈출 이유는 아니다)
	 */
	public static List<RssItem> parse(byte[] xml) {
		if (xml == null || xml.length == 0) {
			return List.of();
		}
		Document doc;
		try {
			doc = builder().parse(new ByteArrayInputStream(xml));
		} catch (Exception e) {
			log.warn("피드 XML 파싱 실패: {}", e.toString());
			return List.of();
		}
		doc.getDocumentElement().normalize();

		List<RssItem> items = new ArrayList<>();
		int rss = doc.getElementsByTagName("item").getLength();
		collect(doc.getElementsByTagName("item"), items, false);

		int seen = rss;
		if (items.isEmpty()) {
			// RSS 항목이 없으면 Atom 으로 본다.
			NodeList entries = doc.getElementsByTagName("entry");
			seen = rss + entries.getLength();
			collect(entries, items, true);
		}

		// 버린 게 있으면 시끄럽게 말한다. Sky 가 BST 를 보내 20건이 통째로 사라졌을 때
		// 아무 로그도 없어서 한참 못 찾았다 — 조용한 전량 손실이 가장 나쁜 실패다.
		int dropped = seen - items.size();
		if (dropped > 0) {
			log.warn("피드 항목 {}건 중 {}건을 버렸습니다(제목·링크·발행시각 중 하나가 없음).",
					seen, dropped);
		}
		return items;
	}

	private static void collect(NodeList nodes, List<RssItem> out, boolean atom) {
		for (int i = 0; i < nodes.getLength(); i++) {
			Node node = nodes.item(i);
			if (node.getNodeType() != Node.ELEMENT_NODE) {
				continue;
			}
			Element el = (Element) node;
			String title = text(el, "title");
			String link = atom ? atomLink(el) : text(el, "link");
			Instant published = parseDate(atom
					? firstNonBlank(text(el, "published"), text(el, "updated"))
					: firstNonBlank(text(el, "pubDate"), text(el, "dc:date")));

			RssItem item = new RssItem(clean(title), clean(link), published);
			if (item.usable()) {
				out.add(item);
			}
		}
	}

	/** Atom 의 링크는 {@code <link href="..."/>} 이고, {@code rel="alternate"} 가 본문이다. */
	private static String atomLink(Element entry) {
		NodeList links = entry.getElementsByTagName("link");
		String fallback = null;
		for (int i = 0; i < links.getLength(); i++) {
			Element link = (Element) links.item(i);
			String href = link.getAttribute("href");
			if (href == null || href.isBlank()) {
				continue;
			}
			String rel = link.getAttribute("rel");
			if (rel == null || rel.isBlank() || "alternate".equals(rel)) {
				return href;
			}
			if (fallback == null) {
				fallback = href;
			}
		}
		return fallback;
	}

	/**
	 * 자식 엘리먼트의 텍스트. <b>직계 자식만</b> 본다 —
	 * {@code getElementsByTagName} 은 손자까지 훑어서, 항목 안에 중첩된 다른 구조가 있으면
	 * 엉뚱한 값을 집어 온다.
	 */
	private static String text(Element parent, String tag) {
		NodeList children = parent.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);
			if (child.getNodeType() == Node.ELEMENT_NODE && tag.equals(child.getNodeName())) {
				return child.getTextContent();
			}
		}
		return null;
	}

	private static String firstNonBlank(String a, String b) {
		if (a != null && !a.isBlank()) {
			return a;
		}
		return b;
	}

	/**
	 * 제목에 섞여 오는 HTML 태그와 공백을 정리한다.
	 *
	 * <p>발행자가 제목에 {@code <b>}나 개행을 넣어 보내는 경우가 있다. 태그를 지우는 것은
	 * 요약이 아니라 <b>표기 정리</b>다 — 글자는 하나도 바꾸지 않는다.
	 */
	private static String clean(String raw) {
		if (raw == null) {
			return null;
		}
		return raw.replaceAll("<[^>]+>", "")
				.replace('\n', ' ')
				.replace('\r', ' ')
				.replaceAll("\\s{2,}", " ")
				.trim();
	}

	/**
	 * 숫자 오프셋 대신 약어를 쓰는 발행자를 위한 변환표.
	 *
	 * <p><b>실측에서 걸린 문제다.</b> Sky Sports 는 {@code "Mon, 03 Aug 2026 13:45:00 BST"}
	 * 를 보내는데, {@code RFC_1123_DATE_TIME} 은 {@code BST} 를 모른다. 그래서 20건
	 * <b>전부</b> 발행 시각이 null 이 되고, "시각 없으면 버린다" 규칙에 걸려 피드 하나가
	 * 통째로 사라졌다. 조용히.
	 *
	 * <p><b>모호한 약어는 일부러 넣지 않았다.</b> {@code IST} 는 아일랜드(+01)·인도(+05:30)·
	 * 이스라엘(+02)이고 {@code CST} 는 미국(-06)·중국(+08)이다. 잘못 고르면 헤드라인이
	 * 몇 시간에서 며칠까지 어긋난 자리에 꽂힌다 — 시각을 모르는 것보다 나쁘다.
	 * (좌표 별칭에서 억지 매칭을 안 하는 것과 같은 판단이다.)
	 */
	private static final Map<String, String> ZONE_OFFSETS = Map.of(
			"BST", "+0100",   // British Summer Time
			"CET", "+0100",
			"CEST", "+0200",
			"WET", "+0000",
			"WEST", "+0100",
			"EET", "+0200",
			"EEST", "+0300",
			"UTC", "+0000");

	private static final Pattern TRAILING_ZONE = Pattern.compile("\\s+([A-Z]{2,4})$");

	private static Instant parseDate(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String value = raw.trim();
		Instant parsed = tryFormats(value);
		if (parsed != null) {
			return parsed;
		}
		// 약어 시간대를 숫자 오프셋으로 바꿔 한 번 더 시도한다.
		String normalised = normaliseZone(value);
		if (!normalised.equals(value)) {
			parsed = tryFormats(normalised);
			if (parsed != null) {
				return parsed;
			}
		}
		log.debug("발행 시각을 읽지 못했습니다: {}", value);
		return null;
	}

	private static Instant tryFormats(String value) {
		for (DateTimeFormatter format : DATE_FORMATS) {
			try {
				return ZonedDateTime.parse(value, format).toInstant();
			} catch (DateTimeParseException ignored) {
				// 다음 형식으로.
			}
		}
		try {
			return Instant.parse(value);
		} catch (DateTimeParseException ignored) {
			return null;
		}
	}

	private static String normaliseZone(String value) {
		Matcher matcher = TRAILING_ZONE.matcher(value);
		if (!matcher.find()) {
			return value;
		}
		String offset = ZONE_OFFSETS.get(matcher.group(1));
		return offset == null ? value : matcher.replaceFirst(" " + offset);
	}

	/** DTD·외부 엔티티를 끈 파서. 외부에서 온 XML 을 다루는 한 이건 선택이 아니다. */
	private static DocumentBuilder builder() throws ParserConfigurationException {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
		factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		factory.setXIncludeAware(false);
		factory.setExpandEntityReferences(false);
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
		return factory.newDocumentBuilder();
	}

	/** 편의 — 문자열로 들어온 XML. */
	public static List<RssItem> parse(String xml) {
		return xml == null ? List.of() : parse(xml.getBytes(StandardCharsets.UTF_8));
	}
}
