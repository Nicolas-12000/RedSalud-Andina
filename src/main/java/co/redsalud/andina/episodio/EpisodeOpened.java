package co.redsalud.andina.episodio;

import java.time.Instant;
import java.util.UUID;

/** Published when a new episode is stored (once per episode, not on replays). */
public record EpisodeOpened(UUID episodeId, UUID patientId, String centerId, Instant openedAt) {
}
