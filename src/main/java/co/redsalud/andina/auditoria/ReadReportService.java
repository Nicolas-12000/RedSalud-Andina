package co.redsalud.andina.auditoria;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReadReportService {

	private final AuditStore store;

	@PreAuthorize("hasRole('AUDITOR')")
	public ReadReport whoRead(String resourceType, String resourceId) {
		List<ReadReport.Entry> reads = store.findByResource(resourceType, resourceId).stream()
				.filter(r -> r.kind() == AuditKind.READ)
				.map(r -> new ReadReport.Entry(r.actor(), r.occurredAt(), r.reason()))
				.toList();
		return new ReadReport(ReadReport.VERSION, resourceType, resourceId, reads, hash(reads));
	}

	static String hash(List<ReadReport.Entry> reads) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			for (ReadReport.Entry e : reads) {
				digest.update((e.actor() + "|" + e.occurredAt() + "|" + e.reason() + "\n").getBytes(StandardCharsets.UTF_8));
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
