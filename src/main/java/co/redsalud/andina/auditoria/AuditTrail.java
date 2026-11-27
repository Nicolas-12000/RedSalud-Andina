package co.redsalud.andina.auditoria;

import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
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

	/** Records a refused request. Works for callers without a recognized role, so it reads the subject straight from the token. */
	public void recordAccessDenied(String resource, String claimedReason) {
		var principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		if (!(principal instanceof Jwt jwt)) {
			return;
		}
		String reason = claimedReason == null || claimedReason.isBlank() ? null : truncate(claimedReason.strip(), 500);
		store.append(new AuditRecord(UUID.randomUUID(), AuditKind.ACCESS_DENIED, actorOf(jwt.getSubject()),
				Instant.now(), "http-request", truncate(resource, 128), reason, null, null, null));
	}

	/** Cognito subjects are UUIDs; anything else still gets a stable actor id so a denial is never lost or turned into a 500. */
	private static UUID actorOf(String subject) {
		try {
			return UUID.fromString(subject);
		} catch (IllegalArgumentException e) {
			return UUID.nameUUIDFromBytes(subject.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		}
	}

	private static String truncate(String text, int max) {
		return text.length() <= max ? text : text.substring(0, max);
	}

	private static UUID currentActor() {
		return CurrentUser.fromContext().subject();
	}
}
