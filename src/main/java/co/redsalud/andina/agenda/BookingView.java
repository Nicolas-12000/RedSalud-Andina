package co.redsalud.andina.agenda;

import java.time.Instant;
import java.util.UUID;

import co.redsalud.andina.agenda.internal.Booking;
import co.redsalud.andina.agenda.internal.Slot;

public record BookingView(UUID id, UUID slotId, UUID patientId, UUID specialistId, String specialty, Instant bookedAt) {

	static BookingView of(Booking booking, Slot slot) {
		return new BookingView(booking.getId(), booking.getSlotId(), booking.getPatientId(), slot.getSpecialistId(),
				slot.getSpecialty(), booking.getBookedAt());
	}
}
