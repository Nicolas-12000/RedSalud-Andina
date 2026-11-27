package co.redsalud.andina.analitica.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class PseudonymizerTests {

	UUID patient = UUID.randomUUID();

	@Test
	void isStableForTheSamePatientAndKey() {
		assertThat(new Pseudonymizer("key-1").of(patient)).isEqualTo(new Pseudonymizer("key-1").of(patient));
	}

	@Test
	void changesWithTheKeyAndHidesTheIdentifier() {
		String first = new Pseudonymizer("key-1").of(patient);

		assertThat(first).isNotEqualTo(new Pseudonymizer("key-2").of(patient))
				.doesNotContain(patient.toString())
				.hasSize(64);
	}
}
