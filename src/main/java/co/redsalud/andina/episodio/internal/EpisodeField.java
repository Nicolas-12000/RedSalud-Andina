package co.redsalud.andina.episodio.internal;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(schema = "episodio", name = "episode_field")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EpisodeField {

	@Id
	private UUID id;
	private UUID episodeId;
	private String fieldName;
	@Column(name = "field_value")
	private String value;
	private Instant updatedAt;

	public EpisodeField(UUID episodeId, String fieldName, String value, Instant updatedAt) {
		this.id = UUID.randomUUID();
		this.episodeId = episodeId;
		this.fieldName = fieldName;
		this.value = value;
		this.updatedAt = updatedAt;
	}

	public void overwrite(String newValue, Instant newUpdatedAt) {
		this.value = newValue;
		this.updatedAt = newUpdatedAt;
	}
}
