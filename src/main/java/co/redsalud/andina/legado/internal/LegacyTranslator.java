package co.redsalud.andina.legado.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import co.redsalud.andina.legado.LegacyTranslationException;
import co.redsalud.andina.legado.PatientHistory;

/** The only place that knows the legacy formats. If the legacy vendor changes, only this class changes. */
@Component
public class LegacyTranslator {

	private static final DateTimeFormatter LEGACY_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	private static final DateTimeFormatter LEGACY_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	// Assumption: the legacy system stamps extraction time in Colombian local time.
	private static final ZoneId LEGACY_ZONE = ZoneId.of("America/Bogota");

	public PatientHistory toCanonical(UUID patientId, LegacyHistory legacy, Instant expiresAt) {
		try {
			var episodes = legacy.encounters().stream()
					.map(e -> new PatientHistory.EpisodeSummary(e.encounterId(), LocalDate.parse(e.visitDate(), LEGACY_DATE), e.notes()))
					.toList();
			var documents = legacy.documents().stream()
					.map(d -> new PatientHistory.DocumentRef(d.documentId(), d.title(), d.kind()))
					.toList();
			Instant freshAt = LocalDateTime.parse(legacy.extractedAt(), LEGACY_TIMESTAMP).atZone(LEGACY_ZONE).toInstant();
			return new PatientHistory(patientId, List.copyOf(episodes), List.copyOf(documents), freshAt, expiresAt,
					PatientHistory.Source.LEGACY_DIRECT);
		} catch (DateTimeParseException | NullPointerException e) {
			throw new LegacyTranslationException("Legacy history of patient " + patientId + " has an unexpected format", e);
		}
	}
}
