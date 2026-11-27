package co.redsalud.andina.episodio;

import java.util.UUID;

import co.redsalud.andina.episodio.internal.Attachment;

public record AttachmentView(UUID id, UUID episodeId, String fileName, String contentType, long sizeBytes) {

	static AttachmentView of(Attachment a) {
		return new AttachmentView(a.getId(), a.getEpisodeId(), a.getFileName(), a.getContentType(), a.getSizeBytes());
	}
}
