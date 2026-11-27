package co.redsalud.andina.interconsulta;

import static co.redsalud.andina.TestTokens.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import co.redsalud.andina.agenda.internal.BookingRepository;
import co.redsalud.andina.agenda.internal.Slot;
import co.redsalud.andina.agenda.internal.SlotRepository;
import co.redsalud.andina.identidad.CareRelationships;
import co.redsalud.andina.identidad.Role;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@RecordApplicationEvents
class ConsultTests {

	@Autowired MockMvc mockMvc;
	@Autowired CareRelationships careRelationships;
	@Autowired SlotRepository slotRepository;
	@Autowired BookingRepository bookingRepository;
	@Autowired ApplicationEvents applicationEvents;

	UUID doctor = UUID.randomUUID();
	UUID specialist = UUID.randomUUID();
	UUID patient = UUID.randomUUID();
	UUID consultId = UUID.randomUUID();

	@Test
	void fullLifecycleWithEventsAndAlerts() throws Exception {
		careRelationships.grant(doctor, patient);
		request().andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REQUESTED"));
		// Specialists of the specialty are alerted about the request.
		mockMvc.perform(get("/api/interconsultas/alerts").with(as(UUID.randomUUID(), Role.SPECIALIST, "c1", "cardiology")))
				.andExpect(jsonPath("$[?(@.consultId=='%s')].kind".formatted(consultId)).value("REQUESTED"));

		Slot slot = slot("cardiology");
		schedule(UUID.randomUUID(), slot).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SCHEDULED"))
				.andExpect(jsonPath("$.specialistId").value(slot.getSpecialistId().toString()));
		UUID assigned = slot.getSpecialistId();

		mockMvc.perform(post("/api/interconsultas/{id}/attend", consultId).with(as(assigned, Role.SPECIALIST, "c1", "cardiology")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ATTENDED"));
		mockMvc.perform(post("/api/interconsultas/{id}/close", consultId).with(as(assigned, Role.SPECIALIST, "c1", "cardiology")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));

		// The requester received one alert per step after the request.
		mockMvc.perform(get("/api/interconsultas/alerts").with(as(doctor, Role.RURAL_DOCTOR)))
				.andExpect(jsonPath("$[?(@.consultId=='%s')].kind".formatted(consultId)).value(
						org.hamcrest.Matchers.containsInAnyOrder("SCHEDULED", "ATTENDED", "CLOSED")));
		assertThat(applicationEvents.stream(ConsultStatusChanged.class).filter(e -> e.consultId().equals(consultId)))
				.extracting(ConsultStatusChanged::to)
				.containsExactly(ConsultStatus.REQUESTED, ConsultStatus.SCHEDULED, ConsultStatus.ATTENDED, ConsultStatus.CLOSED);
	}

	@Test
	void requestIsIdempotentAndIdReuseIsRejected() throws Exception {
		careRelationships.grant(doctor, patient);
		request().andExpect(status().isOk());
		request().andExpect(status().isOk());
		mockMvc.perform(put("/api/interconsultas/{id}", consultId).with(as(doctor, Role.RURAL_DOCTOR))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"episodeId\":\"%s\",\"patientId\":\"%s\",\"specialty\":\"dermatology\"}".formatted(UUID.randomUUID(), patient)))
				.andExpect(status().isConflict());
		assertThat(applicationEvents.stream(ConsultStatusChanged.class).filter(e -> e.consultId().equals(consultId))).hasSize(1);
	}

	@Test
	void transitionsCannotBeSkipped() throws Exception {
		careRelationships.grant(doctor, patient);
		request().andExpect(status().isOk());
		Slot slot = slot("cardiology");
		schedule(UUID.randomUUID(), slot).andExpect(status().isOk());

		// SCHEDULED -> CLOSED skips ATTENDED.
		mockMvc.perform(post("/api/interconsultas/{id}/close", consultId).with(as(slot.getSpecialistId(), Role.SPECIALIST)))
				.andExpect(status().isConflict());
		// Scheduling again with another booking is not a valid transition.
		schedule(UUID.randomUUID(), slot("cardiology")).andExpect(status().isConflict());
	}

	@Test
	void takenSlotLeavesTheInterconsultaRequested() throws Exception {
		careRelationships.grant(doctor, patient);
		request().andExpect(status().isOk());
		Slot slot = slot("cardiology");
		mockMvc.perform(put("/api/agenda/bookings/{id}", UUID.randomUUID()).with(as(doctor, Role.RURAL_DOCTOR))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"slotId\":\"%s\",\"patientId\":\"%s\"}".formatted(slot.getId(), UUID.randomUUID())))
				.andExpect(status().isOk());

		schedule(UUID.randomUUID(), slot).andExpect(status().isConflict());

		mockMvc.perform(get("/api/interconsultas/{id}", consultId).with(as(doctor, Role.RURAL_DOCTOR)))
				.andExpect(jsonPath("$.status").value("REQUESTED"));
	}

