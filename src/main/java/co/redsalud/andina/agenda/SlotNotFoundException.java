package co.redsalud.andina.agenda;

import java.util.UUID;

public class SlotNotFoundException extends RuntimeException {

	public SlotNotFoundException(UUID slotId) {
		super("Slot not found: " + slotId);
	}
}
