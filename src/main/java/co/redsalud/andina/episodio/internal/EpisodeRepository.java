package co.redsalud.andina.episodio.internal;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EpisodeRepository extends JpaRepository<Episode, UUID> {
}
