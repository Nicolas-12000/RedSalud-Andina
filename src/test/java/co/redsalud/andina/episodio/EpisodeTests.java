package co.redsalud.andina.episodio;

import static co.redsalud.andina.TestTokens.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import co.redsalud.andina.auditoria.AuditKind;
import co.redsalud.andina.auditoria.AuditStore;
import co.redsalud.andina.episodio.internal.EpisodeRepository;
import co.redsalud.andina.episodio.internal.EpisodeFieldRepository;
import co.redsalud.andina.identidad.CareRelationships;
import co.redsalud.andina.identidad.Role;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class EpisodeTests {

	static final Instant T0 = Instant.parse("2026-03-01T10:00:00Z");

	@Autowired MockMvc mockMvc;
	@Autowired AuditStore auditStore;
	@Autowired EpisodeRepository episodeRepository;
	@Autowired EpisodeFieldRepository fieldRepository;
	@Autowired CareRelationships careRelationships;

	UUID doctor = UUID.randomUUID();
	UUID patient = UUID.randomUUID();
	UUID episodeId = UUID.randomUUID();

	@Test
	void replayingTheSameBatchGivesTheSameResultAndDuplicatesNothing() throws Exception {
		String batch = "[" + opened(UUID.randomUUID(), 0, T0) + "," + field(UUID.randomUUID(), 1, T0.plusSeconds(5), "NOTES", "cough", null) + "]";

		String first = sync(doctor, batch).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		String second = sync(doctor, batch).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

		assertThat(second).isEqualTo(first).doesNotContain("REJECTED");
		assertThat(episodeRepository.findById(episodeId)).isPresent();
		assertThat(fieldRepository.findByEpisodeId(episodeId)).hasSize(1);
	}

	@Test
	void eventsOfAnAggregateAreAppliedInSequenceEvenIfSentOutOfOrder() throws Exception {
		String batch = "[" + field(UUID.randomUUID(), 1, T0.plusSeconds(5), "REASON", "fever", null) + "," + opened(UUID.randomUUID(), 0, T0) + "]";

		sync(doctor, batch).andExpect(status().isOk()).andExpect(jsonPath("$[?(@.status=='REJECTED')]").isEmpty());
		assertThat(fieldRepository.findByEpisodeId(episodeId)).hasSize(1);
	}

	@Test
	void fieldEventsForAnUnknownEpisodeAreRejectedAndKeepTheOrder() throws Exception {
		String batch = "[" + field(UUID.randomUUID(), 0, T0, "NOTES", "a", null) + "," + field(UUID.randomUUID(), 1, T0.plusSeconds(1), "NOTES", "b", null) + "]";

		sync(doctor, batch).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].status").value("REJECTED"))
				.andExpect(jsonPath("$[1].status").value("REJECTED"));
	}

	@Test
	void bookingsAndSignedPrescriptionsAreNotResolvedAtTheEdge() throws Exception {
		String batch = "[" + event(UUID.randomUUID(), UUID.randomUUID(), 0, "BOOKING_REQUEST", T0, "null", "null", "null", null)
				+ "," + event(UUID.randomUUID(), UUID.randomUUID(), 0, "PRESCRIPTION_SIGN", T0, "null", "null", "null", null) + "]";

		sync(doctor, batch).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].status").value("REJECTED"))
				.andExpect(jsonPath("$[0].message").value(org.hamcrest.Matchers.containsString("SERVER_AUTHORITATIVE")))
				.andExpect(jsonPath("$[1].status").value("REJECTED"));
	}

	@Test
	void concurrentEditsKeepBothValuesInTheAuditTrail() throws Exception {
		UUID otherDoctor = UUID.randomUUID();
		sync(doctor, "[" + opened(UUID.randomUUID(), 0, T0) + "," + field(UUID.randomUUID(), 1, T0.plusSeconds(10), "NOTES", "from A", null) + "]");
		careRelationships.grant(otherDoctor, patient);

		// B edited without having seen A's change and its clock is later: B wins, A's value must survive in the audit.
		sync(otherDoctor, "[" + field(UUID.randomUUID(), 2, T0.plusSeconds(20), "NOTES", "from B", null) + "]")
				.andExpect(jsonPath("$[0].conflict").value(true));
		// C also edited blind but its clock is older than the stored value: C loses, its value must survive in the audit.
		sync(otherDoctor, "[" + field(UUID.randomUUID(), 3, T0.plusSeconds(5), "NOTES", "from C", null) + "]")
				.andExpect(jsonPath("$[0].conflict").value(true));

		assertThat(fieldRepository.findByEpisodeIdAndFieldName(episodeId, "NOTES")).get()
				.extracting("value").isEqualTo("from B");
		assertThat(auditStore.findByResource("episode", episodeId.toString()))
				.filteredOn(r -> r.kind() == AuditKind.FIELD_CONFLICT)
				.extracting("keptValue", "discardedValue")
				.containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple("from B", "from A"),
						org.assertj.core.groups.Tuple.tuple("from B", "from C"));
	}

	@Test
	void sequentialEditByTheSameDoctorIsNotAConflict() throws Exception {
		sync(doctor, "[" + opened(UUID.randomUUID(), 0, T0) + "," + field(UUID.randomUUID(), 1, T0.plusSeconds(10), "NOTES", "v1", null) + "]");

		sync(doctor, "[" + field(UUID.randomUUID(), 2, T0.plusSeconds(20), "NOTES", "v2", T0.plusSeconds(10)) + "]")
				.andExpect(jsonPath("$[0].conflict").value(false));

		assertThat(auditStore.findByResource("episode", episodeId.toString())).isEmpty();
	}

	@Test
	void episodeAdvancesFromWaitingRoomToCompletedOneStepAtATime() throws Exception {
		sync(doctor, "[" + opened(UUID.randomUUID(), 0, T0) + "]");

		// Skipping ADMITTED is refused and keeps the later events of the aggregate out as well.
		sync(doctor, "[" + statusEvent(UUID.randomUUID(), 1, T0.plusSeconds(10), "IN_PROGRESS") + "]")
				.andExpect(jsonPath("$[0].status").value("REJECTED"));
		UUID admission = UUID.randomUUID();
		sync(doctor, "[" + statusEvent(admission, 1, T0.plusSeconds(20), "ADMITTED") + "]")
				.andExpect(jsonPath("$[0].status").value("APPLIED"));
		// An old status event arriving again changes nothing.
		sync(doctor, "[" + statusEvent(UUID.randomUUID(), 2, T0.plusSeconds(30), "WAITING") + "]")
				.andExpect(jsonPath("$[0].status").value("APPLIED"));
		sync(doctor, "[" + statusEvent(UUID.randomUUID(), 3, T0.plusSeconds(40), "IN_PROGRESS") + "," + statusEvent(UUID.randomUUID(), 4, T0.plusSeconds(50), "COMPLETED") + "]")
				.andExpect(jsonPath("$[?(@.status=='REJECTED')]").isEmpty());

		mockMvc.perform(get("/api/episodes/{id}", episodeId).with(as(doctor, Role.RURAL_DOCTOR)).header("X-Access-Reason", "check"))
				.andExpect(jsonPath("$.status").value("COMPLETED"))
				.andExpect(jsonPath("$.admittedAt").value(T0.plusSeconds(20).toString()));
	}

	@Test
	void vitalSignsAndEvolutionNoteAreIndependentClinicalFields() throws Exception {
		sync(doctor, "[" + opened(UUID.randomUUID(), 0, T0) + ","
				+ field(UUID.randomUUID(), 1, T0.plusSeconds(5), "VITAL_SIGNS", "BP 120/80", null) + ","
				+ field(UUID.randomUUID(), 2, T0.plusSeconds(6), "EVOLUTION_NOTE", "stable", null) + "]")
				.andExpect(jsonPath("$[?(@.status=='REJECTED')]").isEmpty());

		assertThat(fieldRepository.findByEpisodeId(episodeId)).extracting("fieldName")
				.containsExactlyInAnyOrder("VITAL_SIGNS", "EVOLUTION_NOTE");
	}

	@Test
	void readingRequiresAReasonAndIsAudited() throws Exception {
		sync(doctor, "[" + opened(UUID.randomUUID(), 0, T0) + "]");

		mockMvc.perform(get("/api/episodes/{id}", episodeId).with(as(doctor, Role.RURAL_DOCTOR))).andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/episodes/{id}", episodeId).with(as(doctor, Role.RURAL_DOCTOR)).header("X-Access-Reason", " "))
				.andExpect(status().isBadRequest());
		assertThat(auditStore.findByResource("episode", episodeId.toString())).isEmpty();

		mockMvc.perform(get("/api/episodes/{id}", episodeId).with(as(doctor, Role.RURAL_DOCTOR)).header("X-Access-Reason", "consultation"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.patientId").value(patient.toString()));
		assertThat(auditStore.findByResource("episode", episodeId.toString())).singleElement()
				.satisfies(r -> {
					assertThat(r.kind()).isEqualTo(AuditKind.READ);
					assertThat(r.actor()).isEqualTo(doctor);
					assertThat(r.reason()).isEqualTo("consultation");
				});
	}

	@Test
	void aProfessionalWithoutRelationshipCannotReadAndLeavesNoReadRecord() throws Exception {
		sync(doctor, "[" + opened(UUID.randomUUID(), 0, T0) + "]");

		mockMvc.perform(get("/api/episodes/{id}", episodeId).with(as(UUID.randomUUID(), Role.SPECIALIST)).header("X-Access-Reason", "curiosity"))
				.andExpect(status().isForbidden());
		assertThat(auditStore.findByResource("episode", episodeId.toString())).isEmpty();
	}

	@Test
	void attachmentUploadIsIdempotentAndDownloadIsAudited() throws Exception {
		sync(doctor, "[" + opened(UUID.randomUUID(), 0, T0) + "]");
		UUID attachmentId = UUID.randomUUID();
		byte[] bytes = "xray-bytes".getBytes();

		for (int i = 0; i < 2; i++) {
			mockMvc.perform(multipart(HttpMethod.PUT, "/api/episodes/{e}/attachments/{a}", episodeId, attachmentId)
							.file(new MockMultipartFile("file", "xray.png", "image/png", bytes))
							.with(as(doctor, Role.RURAL_DOCTOR)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.id").value(attachmentId.toString()))
					.andExpect(jsonPath("$.sizeBytes").value(bytes.length));
		}

		mockMvc.perform(get("/api/attachments/{a}", attachmentId).with(as(doctor, Role.RURAL_DOCTOR)).header("X-Access-Reason", "review"))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.IMAGE_PNG))
				.andExpect(content().bytes(bytes));
		assertThat(auditStore.findByResource("attachment", attachmentId.toString())).hasSize(1);
	}

	private ResultActions sync(UUID caller, String batch) throws Exception {
		return mockMvc.perform(post("/api/sync/events").with(as(caller, Role.RURAL_DOCTOR, "center-1", null))
				.contentType(MediaType.APPLICATION_JSON).content(batch));
	}

	private String opened(UUID eventId, long sequence, Instant at) {
		return event(eventId, episodeId, sequence, "EPISODE_OPENED", at, "\"" + patient + "\"", "null", "null", null);
	}

	private String statusEvent(UUID eventId, long sequence, Instant at, String target) {
		return event(eventId, episodeId, sequence, "EPISODE_STATUS_CHANGED", at, "null", "null", "\"" + target + "\"", null);
	}

	private String field(UUID eventId, long sequence, Instant at, String field, String value, Instant base) {
		return event(eventId, episodeId, sequence, "FIELD_UPDATED", at, "null", "\"" + field + "\"", "\"" + value + "\"", base);
	}

	private static String event(UUID eventId, UUID aggregateId, long sequence, String type, Instant at, String patientId,
			String field, String value, Instant base) {
		return """
				{"eventId":"%s","aggregateId":"%s","sequence":%d,"type":"%s","occurredAt":"%s","patientId":%s,"field":%s,"value":%s,"baseUpdatedAt":%s}"""
				.formatted(eventId, aggregateId, sequence, type, at, patientId, field, value, base == null ? "null" : "\"" + base + "\"");
	}
}
