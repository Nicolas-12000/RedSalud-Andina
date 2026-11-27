package co.redsalud.andina.episodio;

import java.util.Arrays;
import java.util.Optional;

/**
 * Teleconsultation lifecycle: WAITING (waiting room) -> ADMITTED (admission, attendance registered)
 * -> IN_PROGRESS -> COMPLETED. Only forward, one step at a time.
 */
public enum EpisodeStatus {
	WAITING, ADMITTED, IN_PROGRESS, COMPLETED;

	public boolean canMoveTo(EpisodeStatus target) {
		return target.ordinal() == ordinal() + 1;
	}

	static Optional<EpisodeStatus> parse(String name) {
		return Arrays.stream(values()).filter(s -> s.name().equalsIgnoreCase(name)).findFirst();
	}
}
