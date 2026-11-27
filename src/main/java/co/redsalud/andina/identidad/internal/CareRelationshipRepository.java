package co.redsalud.andina.identidad.internal;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CareRelationshipRepository extends JpaRepository<CareRelationship, UUID> {

	boolean existsByProfessionalSubjectAndPatientId(UUID professionalSubject, UUID patientId);
}
