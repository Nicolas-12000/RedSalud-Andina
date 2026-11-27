package co.redsalud.andina.interconsulta;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.redsalud.andina.identidad.AccessPolicy;
import co.redsalud.andina.identidad.CareRelationships;
import co.redsalud.andina.identidad.CurrentUser;
import co.redsalud.andina.interconsulta.internal.Alert;
import co.redsalud.andina.interconsulta.internal.AlertRepository;
import co.redsalud.andina.interconsulta.internal.Consult;
import co.redsalud.andina.interconsulta.internal.ConsultRepository;
import lombok.RequiredArgsConstructor;

/** Every change stores its alerts and publishes {@link ConsultStatusChanged} in the same transaction. */
@Service
@RequiredArgsConstructor
public class ConsultService {

	static final int MAX_ALERTS = 50;
	static final int MAX_PENDING = 100;

	private final ConsultRepository consultRepository;
	private final AlertRepository alertRepository;
	private final AccessPolicy accessPolicy;
	private final CareRelationships careRelationships;
	private final ApplicationEventPublisher events;

	/** Idempotent on the client-generated id. */
	@PreAuthorize("hasRole('RURAL_DOCTOR')")
	@Transactional
	public ConsultView request(UUID id, UUID episodeId, UUID patientId, String specialty) {
		var existing = consultRepository.findById(id);
		if (existing.isPresent()) {
			if (existing.get().sameRequestAs(episodeId, patientId, specialty)) {
				return ConsultView.of(existing.get());
			}
			throw new ConsultConflictException("Interconsulta id " + id + " was already used for a different request");
		}
		accessPolicy.requirePatientAccess(patientId);
		CurrentUser user = CurrentUser.fromContext();
		Consult consult = consultRepository.save(new Consult(id, episodeId, patientId, user.subject(), specialty, user.centerId()));
		alertRepository.save(Alert.forSpecialty(id, specialty, "REQUESTED", "New interconsulta request for " + specialty));
		publish(consult, null);
		return ConsultView.of(consult);
	}

	/** Called together with the booking by the scheduling orchestrator. Replaying the same booking changes nothing. */
	@PreAuthorize("hasRole('RURAL_DOCTOR')")
	@Transactional
	public ConsultView markScheduled(UUID id, UUID bookingId, UUID specialistId) {
		Consult consult = find(id);
		if (consult.getStatus() == ConsultStatus.SCHEDULED && bookingId.equals(consult.getBookingId())) {
			return ConsultView.of(consult);
		}
		ConsultStatus from = consult.getStatus();
		consult.schedule(bookingId, specialistId);
		careRelationships.grant(specialistId, consult.getPatientId());
		alertRepository.save(Alert.forPerson(id, specialistId, "SCHEDULED", "Interconsulta scheduled with you"));
		alertRepository.save(Alert.forPerson(id, consult.getRequestedBy(), "SCHEDULED", "Your interconsulta was scheduled"));
		publish(consult, from);
		return ConsultView.of(consult);
	}

	@PreAuthorize("hasRole('SPECIALIST')")
	@Transactional
	public ConsultView attend(UUID id) {
		Consult consult = findAssignedTo(id);
		ConsultStatus from = consult.getStatus();
		consult.attend();
		alertRepository.save(Alert.forPerson(id, consult.getRequestedBy(), "ATTENDED", "Your interconsulta was attended"));
		publish(consult, from);
		return ConsultView.of(consult);
	}

	@PreAuthorize("hasRole('SPECIALIST')")
	@Transactional
	public ConsultView close(UUID id) {
		Consult consult = findAssignedTo(id);
		ConsultStatus from = consult.getStatus();
		consult.close();
		alertRepository.save(Alert.forPerson(id, consult.getRequestedBy(), "CLOSED", "Your interconsulta was closed"));
		publish(consult, from);
		return ConsultView.of(consult);
	}

	@PreAuthorize("hasAnyRole('RURAL_DOCTOR','SPECIALIST','PATIENT')")
	@Transactional(readOnly = true)
	public ConsultView get(UUID id) {
		Consult consult = find(id);
		accessPolicy.requirePatientAccess(consult.getPatientId());
		return ConsultView.of(consult);
	}

	@PreAuthorize("hasAnyRole('RURAL_DOCTOR','SPECIALIST')")
	@Transactional(readOnly = true)
	public List<AlertView> myAlerts() {
		CurrentUser user = CurrentUser.fromContext();
		return alertRepository.findFor(user.subject(), user.specialty(), PageRequest.of(0, MAX_ALERTS)).stream()
				.map(AlertView::of).toList();
	}

	/** Follow-up list for the network manager: interconsultas not closed and without movement for {@code olderThan}. */
	@PreAuthorize("hasRole('NETWORK_MANAGER')")
	@Transactional(readOnly = true)
	public List<ConsultView> pending(Duration olderThan) {
		return consultRepository.findOpenNotUpdatedSince(Instant.now().minus(olderThan), PageRequest.of(0, MAX_PENDING))
				.stream().map(ConsultView::of).toList();
	}

	private Consult find(UUID id) {
		return consultRepository.findById(id).orElseThrow(() -> new ConsultNotFoundException(id));
	}

	private Consult findAssignedTo(UUID id) {
		Consult consult = find(id);
		if (!CurrentUser.fromContext().subject().equals(consult.getSpecialistId())) {
			throw new AccessDeniedException("Only the assigned specialist can do this");
		}
		return consult;
	}

	private void publish(Consult c, ConsultStatus from) {
		events.publishEvent(new ConsultStatusChanged(c.getId(), c.getPatientId(), c.getSpecialty(), c.getCenterId(), from,
				c.getStatus(), c.getSpecialistId(), c.getUpdatedAt()));
	}
}
