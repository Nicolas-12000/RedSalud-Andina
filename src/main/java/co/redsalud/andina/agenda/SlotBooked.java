package co.redsalud.andina.agenda;

import java.time.Instant;
import java.util.UUID;

/** Published when a slot is booked. Other modules react to this instead of reading agenda tables. */
public record SlotBooked(UUID bookingId, UUID slotId, UUID specialistId, UUID patientId, Instant startAt) {
}
