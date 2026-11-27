package co.redsalud.andina.legado;

import java.util.UUID;

public class PatientNotInLegacyException extends RuntimeException {

	public PatientNotInLegacyException(UUID patientId) {
		super("Patient " + patientId + " is unknown to the legacy system");
	}
}
