package co.redsalud.andina.episodio;

import java.util.UUID;

public class EpisodeNotFoundException extends RuntimeException {

	public EpisodeNotFoundException(UUID id) {
		super("Episode not found: " + id);
	}
}
