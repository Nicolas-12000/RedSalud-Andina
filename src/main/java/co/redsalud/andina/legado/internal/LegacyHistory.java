package co.redsalud.andina.legado.internal;

import java.util.List;

/**
 * The legacy model as the legacy system sends it: its own patient code, text dates (dd/MM/yyyy and
 * yyyy-MM-dd HH:mm:ss, legacy local time) and personal data we do not want. Never leaves this package.
 */
public record LegacyHistory(String patientCode, String fullName, String address, List<Encounter> encounters,
		List<Document> documents, String extractedAt) {

	public record Encounter(String encounterId, String visitDate, String notes) {
	}

	public record Document(String documentId, String title, String kind) {
	}
}
