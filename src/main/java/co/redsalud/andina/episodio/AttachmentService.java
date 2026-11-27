package co.redsalud.andina.episodio;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.redsalud.andina.auditoria.AuditTrail;
import co.redsalud.andina.episodio.internal.Attachment;
import co.redsalud.andina.episodio.internal.AttachmentRepository;
import co.redsalud.andina.episodio.internal.Episode;
import co.redsalud.andina.episodio.internal.EpisodeRepository;
import co.redsalud.andina.identidad.AccessPolicy;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AttachmentService {

	private final EpisodeRepository episodeRepository;
	private final AttachmentRepository attachmentRepository;
	private final AdjuntoStorage storage;
	private final AccessPolicy accessPolicy;
	private final AuditTrail auditTrail;

	/**
	 * Idempotent on {@code attachmentId} (client generated). shortcut: bytes are held in memory,
	 * switch to streaming when attachments are larger than a few MB.
	 */
	@PreAuthorize("hasAnyRole('RURAL_DOCTOR','SPECIALIST')")
	@Transactional
	public AttachmentView upload(UUID episodeId, UUID attachmentId, String fileName, String contentType, byte[] content) {
		Episode episode = episodeRepository.findById(episodeId).orElseThrow(() -> new EpisodeNotFoundException(episodeId));
		accessPolicy.requirePatientAccess(episode.getPatientId());
		var existing = attachmentRepository.findById(attachmentId);
		if (existing.isPresent()) {
			if (!existing.get().getEpisodeId().equals(episodeId)) {
				throw new EpisodeConflictException("Attachment id already used in another episode");
			}
			return AttachmentView.of(existing.get());
		}
		String key = "episodes/" + episodeId + "/" + attachmentId;
		storage.store(key, contentType, content);
		return AttachmentView.of(attachmentRepository.save(
				new Attachment(attachmentId, episodeId, fileName, contentType, content.length, key)));
	}

	@PreAuthorize("hasAnyRole('RURAL_DOCTOR','SPECIALIST','PATIENT')")
	@Transactional
	public AttachmentContent download(UUID attachmentId, String reason) {
		Attachment attachment = attachmentRepository.findById(attachmentId)
				.orElseThrow(() -> new EpisodeNotFoundException(attachmentId));
		Episode episode = episodeRepository.findById(attachment.getEpisodeId()).orElseThrow();
		accessPolicy.requirePatientAccess(episode.getPatientId());
		auditTrail.recordRead("attachment", attachmentId.toString(), reason);
		return new AttachmentContent(attachment.getFileName(), attachment.getContentType(),
				storage.load(attachment.getStorageKey()));
	}
}
