package co.redsalud.andina.auditoria;

import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import co.redsalud.andina.identidad.CurrentUser;
import lombok.RequiredArgsConstructor;

/**
 * Single entry point for audit facts. The actor always comes from the security context, never from the caller,
 * and the audit record is stored before the caller returns data.
 */
@Service
@RequiredArgsConstructor
public class AuditTrail {

	private final AuditStore store;
	private final ApplicationEventPublisher events;

	/** Call before returning clinical data. Throws {@link ReasonRequiredException} when the reason is blank. */
	public void recordRead(String resourceType, String resourceId, String reason) {
		if (reason == null || reason.isBlank()) {
			throw new ReasonRequiredException();
		}
		UUID actor = currentActor();
		Instant now = Instant.now();
		store.append(new AuditRecord(UUID.randomUUID(), AuditKind.READ, actor, now, resourceType, resourceId,
				reason.strip(), null, null, null));
		events.publishEvent(new ClinicalRecordRead(actor, now, resourceType, resourceId, reason.strip()));
	}

	/** Keeps the value that lost a last-writer-wins resolution: nothing is discarded silently. */
	public void recordFieldConflict(String resourceType, String resourceId, String fieldName, String keptValue,
			String discardedValue) {
		store.append(new AuditRecord(UUID.randomUUID(), AuditKind.FIELD_CONFLICT, currentActor(), Instant.now(),
				resourceType, resourceId, null, fieldName, keptValue, discardedValue));
	}

	private static UUID currentActor() {
		return CurrentUser.fromContext().subject();
	}
}
