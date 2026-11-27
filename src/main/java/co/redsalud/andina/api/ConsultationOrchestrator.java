package co.redsalud.andina.api;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.redsalud.andina.agenda.BookingService;
import co.redsalud.andina.agenda.BookingView;
import co.redsalud.andina.identidad.CurrentUser;
import co.redsalud.andina.interconsulta.ConsultConflictException;
import co.redsalud.andina.interconsulta.ConsultService;
import co.redsalud.andina.interconsulta.ConsultView;
import lombok.RequiredArgsConstructor;

/**
 * The only use case that needs two modules at once: booking a slot (agenda) and moving the interconsulta
 * to SCHEDULED (interconsulta). One transaction, so a failure in either leaves neither changed.
 */
@Service
@RequiredArgsConstructor
class ConsultationOrchestrator {

	private final ConsultService consultService;
	private final BookingService bookingService;

	@Transactional
	ConsultView schedule(UUID consultId, UUID bookingId, UUID slotId) {
		ConsultView consult = consultService.get(consultId);
		BookingView booking = bookingService.book(bookingId, slotId, consult.patientId(), CurrentUser.fromContext().subject());
		if (!booking.specialty().equals(consult.specialty())) {
			throw new ConsultConflictException("The slot belongs to " + booking.specialty() + ", not " + consult.specialty());
		}
		return consultService.markScheduled(consultId, booking.id(), booking.specialistId());
	}
}
