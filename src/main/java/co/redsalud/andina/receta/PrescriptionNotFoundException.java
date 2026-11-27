package co.redsalud.andina.receta;

import java.util.UUID;

public class PrescriptionNotFoundException extends RuntimeException {

	public PrescriptionNotFoundException(UUID id) {
		super("Prescription not found: " + id);
	}
}
