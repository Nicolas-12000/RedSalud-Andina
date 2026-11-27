package co.redsalud.andina.receta.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import co.redsalud.andina.receta.PrescriptionImmutableException;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A signed prescription never changes: every mutation goes through this class and fails once signed. */
@Entity
@Table(schema = "receta", name = "prescription")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Prescription {

	@Id
	private UUID id;
	private UUID episodeId;
	private UUID patientId;
	private UUID prescriberSubject;
	private String content;
	@Enumerated(EnumType.STRING)
	private PrescriptionStatus status;
	private String contentHash;
	private Instant signedAt;
	private Instant createdAt;

	public Prescription(UUID id, UUID episodeId, UUID patientId, UUID prescriberSubject, String content) {
		this.id = id;
		this.episodeId = episodeId;
		this.patientId = patientId;
		this.prescriberSubject = prescriberSubject;
		this.content = content;
		this.status = PrescriptionStatus.DRAFT;
		this.createdAt = Instant.now();
	}

	public boolean isSigned() {
		return status == PrescriptionStatus.SIGNED;
	}

	public boolean sameRequestAs(UUID episodeId, UUID patientId, String content) {
		return this.episodeId.equals(episodeId) && this.patientId.equals(patientId) && this.content.equals(content);
	}

	public void changeContent(String newContent) {
		requireDraft();
		this.content = newContent;
	}

	/** shortcut: the "signature" is a SHA-256 of the content plus signer and time; plug a certificate-based signature when the regulator requires it. */
	public void sign() {
		requireDraft();
		this.contentHash = sha256(content);
		this.signedAt = Instant.now();
		this.status = PrescriptionStatus.SIGNED;
	}

	private void requireDraft() {
		if (isSigned()) {
			throw new PrescriptionImmutableException(id);
		}
	}

	private static String sha256(String text) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
