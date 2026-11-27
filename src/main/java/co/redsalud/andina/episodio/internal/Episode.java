package co.redsalud.andina.episodio.internal;

import java.time.Instant;
import java.util.UUID;

import co.redsalud.andina.episodio.EpisodeStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(schema = "episodio", name = "episode")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Episode {

	@Id
	private UUID id;
	private UUID patientId;
	private String centerId;
	private UUID doctorSubject;
	@Enumerated(EnumType.STRING)
	private EpisodeStatus status;
	private Instant openedAt;
	private Instant admittedAt;
	private Instant receivedAt;

	public Episode(UUID id, UUID patientId, String centerId, UUID doctorSubject, Instant openedAt) {
		this.id = id;
		this.patientId = patientId;
		this.centerId = centerId;
		this.doctorSubject = doctorSubject;
		this.status = EpisodeStatus.WAITING;
		this.openedAt = openedAt;
		this.receivedAt = Instant.now();
	}

	/** Moves one step forward. Admission is the moment the patient's attendance is registered. */
	public void moveTo(EpisodeStatus target, Instant at) {
		this.status = target;
		if (target == EpisodeStatus.ADMITTED) {
			this.admittedAt = at;
		}
	}
}
