package co.redsalud.andina.episodio;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * One event from the offline queue of a center. {@code sequence} orders events of the same aggregate;
 * {@code occurredAt} is the center clock. {@code baseUpdatedAt} is the field timestamp the client saw when it edited.
 */
public record SyncEvent(@NotNull UUID eventId, @NotNull UUID aggregateId, @PositiveOrZero long sequence,
		@NotNull SyncEventType type, @NotNull Instant occurredAt, UUID patientId, String field, String value,
		Instant baseUpdatedAt) {
}
