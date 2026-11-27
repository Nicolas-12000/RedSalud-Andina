package co.redsalud.andina.receta;

import static co.redsalud.andina.TestTokens.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import co.redsalud.andina.auditoria.AuditStore;
import co.redsalud.andina.identidad.CareRelationships;
import co.redsalud.andina.identidad.Role;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class PrescriptionTests {

	@Autowired MockMvc mockMvc;
	@Autowired CareRelationships careRelationships;
	@Autowired AuditStore auditStore;
	@Autowired IncompleteEventPublications incompletePublications;
	@MockitoBean PharmacyDispatcher pharmacy;

	UUID specialist = UUID.randomUUID();
	UUID patient = UUID.randomUUID();
	UUID episode = UUID.randomUUID();
	UUID prescriptionId = UUID.randomUUID();

	@Test
	void signingPublishesOnceToThePharmacyOutbox() throws Exception {
		careRelationships.grant(specialist, patient);
		save("amoxicillin 500mg").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT"));

		sign().andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SIGNED"))
				.andExpect(jsonPath("$.contentHash").isNotEmpty());
		sign().andExpect(status().isOk());

		verify(pharmacy, timeout(5000).times(1)).send(argThat(this::isThisPrescription));
	}

	@Test
	void pharmacyDownLosesNothingAndTheMessageIsDeliveredWhenItComesBack() throws Exception {
		careRelationships.grant(specialist, patient);
		doThrow(new IllegalStateException("queue is down")).doNothing().when(pharmacy).send(argThat(this::isThisPrescription));
		save("amoxicillin 500mg").andExpect(status().isOk());

		sign().andExpect(status().isOk()); // the prescription is signed even though the pharmacy is unreachable
		verify(pharmacy, timeout(5000).times(1)).send(argThat(this::isThisPrescription)); // first attempt failed
		incompletePublications.resubmitIncompletePublications(
				p -> p.getEvent() instanceof PrescriptionSigned signed && signed.prescriptionId().equals(prescriptionId));

		verify(pharmacy, timeout(5000).times(2)).send(argThat(this::isThisPrescription));
	}

	@Test
	void aSignedPrescriptionCannotBeChanged() throws Exception {
		careRelationships.grant(specialist, patient);
		save("ibuprofen 400mg").andExpect(status().isOk());
		sign().andExpect(status().isOk());

		save("something else").andExpect(status().isConflict());
		save("ibuprofen 400mg").andExpect(status().isOk()); // identical replay is idempotent
	}

	@Test
	void aDraftCanBeEditedAndNothingIsSentUntilSigned() throws Exception {
		careRelationships.grant(specialist, patient);
		save("v1").andExpect(status().isOk());
		save("v2").andExpect(status().isOk()).andExpect(jsonPath("$.content").value("v2"));

		verify(pharmacy, never()).send(argThat(this::isThisPrescription));
	}

	@Test
	void onlySpecialistsPrescribeAndOnlyWithAccessToThePatient() throws Exception {
		mockMvc.perform(put("/api/prescriptions/{id}", prescriptionId).with(as(UUID.randomUUID(), Role.RURAL_DOCTOR))
				.contentType(MediaType.APPLICATION_JSON).content(body("x"))).andExpect(status().isForbidden());
		save("x").andExpect(status().isForbidden()); // specialist without relationship to the patient
	}

	@Test
	void readingRequiresReasonAndIsAudited() throws Exception {
		careRelationships.grant(specialist, patient);
		save("paracetamol").andExpect(status().isOk());

		mockMvc.perform(get("/api/prescriptions/{id}", prescriptionId).with(as(specialist, Role.SPECIALIST)))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/prescriptions/{id}", prescriptionId).with(as(specialist, Role.SPECIALIST))
				.header("X-Access-Reason", "dispensing check")).andExpect(status().isOk());

		assertThat(auditStore.findByResource("prescription", prescriptionId.toString())).hasSize(1);
	}

	// Async dispatches of other tests may still arrive on the shared mock, so only count this test's prescription.
	private boolean isThisPrescription(PrescriptionSigned message) {
		return message.prescriptionId().equals(prescriptionId);
	}

	private ResultActions save(String content) throws Exception {
		return mockMvc.perform(put("/api/prescriptions/{id}", prescriptionId).with(as(specialist, Role.SPECIALIST))
				.contentType(MediaType.APPLICATION_JSON).content(body(content)));
	}

	private ResultActions sign() throws Exception {
		return mockMvc.perform(post("/api/prescriptions/{id}/sign", prescriptionId).with(as(specialist, Role.SPECIALIST)));
	}

	private String body(String content) {
		return "{\"episodeId\":\"%s\",\"patientId\":\"%s\",\"content\":\"%s\"}".formatted(episode, patient, content);
	}
}
