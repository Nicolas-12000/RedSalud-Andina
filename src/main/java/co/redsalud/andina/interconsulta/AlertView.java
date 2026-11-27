package co.redsalud.andina.interconsulta;

import java.time.Instant;
import java.util.UUID;

import co.redsalud.andina.interconsulta.internal.Alert;

public record AlertView(UUID id, UUID consultId, String kind, String message, Instant createdAt) {

	static AlertView of(Alert a) {
		return new AlertView(a.getId(), a.getConsultId(), a.getKind(), a.getMessage(), a.getCreatedAt());
	}
}
