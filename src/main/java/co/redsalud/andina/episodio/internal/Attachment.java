package co.redsalud.andina.episodio.internal;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(schema = "episodio", name = "attachment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Attachment {

	@Id
	private UUID id;
	private UUID episodeId;
	private String fileName;
	private String contentType;
	private long sizeBytes;
	private String storageKey;
	private Instant createdAt;

	public Attachment(UUID id, UUID episodeId, String fileName, String contentType, long sizeBytes, String storageKey) {
		this.id = id;
		this.episodeId = episodeId;
		this.fileName = fileName;
		this.contentType = contentType;
		this.sizeBytes = sizeBytes;
		this.storageKey = storageKey;
		this.createdAt = Instant.now();
	}
}
