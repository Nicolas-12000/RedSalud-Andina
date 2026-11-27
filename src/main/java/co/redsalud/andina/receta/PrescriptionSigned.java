package co.redsalud.andina.receta;

import java.time.Instant;
import java.util.UUID;

/** Outbox message for the pharmacy: it carries the content because the pharmacy needs it to dispense. */
public record PrescriptionSigned(UUID prescriptionId, UUID episodeId, UUID patientId, String content,
		String contentHash, Instant signedAt) {
}
