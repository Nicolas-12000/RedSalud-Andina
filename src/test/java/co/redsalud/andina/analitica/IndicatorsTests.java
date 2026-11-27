package co.redsalud.andina.analitica;

import static co.redsalud.andina.TestTokens.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import co.redsalud.andina.episodio.EpisodeOpened;
import co.redsalud.andina.identidad.Role;
import co.redsalud.andina.interconsulta.ConsultStatus;
import co.redsalud.andina.interconsulta.ConsultStatusChanged;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class IndicatorsTests {

	static final Instant T0 = Instant.parse("2026-03-01T10:00:00Z");

	@Autowired MockMvc mockMvc;
	@Autowired IndicatorsService indicatorsService;
	@Autowired ApplicationEventPublisher publisher;
	@Autowired TransactionTemplate transactions;
	@Autowired JdbcTemplate jdbc;

	String center = "center-" + UUID.randomUUID();
	UUID patient = UUID.randomUUID();

	@Test
	void aggregatesPerCenterFromEventsWithoutExposingPatients() {
		UUID closedConsult = UUID.randomUUID();
		UUID openConsult = UUID.randomUUID();
		transactions.executeWithoutResult(tx -> {
			publisher.publishEvent(new EpisodeOpened(UUID.randomUUID(), patient, center, T0));
			publisher.publishEvent(new EpisodeOpened(UUID.randomUUID(), patient, center, T0.plusSeconds(60)));
			publisher.publishEvent(changed(closedConsult, null, ConsultStatus.REQUESTED, T0));
			publisher.publishEvent(changed(closedConsult, ConsultStatus.REQUESTED, ConsultStatus.SCHEDULED, T0.plusSeconds(100)));
			publisher.publishEvent(changed(closedConsult, ConsultStatus.SCHEDULED, ConsultStatus.ATTENDED, T0.plusSeconds(600)));
			publisher.publishEvent(changed(closedConsult, ConsultStatus.ATTENDED, ConsultStatus.CLOSED, T0.plusSeconds(700)));
			publisher.publishEvent(changed(openConsult, null, ConsultStatus.REQUESTED, T0));
		});

		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			CenterIndicators indicators = mine();
			assertThat(indicators.teleconsults()).isEqualTo(2);
			assertThat(indicators.uniquePatients()).isEqualTo(1);
			assertThat(indicators.interconsultasRequested()).isEqualTo(2);
			assertThat(indicators.interconsultasClosed()).isEqualTo(1);
			assertThat(indicators.closeRate()).isEqualTo(0.5);
			assertThat(indicators.averageWaitSeconds()).isEqualTo(600.0);
		});
		assertThat(jdbc.queryForList("select patient_pseudonym from analitica.episode_fact where center_id = ?", String.class, center))
				.isNotEmpty()
				.noneMatch(value -> value.contains(patient.toString()));
	}

	@Test
	void indicatorsAreOnlyForAnalysts() throws Exception {
		mockMvc.perform(get("/api/analytics/centers").with(as(UUID.randomUUID(), Role.SPECIALIST))).andExpect(status().isForbidden());
		mockMvc.perform(get("/api/analytics/centers").with(as(UUID.randomUUID(), Role.NETWORK_MANAGER))).andExpect(status().isOk());
	}

	private CenterIndicators mine() {
		var all = asAnalyst(() -> indicatorsService.byCenter());
		return all.stream().filter(i -> i.centerId().equals(center)).findFirst().orElse(
				new CenterIndicators(center, 0, 0, 0, 0, null, null));
	}

	private <T> T asAnalyst(java.util.function.Supplier<T> action) {
		var jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("t").header("alg", "none")
				.subject(UUID.randomUUID().toString()).claim("cognito:groups", List.of("network-manager")).build();
		var context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
		context.setAuthentication(new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(
				jwt, List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_NETWORK_MANAGER")), jwt.getSubject()));
		org.springframework.security.core.context.SecurityContextHolder.setContext(context);
		try {
			return action.get();
		} finally {
			org.springframework.security.core.context.SecurityContextHolder.clearContext();
		}
	}

	private ConsultStatusChanged changed(UUID consult, ConsultStatus from, ConsultStatus to, Instant at) {
		return new ConsultStatusChanged(consult, patient, "cardiology", center, from, to, null, at);
	}
}
