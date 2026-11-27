package co.redsalud.andina.auditoria.internal;

import java.time.Instant;
import java.util.UUID;

import co.redsalud.andina.auditoria.AuditKind;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(schema = "auditoria", name = "audit_record")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class AuditRecordEntity {

	@Id
	private UUID id;
	@Enumerated(EnumType.STRING)
	private AuditKind kind;
	private UUID actor;
	private Instant occurredAt;
	private String resourceType;
	private String resourceId;
	private String reason;
	private String fieldName;
	private String keptValue;
	private String discardedValue;

	AuditRecordEntity(UUID id, AuditKind kind, UUID actor, Instant occurredAt, String resourceType, String resourceId,
			String reason, String fieldName, String keptValue, String discardedValue) {
		this.id = id;
		this.kind = kind;
		this.actor = actor;
		this.occurredAt = occurredAt;
		this.resourceType = resourceType;
		this.resourceId = resourceId;
		this.reason = reason;
		this.fieldName = fieldName;
		this.keptValue = keptValue;
		this.discardedValue = discardedValue;
	}
}
