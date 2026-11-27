package co.redsalud.andina.episodio.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EpisodeFieldRepository extends JpaRepository<EpisodeField, UUID> {

	Optional<EpisodeField> findByEpisodeIdAndFieldName(UUID episodeId, String fieldName);

	List<EpisodeField> findByEpisodeId(UUID episodeId);
}
