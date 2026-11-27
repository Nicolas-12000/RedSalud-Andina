package co.redsalud.andina.auditoria;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Answer to "who saw this document and why". Deterministic: no generation time, entries in stable order,
 * and a hash over the entries so two runs over the same evidence can be compared.
 */
public record ReadReport(String reportVersion, String resourceType, String resourceId, List<Entry> reads,
		String contentHash) {

	public static final String VERSION = "1";

	public record Entry(UUID actor, Instant occurredAt, String reason) {
	}
}
