package co.redsalud.andina.episodio.internal;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(schema = "episodio", name = "processed_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedEvent {

	@Id
	private UUID eventId;
	private String message;
	private boolean conflict;
	private Instant processedAt;

	public ProcessedEvent(UUID eventId, String message, boolean conflict) {
		this.eventId = eventId;
		this.message = message;
		this.conflict = conflict;
		this.processedAt = Instant.now();
	}
}
