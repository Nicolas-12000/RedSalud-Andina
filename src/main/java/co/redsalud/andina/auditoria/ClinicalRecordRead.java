package co.redsalud.andina.auditoria;

import java.time.Instant;
import java.util.UUID;

/** Published for every clinical read, after it was stored in the audit store. */
public record ClinicalRecordRead(UUID actor, Instant occurredAt, String resourceType, String resourceId, String reason) {
}
