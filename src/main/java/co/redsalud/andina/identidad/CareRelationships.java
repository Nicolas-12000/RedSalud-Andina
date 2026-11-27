package co.redsalud.andina.identidad;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.redsalud.andina.identidad.internal.CareRelationship;
import co.redsalud.andina.identidad.internal.CareRelationshipRepository;
import lombok.RequiredArgsConstructor;

/** Public API: lets other modules record that a professional is allowed to treat a patient. */
@Service
@RequiredArgsConstructor
public class CareRelationships {

	private final CareRelationshipRepository repository;

	/** Idempotent: granting an existing relationship does nothing. */
	@Transactional
	public void grant(UUID professionalSubject, UUID patientId) {
		if (!repository.existsByProfessionalSubjectAndPatientId(professionalSubject, patientId)) {
			repository.save(new CareRelationship(professionalSubject, patientId));
		}
	}

	boolean exists(UUID professionalSubject, UUID patientId) {
		return repository.existsByProfessionalSubjectAndPatientId(professionalSubject, patientId);
	}
}
