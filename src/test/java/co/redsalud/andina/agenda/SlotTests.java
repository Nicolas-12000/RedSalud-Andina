package co.redsalud.andina.agenda;

import static co.redsalud.andina.TestTokens.as;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import co.redsalud.andina.identidad.Role;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class SlotTests {

	@Autowired MockMvc mockMvc;

	String specialty = "spec-" + UUID.randomUUID();
	UUID specialist = UUID.randomUUID();
	Instant start = Instant.now().plusSeconds(7200);

	@Test
	void specialistPublishesSlotsAndOnlyFreeOnesAreListed() throws Exception {
		String freeSlot = createSlot(start).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		String takenSlot = createSlot(start.plusSeconds(3600)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		String takenId = takenSlot.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
		mockMvc.perform(put("/api/agenda/bookings/{id}", UUID.randomUUID()).with(as(UUID.randomUUID(), Role.RURAL_DOCTOR))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"slotId\":\"%s\",\"patientId\":\"%s\"}".formatted(takenId, UUID.randomUUID()))).andExpect(status().isOk());

		mockMvc.perform(get("/api/agenda/slots").param("specialty", specialty).with(as(UUID.randomUUID(), Role.RURAL_DOCTOR)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(freeSlot.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1")))
				.andExpect(jsonPath("$[0].specialistId").value(specialist.toString()));
	}

	@Test
	void sameStartForTheSameSpecialistIsAConflict() throws Exception {
		createSlot(start).andExpect(status().isOk());
		createSlot(start).andExpect(status().isConflict());
	}

	@Test
	void endBeforeStartIsABadRequest() throws Exception {
		mockMvc.perform(post("/api/agenda/slots").with(as(specialist, Role.SPECIALIST, "c1", specialty))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"startAt\":\"%s\",\"endAt\":\"%s\"}".formatted(start, start.minusSeconds(60))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void onlySpecialistsPublishSlots() throws Exception {
		mockMvc.perform(post("/api/agenda/slots").with(as(UUID.randomUUID(), Role.RURAL_DOCTOR))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"startAt\":\"%s\",\"endAt\":\"%s\"}".formatted(start, start.plusSeconds(1800))))
				.andExpect(status().isForbidden());
	}

	private ResultActions createSlot(Instant startAt) throws Exception {
		return mockMvc.perform(post("/api/agenda/slots").with(as(specialist, Role.SPECIALIST, "c1", specialty))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"startAt\":\"%s\",\"endAt\":\"%s\"}".formatted(startAt, startAt.plusSeconds(1800))));
	}
}
