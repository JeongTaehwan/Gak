package page.usetaehwan.gak.service.seed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import page.usetaehwan.gak.domain.Competition;
import page.usetaehwan.gak.domain.CompetitionType;
import page.usetaehwan.gak.repository.CompetitionRepository;

/**
 * 동기화 대상 대회를 기동 시 DB에 심는다({@code seeds/competitions.json}).
 *
 * <p>대회 목록을 API에서 가져오지 않는 이유가 둘 있다.
 * <ol>
 *   <li>요청 예산. {@code /leagues} 는 940개 대회를 주는데, 우리가 쓰는 건 열몇 개다.
 *       하루 100요청짜리 예산에서 목록 조회에 요청을 쓸 이유가 없다.</li>
 *   <li>{@code league.type}이 League/Cup 뿐이라 HYBRID를 판별할 수 없다. 어차피
 *       우리가 정해야 하는 값이면, 대회 목록 전체를 시드로 고정하는 편이 명확하다.</li>
 * </ol>
 *
 * <p>매 기동마다 시드 값으로 덮어쓴다(upsert). 시드가 이 필드들의 원본이므로,
 * 파일을 고치고 재기동하면 그게 반영되는 게 맞다.
 *
 * <h2>시드에서 뺀 대회는 내린다 — 지우지는 않는다</h2>
 * <p>예전에는 {@code displayed} 를 항상 {@code true} 로 덮어썼다. 그래서 <b>시드에서
 * 한 줄을 지워도 동기화가 멈추지 않았다</b> — 행이 그대로 남아 {@code displayed=true} 인
 * 채로 {@code SyncPlanner} 에 계속 잡혔다. 파일을 고쳤는데 아무 일도 안 일어나고 요청만
 * 계속 나가는, 이 프로젝트가 반복해서 경계하는 종류의 조용한 실패다.
 *
 * <p>지금은 시드에 없는 대회를 {@code displayed=false} 로 내린다. <b>행과 경기는 남긴다</b> —
 * 이미 받아 둔 경기가 대회를 잃으면 진단이 통째로 깨지고, 다시 넣기로 하면 한 줄로 돌아온다.
 * 노출 목록에서 빠지는 것과 데이터를 버리는 것은 다르다.
 */
@Component
@Order(1) // 동기화 스케줄러보다 먼저 대회가 존재해야 한다.
public class CompetitionSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(CompetitionSeeder.class);
	private static final String LOCATION = "classpath:seeds/competitions.json";

	private final CompetitionRepository competitionRepository;
	private final ResourceLoader resourceLoader;
	private final ObjectMapper objectMapper;

	public CompetitionSeeder(CompetitionRepository competitionRepository,
	                         ResourceLoader resourceLoader,
	                         ObjectMapper objectMapper) {
		this.competitionRepository = competitionRepository;
		this.resourceLoader = resourceLoader;
		this.objectMapper = objectMapper;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record CompetitionSeed(
			Long id,
			String name,
			String nameKo,
			String shortNameKo,
			String country,
			CompetitionType type,
			boolean calendarSeason
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record SeedFile(List<CompetitionSeed> competitions) {
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		List<CompetitionSeed> seeds = readSeeds();
		int created = 0;
		int updated = 0;

		// id별로 findById를 돌리면 시드 개수만큼 SELECT가 나간다. 한 번에 끌어와 맵으로 본다.
		Map<Long, Competition> existingById = competitionRepository
				.findAllById(seeds.stream().map(CompetitionSeed::id).toList())
				.stream()
				.collect(Collectors.toMap(Competition::getId, Function.identity()));

		for (CompetitionSeed seed : seeds) {
			Competition existing = existingById.get(seed.id());
			if (existing == null) {
				competitionRepository.save(Competition.builder()
						.id(seed.id())
						.name(seed.name())
						.nameKo(seed.nameKo())
						.shortNameKo(seed.shortNameKo())
						.country(seed.country())
						.type(seed.type())
						.calendarSeason(seed.calendarSeason())
						.displayed(true)
						.build());
				created++;
			} else {
				existing.applySeed(seed.name(), seed.nameKo(), seed.shortNameKo(), seed.country(),
						seed.type(), seed.calendarSeason(), true);
				updated++;
			}
		}
		int retired = retireUnseeded(seeds);
		log.info("대회 시드 반영: 신규 {}건, 갱신 {}건, 노출 해제 {}건 (시드 {}건)",
				created, updated, retired, seeds.size());
	}

	/**
	 * 시드에 없는데 아직 노출 중인 대회를 내린다. 삭제하지 않는 이유는 클래스 주석 참고.
	 *
	 * @return 이번에 내려간 대회 수
	 */
	private int retireUnseeded(List<CompetitionSeed> seeds) {
		Set<Long> seeded = seeds.stream().map(CompetitionSeed::id).collect(Collectors.toSet());
		int retired = 0;
		for (Competition competition : competitionRepository.findByDisplayedTrue()) {
			if (!seeded.contains(competition.getId())) {
				competition.hide();
				retired++;
				log.info("대회 노출 해제: {} ({}) — 시드에서 빠졌습니다. 경기 데이터는 남습니다",
						competition.getId(), competition.displayName());
			}
		}
		return retired;
	}

	/** 테스트가 시드 내용을 그대로 검증할 수 있도록 읽기만 분리해 둔다. */
	public List<CompetitionSeed> readSeeds() {
		try (InputStream in = resourceLoader.getResource(LOCATION).getInputStream()) {
			SeedFile file = objectMapper.readValue(in, SeedFile.class);
			if (file == null || file.competitions() == null || file.competitions().isEmpty()) {
				throw new IllegalStateException("대회 시드가 비어 있습니다: " + LOCATION);
			}
			return file.competitions();
		} catch (IOException e) {
			// 대회가 없으면 동기화 자체가 불가능하다. 여기는 조용히 넘어가면 안 된다.
			throw new UncheckedIOException("대회 시드를 읽지 못했습니다: " + LOCATION, e);
		}
	}
}
