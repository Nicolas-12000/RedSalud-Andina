package co.redsalud.andina.auditoria;

import static co.redsalud.andina.TestTokens.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import co.redsalud.andina.identidad.Role;

/**
 * "Who saw this document and why", rebuilt from fixed evidence and compared with the report versioned in the
 * repository. Regenerate it with scripts/generate-audit-report.sh (same as -Dupdate.golden=true).
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AuditReportGoldenTests {

	static final Path VERSIONED_REPORT = Path.of("docs/audit/who-read-report.json");

	@Autowired AuditStore store;
	@Autowired MockMvc mockMvc;

	@Test
	void reportIsReproducibleFromTheSameEvidenceAndMatchesTheVersionedFile() throws Exception {
		String episode = "golden-episode-001";
		store.append(read(1, "11111111-1111-1111-1111-111111111111", "2026-01-10T09:00:00Z", episode, "follow-up consultation"));
		store.append(read(2, "22222222-2222-2222-2222-222222222222", "2026-01-10T15:30:00Z", episode, "second opinion"));
		store.append(new AuditRecord(uuid(3), AuditKind.FIELD_CONFLICT, UUID.fromString("11111111-1111-1111-1111-111111111111"),
				Instant.parse("2026-01-10T09:05:00Z"), "episode", episode, null, "NOTES", "kept", "lost")); // not a read: must not appear

		String report = reportFor(episode);

		assertThat(reportFor(episode)).isEqualTo(report); // twice, same bytes
		if (Boolean.getBoolean("update.golden") || Files.notExists(VERSIONED_REPORT)) {
			Files.createDirectories(VERSIONED_REPORT.getParent());
			Files.writeString(VERSIONED_REPORT, report + System.lineSeparator());
		}
		assertThat(report + System.lineSeparator()).isEqualTo(Files.readString(VERSIONED_REPORT));
	}

	private String reportFor(String episode) throws Exception {
		return mockMvc.perform(get("/api/audit/resources/episode/{id}/reads", episode).with(as(UUID.randomUUID(), Role.AUDITOR)))
				.andReturn().getResponse().getContentAsString();
	}

	private static AuditRecord read(int n, String actor, String at, String episode, String reason) {
		return new AuditRecord(uuid(n), AuditKind.READ, UUID.fromString(actor), Instant.parse(at), "episode", episode, reason, null, null, null);
	}

	private static UUID uuid(int n) {
		return new UUID(0, n);
	}
}
