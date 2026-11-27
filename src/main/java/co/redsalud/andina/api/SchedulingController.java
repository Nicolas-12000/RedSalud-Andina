package co.redsalud.andina.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.redsalud.andina.agenda.BookingService;
import co.redsalud.andina.agenda.BookingView;
import co.redsalud.andina.agenda.SlotService;
import co.redsalud.andina.agenda.SlotView;
import co.redsalud.andina.identidad.CurrentUser;
import co.redsalud.andina.interconsulta.AlertView;
import co.redsalud.andina.interconsulta.ConsultService;
import co.redsalud.andina.interconsulta.ConsultView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

/** Scheduling entry point: availability, bookings and interconsultas. Single-module calls go straight to the services. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
class SchedulingController {

	private final SlotService slotService;
	private final BookingService bookingService;
	private final ConsultService consultService;
	private final ConsultationOrchestrator consultationOrchestrator;

	record CreateSlotRequest(@NotNull Instant startAt, @NotNull Instant endAt) {

		@AssertTrue(message = "endAt must be after startAt")
		boolean isEndAfterStart() {
			return startAt == null || endAt == null || endAt.isAfter(startAt);
		}
	}

	record RequestConsultRequest(@NotNull UUID episodeId, @NotNull UUID patientId, @NotBlank String specialty) {
	}

	record ScheduleConsultRequest(@NotNull UUID bookingId, @NotNull UUID slotId) {
	}

	record BookSlotRequest(@NotNull UUID slotId, @NotNull UUID patientId) {
	}

	@PostMapping("/agenda/slots")
	SlotView createSlot(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateSlotRequest request) {
		return slotService.createSlot(CurrentUser.from(jwt), request.startAt(), request.endAt());
	}

	@GetMapping("/agenda/slots")
	List<SlotView> availableSlots(@RequestParam @NotBlank String specialty,
			@RequestParam(required = false) Instant from, @RequestParam(defaultValue = "50") int size) {
		return slotService.findAvailable(specialty, from != null ? from : Instant.now(), size);
	}

	/** PUT with a client-generated id, so a retry after a network cut gives the same result. */
	@PutMapping("/agenda/bookings/{bookingId}")
	BookingView book(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID bookingId,
			@Valid @RequestBody BookSlotRequest request) {
		return bookingService.book(bookingId, request.slotId(), request.patientId(), UUID.fromString(jwt.getSubject()));
	}

	@PutMapping("/interconsultas/{consultId}")
	ConsultView requestConsult(@PathVariable UUID consultId, @Valid @RequestBody RequestConsultRequest request) {
		return consultService.request(consultId, request.episodeId(), request.patientId(), request.specialty());
	}

	/** Books the slot and moves the interconsulta to SCHEDULED in one step. */
	@PostMapping("/interconsultas/{consultId}/schedule")
	ConsultView scheduleConsult(@PathVariable UUID consultId, @Valid @RequestBody ScheduleConsultRequest request) {
		return consultationOrchestrator.schedule(consultId, request.bookingId(), request.slotId());
	}

	@PostMapping("/interconsultas/{consultId}/attend")
	ConsultView attendConsult(@PathVariable UUID consultId) {
		return consultService.attend(consultId);
	}

	@PostMapping("/interconsultas/{consultId}/close")
	ConsultView closeConsult(@PathVariable UUID consultId) {
		return consultService.close(consultId);
	}

	/** Interconsultas without movement for {@code olderThan} (ISO-8601 duration, default 24 h). */
	@GetMapping("/interconsultas/pending")
	List<ConsultView> pendingConsults(@RequestParam(defaultValue = "PT24H") java.time.Duration olderThan) {
		return consultService.pending(olderThan);
	}

	@GetMapping("/interconsultas/alerts")
	List<AlertView> myAlerts() {
		return consultService.myAlerts();
	}

	@GetMapping("/interconsultas/{consultId}")
	ConsultView consult(@PathVariable UUID consultId) {
		return consultService.get(consultId);
	}
}
