package co.redsalud.andina.legado;

import static co.redsalud.andina.TestTokens.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import co.redsalud.andina.auditoria.AuditStore;
import co.redsalud.andina.identidad.CareRelationships;
import co.redsalud.andina.identidad.Role;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class LegacyHistoryEndpointTests {

	@Autowired MockMvc mockMvc;
	@Autowired CareRelationships careRelationships;
	@Autowired AuditStore auditStore;

	UUID specialist = UUID.randomUUID();
	UUID patient = UUID.randomUUID();

	@Test
	void exposesExpiryAndSourceAndAuditsEveryRead() throws Exception {
		careRelationships.grant(specialist, patient);

		mockMvc.perform(get("/api/patients/{id}/legacy-history", patient).with(as(specialist, Role.SPECIALIST))
						.header("X-Access-Reason", "second opinion"))
				.andExpect(status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().exists("X-Data-Freshness"))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().exists("X-Data-Expires-At"))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Data-Source", "LEGACY_DIRECT"))
				.andExpect(jsonPath("$.source").value("LEGACY_DIRECT"))
				.andExpect(jsonPath("$.expiresAt").isNotEmpty())
				.andExpect(jsonPath("$.freshAt").isNotEmpty())
				.andExpect(jsonPath("$.episodes.length()").value(2));
		mockMvc.perform(get("/api/patients/{id}/legacy-history", patient).with(as(specialist, Role.SPECIALIST))
						.header("X-Access-Reason", "second opinion"))
				.andExpect(jsonPath("$.source").value("VIEW"));

		assertThat(auditStore.findByResource("legacy-history", patient.toString())).hasSize(2);
	}

	@Test
	void requiresReasonAndPatientAccess() throws Exception {
		mockMvc.perform(get("/api/patients/{id}/legacy-history", patient).with(as(specialist, Role.SPECIALIST)))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/patients/{id}/legacy-history", patient).with(as(specialist, Role.SPECIALIST))
						.header("X-Access-Reason", "curiosity"))
				.andExpect(status().isForbidden());
	}
}
