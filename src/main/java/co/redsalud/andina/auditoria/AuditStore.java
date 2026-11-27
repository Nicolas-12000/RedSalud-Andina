package co.redsalud.andina.auditoria;

import java.util.List;

/** Port to the append-only evidence store. Local database in dev; S3 Object Lock (compliance mode) in prod. */
public interface AuditStore {

	void append(AuditRecord record);

	/** Records of the resource, oldest first (ties broken by id) so reports are reproducible. */
	List<AuditRecord> findByResource(String resourceType, String resourceId);
}
