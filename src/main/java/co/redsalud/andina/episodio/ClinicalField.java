package co.redsalud.andina.episodio;

import java.util.Arrays;
import java.util.Optional;

/** Independent clinical fields. Each one is resolved on its own (last-writer-wins). */
public enum ClinicalField {
	REASON, NOTES, DIAGNOSIS, EVOLUTION_NOTE, VITAL_SIGNS;

	static Optional<ClinicalField> parse(String name) {
		return Arrays.stream(values()).filter(f -> f.name().equalsIgnoreCase(name)).findFirst();
	}
}
