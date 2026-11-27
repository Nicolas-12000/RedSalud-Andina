package co.redsalud.andina.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import co.redsalud.andina.agenda.internal.BookingRepository;
import co.redsalud.andina.agenda.internal.Slot;
import co.redsalud.andina.agenda.internal.SlotRepository;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class BookingTests {

	@Autowired MockMvc mockMvc;
	@Autowired SlotRepository slotRepository;
	@Autowired BookingRepository bookingRepository;
	@Autowired BookingService bookingService;

	@Test
	void secondBookingOfTheSameSlotReturns409() throws Exception {
		Slot slot = newSlot();
		UUID patient = UUID.randomUUID();

		mockMvc.perform(book(UUID.randomUUID(), slot.getId(), patient)).andExpect(status().isOk());
		mockMvc.perform(book(UUID.randomUUID(), slot.getId(), patient)).andExpect(status().isConflict());
	}

	@Test
	void retryWithSameBookingIdReturnsSameResultWithoutDuplicating() throws Exception {
		Slot slot = newSlot();
		UUID bookingId = UUID.randomUUID();
		UUID patient = UUID.randomUUID();

		mockMvc.perform(book(bookingId, slot.getId(), patient)).andExpect(status().isOk());
		mockMvc.perform(book(bookingId, slot.getId(), patient))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(bookingId.toString()));

		assertThat(bookingRepository.existsBySlotId(slot.getId())).isTrue();
		assertThat(bookingRepository.findAll().stream().filter(b -> b.getSlotId().equals(slot.getId()))).hasSize(1);
	}

	@Test
	void reusingBookingIdForAnotherSlotReturns409() throws Exception {
		UUID bookingId = UUID.randomUUID();
		UUID patient = UUID.randomUUID();
		mockMvc.perform(book(bookingId, newSlot().getId(), patient)).andExpect(status().isOk());
		mockMvc.perform(book(bookingId, newSlot().getId(), patient)).andExpect(status().isConflict());
	}

	@Test
	void unknownSlotReturns404() throws Exception {
		mockMvc.perform(book(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())).andExpect(status().isNotFound());
	}

	@Test
	void patientCannotBookForAnotherPatient() throws Exception {
		UUID patient = UUID.randomUUID();
		mockMvc.perform(put("/api/agenda/bookings/{id}", UUID.randomUUID())
						.with(token(patient, "PATIENT", "patient"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"slotId\":\"%s\",\"patientId\":\"%s\"}".formatted(newSlot().getId(), UUID.randomUUID())))
				.andExpect(status().isForbidden());
	}

	@Test
	void concurrentAttemptsOnOneSlotProduceExactlyOneBooking() throws Exception {
		Slot slot = newSlot();
		int attempts = 8;
		ExecutorService pool = Executors.newFixedThreadPool(attempts);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Boolean>> results = new ArrayList<>();
		for (int i = 0; i < attempts; i++) {
			results.add(pool.submit((Callable<Boolean>) () -> {
				UUID doctor = UUID.randomUUID();
				Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(doctor.toString()).build();
				SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
						List.of(new SimpleGrantedAuthority("ROLE_RURAL_DOCTOR")), doctor.toString()));
				start.await();
				try {
					bookingService.book(UUID.randomUUID(), slot.getId(), UUID.randomUUID(), doctor);
					return true;
				} catch (AgendaConflictException e) {
					return false;
				} finally {
					SecurityContextHolder.clearContext();
				}
			}));
		}
		start.countDown();
		long winners = 0;
		for (Future<Boolean> result : results) {
			winners += result.get() ? 1 : 0;
		}
		pool.shutdown();

		assertThat(winners).isEqualTo(1);
		assertThat(bookingRepository.findAll().stream().filter(b -> b.getSlotId().equals(slot.getId()))).hasSize(1);
	}

	private Slot newSlot() {
		Instant start = Instant.now().plusSeconds(3600 + (long) (Math.random() * 1_000_000));
		return slotRepository.save(new Slot(UUID.randomUUID(), "cardiology", start, start.plusSeconds(1800)));
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder book(UUID bookingId, UUID slotId, UUID patientId) {
		return put("/api/agenda/bookings/{id}", bookingId)
				.with(token(UUID.randomUUID(), "RURAL_DOCTOR", "rural-doctor"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"slotId\":\"%s\",\"patientId\":\"%s\"}".formatted(slotId, patientId));
	}

	private RequestPostProcessor token(UUID subject, String role, String group) {
		return jwt().jwt(j -> j.subject(subject.toString()).claim("cognito:groups", List.of(group)))
				.authorities(new SimpleGrantedAuthority("ROLE_" + role));
	}
}
