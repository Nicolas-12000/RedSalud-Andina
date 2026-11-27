package co.redsalud.andina.auditoria;

import java.time.Instant;
import java.util.UUID;

/** One immutable audit fact. Conflict fields are null for reads. */
public record AuditRecord(UUID id, AuditKind kind, UUID actor, Instant occurredAt, String resourceType,
		String resourceId, String reason, String fieldName, String keptValue, String discardedValue) {
}