	@Test
	void slotOfAnotherSpecialtyRollsBackTheBooking() throws Exception {
		careRelationships.grant(doctor, patient);
		request().andExpect(status().isOk());
		Slot wrongSpecialty = slot("dermatology");

		schedule(UUID.randomUUID(), wrongSpecialty).andExpect(status().isConflict());

		assertThat(bookingRepository.existsBySlotId(wrongSpecialty.getId())).isFalse();
	}

	@Test
	void onlyTheAssignedSpecialistCanAttend() throws Exception {
		careRelationships.grant(doctor, patient);
		request().andExpect(status().isOk());
		schedule(UUID.randomUUID(), slot("cardiology")).andExpect(status().isOk());

		mockMvc.perform(post("/api/interconsultas/{id}/attend", consultId).with(as(UUID.randomUUID(), Role.SPECIALIST)))
				.andExpect(status().isForbidden());
	}

	@Test
	void networkManagerSeesOpenInterconsultasWithoutMovementAndOthersAreDenied() throws Exception {
		careRelationships.grant(doctor, patient);
		request().andExpect(status().isOk());
		UUID manager = UUID.randomUUID();

		mockMvc.perform(get("/api/interconsultas/pending").param("olderThan", "PT0S").with(as(manager, Role.NETWORK_MANAGER)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.id=='%s')].status".formatted(consultId)).value("REQUESTED"));
		mockMvc.perform(get("/api/interconsultas/pending").param("olderThan", "PT24H").with(as(manager, Role.NETWORK_MANAGER)))
				.andExpect(jsonPath("$[?(@.id=='%s')]".formatted(consultId)).isEmpty());
		mockMvc.perform(get("/api/interconsultas/pending").with(as(doctor, Role.RURAL_DOCTOR)))
				.andExpect(status().isForbidden());
	}

	private ResultActions request() throws Exception {
		return mockMvc.perform(put("/api/interconsultas/{id}", consultId).with(as(doctor, Role.RURAL_DOCTOR))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"episodeId\":\"%s\",\"patientId\":\"%s\",\"specialty\":\"cardiology\"}".formatted(UUID.nameUUIDFromBytes(consultId.toString().getBytes()), patient)));
	}

	private ResultActions schedule(UUID bookingId, Slot slot) throws Exception {
		return mockMvc.perform(post("/api/interconsultas/{id}/schedule", consultId).with(as(doctor, Role.RURAL_DOCTOR))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"bookingId\":\"%s\",\"slotId\":\"%s\"}".formatted(bookingId, slot.getId())));
	}

	private Slot slot(String specialty) {
		Instant start = Instant.now().plusSeconds(3600 + (long) (Math.random() * 1_000_000));
		return slotRepository.save(new Slot(UUID.randomUUID(), specialty, start, start.plusSeconds(1800)));
	}
}
