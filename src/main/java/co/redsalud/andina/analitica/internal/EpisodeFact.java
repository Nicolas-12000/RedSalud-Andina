package co.redsalud.andina.analitica.internal;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(schema = "analitica", name = "episode_fact")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class EpisodeFact {

	@Id
	private UUID episodeId;
	private String centerId;
	private String patientPseudonym;
	private Instant openedAt;

	EpisodeFact(UUID episodeId, String centerId, String patientPseudonym, Instant openedAt) {
		this.episodeId = episodeId;
		this.centerId = centerId;
		this.patientPseudonym = patientPseudonym;
		this.openedAt = openedAt;
	}
}
