package co.redsalud.andina.legado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import co.redsalud.andina.legado.internal.LegacyHistory;
import co.redsalud.andina.legado.internal.LegacyTranslator;

class LegacyTranslatorTests {

	LegacyTranslator translator = new LegacyTranslator();
	UUID patient = UUID.randomUUID();
	Instant expiresAt = Instant.parse("2026-03-02T00:00:00Z");

	@Test
	void translatesFormatsToIsoTypesAndDropsPersonalData() {
		var legacy = new LegacyHistory("P-1", "Maria Perez", "Calle 10 #5-20",
				List.of(new LegacyHistory.Encounter("E-1", "05/01/2022", "cough")),
				List.of(new LegacyHistory.Document("D-1", "Lab results", "LAB")), "2026-03-01 08:00:00");

		PatientHistory history = translator.toCanonical(patient, legacy, expiresAt);

		assertThat(history.patientId()).isEqualTo(patient);
		assertThat(history.episodes()).singleElement().satisfies(e -> {
			assertThat(e.date()).isEqualTo(LocalDate.of(2022, 1, 5));
			assertThat(e.legacyEpisodeId()).isEqualTo("E-1");
		});
		assertThat(history.documents()).extracting(PatientHistory.DocumentRef::documentId).containsExactly("D-1");
		assertThat(history.freshAt()).isEqualTo(Instant.parse("2026-03-01T13:00:00Z")); // Bogota is UTC-5
		assertThat(history.expiresAt()).isEqualTo(expiresAt);
		assertThat(history.toString()).doesNotContain("Maria Perez").doesNotContain("Calle 10");
	}

	@Test
	void malformedLegacyDataFailsLoudlyInsteadOfDroppingEpisodes() {
		var legacy = new LegacyHistory("P-1", "n", "a", List.of(new LegacyHistory.Encounter("E-1", "2022-01-05", "x")),
				List.of(), "2026-03-01 08:00:00");

		assertThatThrownBy(() -> translator.toCanonical(patient, legacy, expiresAt))
				.isInstanceOf(LegacyTranslationException.class);
	}
}
