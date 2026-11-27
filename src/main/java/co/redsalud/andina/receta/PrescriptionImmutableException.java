package co.redsalud.andina.receta;

import java.util.UUID;

public class PrescriptionImmutableException extends RuntimeException {

	public PrescriptionImmutableException(UUID id) {
		super("Prescription " + id + " is signed and cannot be changed");
	}
}
