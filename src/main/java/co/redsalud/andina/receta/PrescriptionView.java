package co.redsalud.andina.receta;

import java.time.Instant;
import java.util.UUID;

import co.redsalud.andina.receta.internal.Prescription;

public record PrescriptionView(UUID id, UUID episodeId, UUID patientId, UUID prescriberSubject, String content,
		String status, String contentHash, Instant signedAt) {

	static PrescriptionView of(Prescription p) {
		return new PrescriptionView(p.getId(), p.getEpisodeId(), p.getPatientId(), p.getPrescriberSubject(),
				p.getContent(), p.getStatus().name(), p.getContentHash(), p.getSignedAt());
	}
}
