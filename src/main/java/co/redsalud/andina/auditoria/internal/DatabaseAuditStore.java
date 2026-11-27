package co.redsalud.andina.auditoria.internal;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import co.redsalud.andina.auditoria.AuditRecord;
import co.redsalud.andina.auditoria.AuditStore;
import lombok.RequiredArgsConstructor;

/** Local append-only store for dev and test. The S3 Object Lock implementation replaces it in prod. */
@Component
@Profile({ "dev", "test" })
@RequiredArgsConstructor
class DatabaseAuditStore implements AuditStore {

	private final AuditRecordRepository repository;

	@Override
	public void append(AuditRecord r) {
		repository.save(new AuditRecordEntity(r.id(), r.kind(), r.actor(), r.occurredAt(), r.resourceType(),
				r.resourceId(), r.reason(), r.fieldName(), r.keptValue(), r.discardedValue()));
	}

	@Override
	public List<AuditRecord> findByResource(String resourceType, String resourceId) {
		return repository.findByResourceTypeAndResourceIdOrderByOccurredAtAscIdAsc(resourceType, resourceId).stream()
				.map(e -> new AuditRecord(e.getId(), e.getKind(), e.getActor(), e.getOccurredAt(), e.getResourceType(),
						e.getResourceId(), e.getReason(), e.getFieldName(), e.getKeptValue(), e.getDiscardedValue()))
				.toList();
	}
}
