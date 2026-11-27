package co.redsalud.andina.episodio;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;

import co.redsalud.andina.RedsaludAndinaApplication;

/** The sync contract under the conditions of a rural link: repeats, shuffled order and a process restart in between. */
@SpringBootTest
@ActiveProfiles("test")
class SyncResilienceTests {

	static final Instant T0 = Instant.parse("2026-03-01T10:00:00Z");
	static final int EPISODES = 10;

	@Autowired EpisodeSyncService syncService;
	@Autowired JdbcTemplate jdbc;

	@Test
	void sameBatchThreeTimesInDifferentOrderDuplicatesAndLosesNothing() {
		UUID doctor = UUID.randomUUID();
		List<SyncEvent> batch = batch(UUID.randomUUID());
		long started = System.nanoTime();

		List<Map<UUID, String>> outcomes = new ArrayList<>();
		for (long seed = 1; seed <= 3; seed++) {
			List<SyncEvent> shuffled = new ArrayList<>(batch);
			Collections.shuffle(shuffled, new Random(seed));
			outcomes.add(outcomeByEvent(asDoctor(doctor, () -> syncService.sync(shuffled))));
		}

		assertThat(outcomes.get(1)).isEqualTo(outcomes.get(0));
		assertThat(outcomes.get(2)).isEqualTo(outcomes.get(0));
		assertThat(outcomes.get(0).values()).noneMatch(outcome -> outcome.startsWith("REJECTED"));
		assertThat(outcomes.get(0)).hasSize(batch.size());
		assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMinutes(15)); // sync objective
		assertThat(countEpisodes(jdbc, batch)).isEqualTo(EPISODES);
	}

	@Test
	void anotherProcessAfterARestartReturnsTheSameResultsAndStillDoesNotDuplicate(@TempDir Path dir) {
		UUID doctor = UUID.randomUUID();
		List<SyncEvent> batch = batch(UUID.randomUUID());
		String url = "jdbc:h2:file:" + dir.resolve("andina") + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";

		Map<UUID, String> beforeRestart;
		try (ConfigurableApplicationContext first = start(url, dir)) {
			beforeRestart = outcomeByEvent(asDoctor(doctor, () -> first.getBean(EpisodeSyncService.class).sync(batch)));
		}
		// The first process is gone: nothing in memory survives, only the database does.
		try (ConfigurableApplicationContext second = start(url, dir)) {
			Map<UUID, String> afterRestart = outcomeByEvent(asDoctor(doctor, () -> second.getBean(EpisodeSyncService.class).sync(batch)));

			assertThat(afterRestart).isEqualTo(beforeRestart);
			assertThat(countEpisodes(second.getBean(JdbcTemplate.class), batch)).isEqualTo(EPISODES);
		}
	}

	private static ConfigurableApplicationContext start(String url, Path dir) {
		return new SpringApplicationBuilder(RedsaludAndinaApplication.class).profiles("test").run(
				"--server.port=0", "--spring.main.banner-mode=off", "--spring.datasource.url=" + url,
				"--spring.datasource.username=sa", "--spring.datasource.password=",
				"--redsalud.attachments.local-dir=" + dir.resolve("attachments"));
	}

	/** Ten episodes: open, two clinical fields and the admission each. Event ids are fixed, so a resend is the same event. */
	private static List<SyncEvent> batch(UUID runId) {
		List<SyncEvent> events = new ArrayList<>();
		for (int i = 0; i < EPISODES; i++) {
			UUID episode = id(runId, "episode-" + i);
			UUID patient = id(runId, "patient-" + i);
			events.add(new SyncEvent(id(runId, "open-" + i), episode, 0, SyncEventType.EPISODE_OPENED, T0, patient, null, null, null));
			events.add(new SyncEvent(id(runId, "notes-" + i), episode, 1, SyncEventType.FIELD_UPDATED, T0.plusSeconds(1), null, "NOTES", "note " + i, null));
			events.add(new SyncEvent(id(runId, "vitals-" + i), episode, 2, SyncEventType.FIELD_UPDATED, T0.plusSeconds(2), null, "VITAL_SIGNS", "BP " + i, null));
			events.add(new SyncEvent(id(runId, "admit-" + i), episode, 3, SyncEventType.EPISODE_STATUS_CHANGED, T0.plusSeconds(3), null, null, "ADMITTED", null));
		}
		return events;
	}

	private static UUID id(UUID runId, String name) {
		return UUID.nameUUIDFromBytes((runId + name).getBytes());
	}

	private static Map<UUID, String> outcomeByEvent(List<SyncResult> results) {
		return results.stream().collect(Collectors.toMap(SyncResult::eventId, r -> r.status() + ":" + r.message() + ":" + r.conflict()));
	}

	private static long countEpisodes(JdbcTemplate jdbc, List<SyncEvent> batch) {
		return batch.stream().map(SyncEvent::aggregateId).distinct()
				.filter(id -> jdbc.queryForObject("select count(*) from episodio.episode where id = ?", Long.class, id) == 1).count();
	}

	private static <T> T asDoctor(UUID doctor, Supplier<T> action) {
		Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(doctor.toString())
				.claim("cognito:groups", List.of("rural-doctor")).claim("custom:center", "center-1").build();
		var context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_RURAL_DOCTOR")), doctor.toString()));
		SecurityContextHolder.setContext(context);
		try {
			return action.get();
		} finally {
			SecurityContextHolder.clearContext();
		}
	}
}
