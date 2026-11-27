package co.redsalud.andina.auditoria;

import static co.redsalud.andina.TestTokens.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import co.redsalud.andina.identidad.Role;
import io.micrometer.core.instrument.MeterRegistry;

/** Every refused request leaves an ACCESS_DENIED record, whatever the reason for the refusal. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AccessDenialAuditTests {

	@Autowired MockMvc mockMvc;
	@Autowired AuditStore auditStore;
	@Autowired MeterRegistry meters;

	UUID doctor = UUID.randomUUID();
	UUID patient = UUID.randomUUID();
	UUID episodeId = UUID.randomUUID();

	@Test
	void everyKindOfDenialIsRecordedWithItsActor() throws Exception {
		openEpisode();
		double deniedBefore = meters.counter("redsalud.access.denied").count();
		RequestPostProcessor administrativeUser = jwt().jwt(j -> j.subject(administrative().toString())); // no clinical role at all
		UUID nonClinical = UUID.randomUUID();
		UUID unrelatedSpecialist = UUID.randomUUID();
		UUID otherPatient = UUID.randomUUID();

		denied(administrativeUser, administrative(), null);
		denied(as(nonClinical, Role.NETWORK_MANAGER), nonClinical, null);
		denied(as(unrelatedSpecialist, Role.SPECIALIST), unrelatedSpecialist, "curiosity");
		denied(as(otherPatient, Role.PATIENT), otherPatient, null);

		assertThat(meters.counter("redsalud.access.denied").count() - deniedBefore).isEqualTo(4);
		var reasonClaimed = auditStore.findByResource("http-request", "GET /api/episodes/" + episodeId).stream()
				.filter(r -> r.actor().equals(unrelatedSpecialist)).findFirst().orElseThrow();
		assertThat(reasonClaimed.reason()).isEqualTo("curiosity");
	}

	@Test
	void denialDecidedByTheFilterChainIsRecordedToo() throws Exception {
		UUID rural = UUID.randomUUID();
		mockMvc.perform(get("/actuator/metrics").with(as(rural, Role.RURAL_DOCTOR))).andExpect(status().isForbidden());

		assertThat(auditStore.findByResource("http-request", "GET /actuator/metrics"))
				.extracting(AuditRecord::actor, AuditRecord::kind)
				.contains(org.assertj.core.groups.Tuple.tuple(rural, AuditKind.ACCESS_DENIED));
	}

	@Test
	void deniedAuditReportRequestIsRecordedToo() throws Exception {
		UUID rural = UUID.randomUUID();
		mockMvc.perform(get("/api/audit/resources/episode/{id}/reads", episodeId).with(as(rural, Role.RURAL_DOCTOR)))
				.andExpect(status().isForbidden());

		assertThat(auditStore.findByResource("http-request", "GET /api/audit/resources/episode/" + episodeId + "/reads"))
				.extracting(AuditRecord::actor, AuditRecord::kind)
				.containsExactly(org.assertj.core.groups.Tuple.tuple(rural, AuditKind.ACCESS_DENIED));
	}

	private void denied(RequestPostProcessor caller, UUID actor, String reason) throws Exception {
		var request = get("/api/episodes/{id}", episodeId).with(caller);
		if (reason != null) {
			request.header("X-Access-Reason", reason);
		} else {
			request.header("X-Access-Reason", "needed");
		}
		mockMvc.perform(request).andExpect(status().isForbidden());

		assertThat(auditStore.findByResource("http-request", "GET /api/episodes/" + episodeId))
				.filteredOn(r -> r.actor().equals(actor))
				.singleElement()
				.satisfies(r -> assertThat(r.kind()).isEqualTo(AuditKind.ACCESS_DENIED));
		// A refused read is not a read: nothing is stored as if the document had been seen.
		assertThat(auditStore.findByResource("episode", episodeId.toString())).isEmpty();
	}

	private UUID administrativeId;

	private UUID administrative() {
		if (administrativeId == null) {
			administrativeId = UUID.randomUUID();
		}
		return administrativeId;
	}

	private void openEpisode() throws Exception {
		String event = """
				[{"eventId":"%s","aggregateId":"%s","sequence":0,"type":"EPISODE_OPENED","occurredAt":"%s","patientId":"%s"}]"""
				.formatted(UUID.randomUUID(), episodeId, Instant.parse("2026-03-01T10:00:00Z"), patient);
		mockMvc.perform(post("/api/sync/events").with(as(doctor, Role.RURAL_DOCTOR, "center-1", null))
				.contentType(MediaType.APPLICATION_JSON).content(event)).andExpect(status().isOk());
	}
}
