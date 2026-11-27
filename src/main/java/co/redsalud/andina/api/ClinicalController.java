package co.redsalud.andina.api;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import co.redsalud.andina.episodio.AttachmentContent;
import co.redsalud.andina.episodio.AttachmentService;
import co.redsalud.andina.episodio.AttachmentView;
import co.redsalud.andina.episodio.EpisodeService;
import co.redsalud.andina.episodio.EpisodeSyncService;
import co.redsalud.andina.episodio.EpisodeView;
import co.redsalud.andina.episodio.SyncEvent;
import co.redsalud.andina.episodio.SyncResult;
import co.redsalud.andina.legado.LegacyHistoryService;
import co.redsalud.andina.legado.PatientHistory;
import co.redsalud.andina.receta.PrescriptionService;
import co.redsalud.andina.receta.PrescriptionView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/** Clinical entry point: episodes, offline sync, attachments, prescriptions and the legacy history. Every read needs an access reason. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
class ClinicalController {

	static final String REASON_HEADER = "X-Access-Reason";

	private final EpisodeSyncService syncService;
	private final EpisodeService episodeService;
	private final AttachmentService attachmentService;
	private final PrescriptionService prescriptionService;
	private final LegacyHistoryService legacyHistoryService;

	record SavePrescriptionRequest(@NotNull UUID episodeId, @NotNull UUID patientId, @NotBlank @Size(max = 4000) String content) {
	}

	@PostMapping("/sync/events")
	List<SyncResult> sync(@RequestBody List<@Valid SyncEvent> events) {
		return syncService.sync(events);
	}

	@GetMapping("/episodes/{episodeId}")
	EpisodeView episode(@PathVariable UUID episodeId, @RequestHeader(REASON_HEADER) String reason) {
		return episodeService.read(episodeId, reason);
	}

	@PutMapping("/episodes/{episodeId}/attachments/{attachmentId}")
	AttachmentView upload(@PathVariable UUID episodeId, @PathVariable UUID attachmentId,
			@RequestPart("file") MultipartFile file) throws IOException {
		String contentType = file.getContentType() != null ? file.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
		return attachmentService.upload(episodeId, attachmentId, file.getOriginalFilename(), contentType, file.getBytes());
	}

	@GetMapping("/attachments/{attachmentId}")
	ResponseEntity<byte[]> download(@PathVariable UUID attachmentId, @RequestHeader(REASON_HEADER) String reason) {
		AttachmentContent attachment = attachmentService.download(attachmentId, reason);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(attachment.contentType()))
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename(attachment.fileName()).build().toString())
				.body(attachment.content());
	}

	@PutMapping("/prescriptions/{prescriptionId}")
	PrescriptionView saveDraft(@PathVariable UUID prescriptionId, @Valid @RequestBody SavePrescriptionRequest request) {
		return prescriptionService.saveDraft(prescriptionId, request.episodeId(), request.patientId(), request.content());
	}

	@PostMapping("/prescriptions/{prescriptionId}/sign")
	PrescriptionView sign(@PathVariable UUID prescriptionId) {
		return prescriptionService.sign(prescriptionId);
	}

	@GetMapping("/prescriptions/{prescriptionId}")
	PrescriptionView prescription(@PathVariable UUID prescriptionId, @RequestHeader(REASON_HEADER) String reason) {
		return prescriptionService.read(prescriptionId, reason);
	}

	@GetMapping("/patients/{patientId}/legacy-history")
	ResponseEntity<PatientHistory> legacyHistory(@PathVariable UUID patientId, @RequestHeader(REASON_HEADER) String reason) {
		PatientHistory history = legacyHistoryService.read(patientId, reason);
		return ResponseEntity.ok()
				.header("X-Data-Freshness", history.freshAt().toString())
				.header("X-Data-Expires-At", history.expiresAt().toString())
				.header("X-Data-Source", history.source().name())
				.body(history);
	}
}
