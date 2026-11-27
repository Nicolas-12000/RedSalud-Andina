package co.redsalud.andina.receta.internal;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import co.redsalud.andina.receta.PharmacyDispatcher;
import co.redsalud.andina.receta.PrescriptionSigned;
import lombok.RequiredArgsConstructor;

/**
 * Outbox consumer. The event was stored with the signing transaction (Modulith event publication registry);
 * if dispatching fails the publication stays incomplete and is resubmitted, so the pharmacy gets it eventually.
 */
@Component
@RequiredArgsConstructor
class PharmacyOutbox {

	private final PharmacyDispatcher dispatcher;

	@ApplicationModuleListener
	void on(PrescriptionSigned event) {
		dispatcher.send(event);
	}
}
