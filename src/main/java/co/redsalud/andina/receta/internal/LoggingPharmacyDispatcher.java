package co.redsalud.andina.receta.internal;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import co.redsalud.andina.receta.PharmacyDispatcher;
import co.redsalud.andina.receta.PrescriptionSigned;
import lombok.extern.slf4j.Slf4j;

@Component
@Profile({ "dev", "test" })
@Slf4j
class LoggingPharmacyDispatcher implements PharmacyDispatcher {

	@Override
	public void send(PrescriptionSigned message) {
		log.info("Pharmacy dispatch (stub) for prescription {}", message.prescriptionId());
	}
}
