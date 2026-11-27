package co.redsalud.andina.analitica.internal;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import co.redsalud.andina.interconsulta.ConsultStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Timestamps per status, set from events. Applying the same event twice gives the same row, in any arrival order. */
@Entity
@Table(schema = "analitica", name = "consult_fact")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ConsultFact {

	@Id
	private UUID consultId;
	private String centerId;
	private String specialty;
	private Instant requestedAt;
	private Instant attendedAt;
	private Instant closedAt;
	private Long waitSeconds;
	@Version
	private long version;

	ConsultFact(UUID consultId, String centerId, String specialty) {
		this.consultId = consultId;
		this.centerId = centerId;
		this.specialty = specialty;
	}

	void apply(ConsultStatus status, Instant at) {
		switch (status) {
			case REQUESTED -> requestedAt = at;
			case ATTENDED -> attendedAt = at;
			case CLOSED -> closedAt = at;
			case SCHEDULED -> { }
		}
		if (requestedAt != null && attendedAt != null) {
			waitSeconds = Duration.between(requestedAt, attendedAt).toSeconds();
		}
	}
}
