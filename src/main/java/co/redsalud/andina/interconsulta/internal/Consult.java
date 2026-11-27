package co.redsalud.andina.interconsulta.internal;

import java.time.Instant;
import java.util.UUID;

import co.redsalud.andina.interconsulta.ConsultConflictException;
import co.redsalud.andina.interconsulta.ConsultStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** The state machine lives here, so no code path can skip a state. {@code @Version} stops two concurrent transitions. */
@Entity
@Table(schema = "interconsulta", name = "consult")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Consult {

	@Id
	private UUID id;
	private UUID episodeId;
	private UUID patientId;
	private UUID requestedBy;
	private String specialty;
	private String centerId;
	@Enumerated(EnumType.STRING)
	private ConsultStatus status;
	private UUID bookingId;
	private UUID specialistId;
	private Instant createdAt;
	private Instant updatedAt;
	@Version
	private long version;

	public Consult(UUID id, UUID episodeId, UUID patientId, UUID requestedBy, String specialty, String centerId) {
		this.id = id;
		this.episodeId = episodeId;
		this.patientId = patientId;
		this.requestedBy = requestedBy;
		this.specialty = specialty;
		this.centerId = centerId;
		this.status = ConsultStatus.REQUESTED;
		this.createdAt = Instant.now();
		this.updatedAt = createdAt;
	}

	public boolean sameRequestAs(UUID episodeId, UUID patientId, String specialty) {
		return this.episodeId.equals(episodeId) && this.patientId.equals(patientId) && this.specialty.equals(specialty);
	}

	public void schedule(UUID bookingId, UUID specialistId) {
		moveTo(ConsultStatus.SCHEDULED);
		this.bookingId = bookingId;
		this.specialistId = specialistId;
	}

	public void attend() {
		moveTo(ConsultStatus.ATTENDED);
	}

	public void close() {
		moveTo(ConsultStatus.CLOSED);
	}

	private void moveTo(ConsultStatus target) {
		if (!status.canMoveTo(target)) {
			throw new ConsultConflictException("Cannot move interconsulta " + id + " from " + status + " to " + target);
		}
		this.status = target;
		this.updatedAt = Instant.now();
	}
}
