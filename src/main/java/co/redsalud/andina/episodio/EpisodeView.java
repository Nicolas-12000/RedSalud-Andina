package co.redsalud.andina.episodio;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record EpisodeView(UUID id, UUID patientId, String centerId, UUID doctorSubject, EpisodeStatus status, Instant openedAt, Instant admittedAt,
		Map<String, FieldView> fields, java.util.List<AttachmentView> attachments) {

	public record FieldView(String value, Instant updatedAt) {
	}
}
