package co.redsalud.andina.agenda;

import java.time.Instant;
import java.util.UUID;

import co.redsalud.andina.agenda.internal.Slot;

public record SlotView(UUID id, UUID specialistId, String specialty, Instant startAt, Instant endAt) {

	static SlotView of(Slot slot) {
		return new SlotView(slot.getId(), slot.getSpecialistId(), slot.getSpecialty(), slot.getStartAt(), slot.getEndAt());
	}
}
