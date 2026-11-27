package co.redsalud.andina.interconsulta;

import java.util.UUID;

public class ConsultNotFoundException extends RuntimeException {

	public ConsultNotFoundException(UUID id) {
		super("Interconsulta not found: " + id);
	}
}
