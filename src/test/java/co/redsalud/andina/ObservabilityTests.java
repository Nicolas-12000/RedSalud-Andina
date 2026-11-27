package co.redsalud.andina;

import static co.redsalud.andina.TestTokens.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import co.redsalud.andina.identidad.Role;
import io.micrometer.tracing.Tracer;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@AutoConfigureMetrics // tests run without metrics export by default; this test reads the Prometheus scrape
class ObservabilityTests {

	@Autowired MockMvc mockMvc;
	@Autowired Tracer tracer;

	@Test
	void healthProbesAreOpenAndTheRestOfActuatorIsForTheNetworkManager() throws Exception {
		mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
		mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
		mockMvc.perform(get("/actuator/prometheus").with(as(UUID.randomUUID(), Role.RURAL_DOCTOR))).andExpect(status().isForbidden());
	}

	@Test
	void prometheusExposesTheBucketsOfTheSloAndOurMetrics() throws Exception {
		mockMvc.perform(get("/api/agenda/slots").param("specialty", "none").with(as(UUID.randomUUID(), Role.RURAL_DOCTOR)))
				.andExpect(status().isOk());
		mockMvc.perform(get("/actuator/metrics").with(as(UUID.randomUUID(), Role.RURAL_DOCTOR))).andExpect(status().isForbidden());

		String scrape = mockMvc.perform(get("/actuator/prometheus").with(as(UUID.randomUUID(), Role.NETWORK_MANAGER)))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

		assertThat(scrape).contains("http_server_requests_seconds_bucket")
				.contains("le=\"2.0\"").contains("le=\"6.0\"") // the 2 s and 6 s objectives
				.contains("redsalud_access_denied_total");
	}

	@Test
	void tracingIsWiredThroughMicrometer() {
		assertThat(tracer).isNotNull();
	}
}
