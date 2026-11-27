package co.redsalud.andina.auditoria.internal;

import java.util.List;
import java.util.UUID;

import org.springframework.data.repository.Repository;

/** Deliberately not a JpaRepository: no update or delete operations are exposed. */
interface AuditRecordRepository extends Repository<AuditRecordEntity, UUID> {

	AuditRecordEntity save(AuditRecordEntity entity);

	List<AuditRecordEntity> findByResourceTypeAndResourceIdOrderByOccurredAtAscIdAsc(String resourceType,
			String resourceId);
}
