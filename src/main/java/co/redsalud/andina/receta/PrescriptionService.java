package co.redsalud.andina.receta;

import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.redsalud.andina.auditoria.AuditTrail;
import co.redsalud.andina.identidad.AccessPolicy;
import co.redsalud.andina.identidad.CurrentUser;
import co.redsalud.andina.receta.internal.Prescription;
import co.redsalud.andina.receta.internal.PrescriptionRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PrescriptionService {

	private final PrescriptionRepository repository;
	private final AccessPolicy accessPolicy;
	private final AuditTrail auditTrail;
	private final ApplicationEventPublisher events;

	/** Creates or edits a draft. Idempotent on the client-generated id; a signed prescription only accepts an identical replay. */
	@PreAuthorize("hasRole('SPECIALIST')")
	@Transactional
	public PrescriptionView saveDraft(UUID id, UUID episodeId, UUID patientId, String content) {
		CurrentUser user = CurrentUser.fromContext();
		var existing = repository.findById(id);
		if (existing.isEmpty()) {
			accessPolicy.requirePatientAccess(patientId);
			return PrescriptionView.of(repository.save(new Prescription(id, episodeId, patientId, user.subject(), content)));
		}
		Prescription prescription = existing.get();
		requireOwner(prescription, user);
		if (prescription.isSigned() && prescription.sameRequestAs(episodeId, patientId, content)) {
			return PrescriptionView.of(prescription);
		}
		if (!prescription.getEpisodeId().equals(episodeId) || !prescription.getPatientId().equals(patientId)) {
			throw new PrescriptionImmutableException(id);
		}
		prescription.changeContent(content);
		return PrescriptionView.of(prescription);
	}

	/** Signs once: a repeated call returns the signed prescription and does not publish again. */
	@PreAuthorize("hasRole('SPECIALIST')")
	@Transactional
	public PrescriptionView sign(UUID id) {
		Prescription prescription = repository.findById(id).orElseThrow(() -> new PrescriptionNotFoundException(id));
		requireOwner(prescription, CurrentUser.fromContext());
		if (!prescription.isSigned()) {
			prescription.sign();
			events.publishEvent(new PrescriptionSigned(id, prescription.getEpisodeId(), prescription.getPatientId(),
					prescription.getContent(), prescription.getContentHash(), prescription.getSignedAt()));
		}
		return PrescriptionView.of(prescription);
	}

	@PreAuthorize("hasAnyRole('RURAL_DOCTOR','SPECIALIST','PATIENT')")
	@Transactional
	public PrescriptionView read(UUID id, String reason) {
		Prescription prescription = repository.findById(id).orElseThrow(() -> new PrescriptionNotFoundException(id));
		accessPolicy.requirePatientAccess(prescription.getPatientId());
		auditTrail.recordRead("prescription", id.toString(), reason);
		return PrescriptionView.of(prescription);
	}

	private static void requireOwner(Prescription prescription, CurrentUser user) {
		if (!prescription.getPrescriberSubject().equals(user.subject())) {
			throw new AccessDeniedException("Only the prescriber can change or sign this prescription");
		}
	}
}
