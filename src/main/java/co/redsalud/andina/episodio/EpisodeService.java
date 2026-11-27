package co.redsalud.andina.episodio;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.redsalud.andina.auditoria.AuditTrail;
import co.redsalud.andina.episodio.internal.AttachmentRepository;
import co.redsalud.andina.episodio.internal.Episode;
import co.redsalud.andina.episodio.internal.EpisodeFieldRepository;
import co.redsalud.andina.episodio.internal.EpisodeRepository;
import co.redsalud.andina.identidad.AccessPolicy;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EpisodeService {

	private final EpisodeRepository episodeRepository;
	private final EpisodeFieldRepository fieldRepository;
	private final AttachmentRepository attachmentRepository;
	private final AccessPolicy accessPolicy;
	private final AuditTrail auditTrail;

	/** Access is checked first, then the read is audited (reason required), then the data is returned. */
	@PreAuthorize("hasAnyRole('RURAL_DOCTOR','SPECIALIST','PATIENT')")
	@Transactional
	public EpisodeView read(UUID episodeId, String reason) {
		Episode episode = episodeRepository.findById(episodeId).orElseThrow(() -> new EpisodeNotFoundException(episodeId));
		accessPolicy.requirePatientAccess(episode.getPatientId());
		auditTrail.recordRead("episode", episodeId.toString(), reason);

		var fields = fieldRepository.findByEpisodeId(episodeId).stream().collect(Collectors.toMap(
				f -> f.getFieldName(), f -> new EpisodeView.FieldView(f.getValue(), f.getUpdatedAt())));
		List<AttachmentView> attachments = attachmentRepository.findByEpisodeId(episodeId).stream()
				.map(AttachmentView::of).toList();
		return new EpisodeView(episode.getId(), episode.getPatientId(), episode.getCenterId(),
				episode.getDoctorSubject(), episode.getStatus(), episode.getOpenedAt(), episode.getAdmittedAt(), fields, attachments);
	}
}
