package co.redsalud.andina.interconsulta.internal;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Addressed to one person ({@code recipientSubject}) or to every specialist of a specialty. */
@Entity
@Table(schema = "interconsulta", name = "alert")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Alert {

	@Id
	private UUID id;
	private UUID consultId;
	private UUID recipientSubject;
	private String recipientSpecialty;
	private String kind;
	private String message;
	private Instant createdAt;

	private Alert(UUID consultId, UUID recipientSubject, String recipientSpecialty, String kind, String message) {
		this.id = UUID.randomUUID();
		this.consultId = consultId;
		this.recipientSubject = recipientSubject;
		this.recipientSpecialty = recipientSpecialty;
		this.kind = kind;
		this.message = message;
		this.createdAt = Instant.now();
	}

	public static Alert forPerson(UUID consultId, UUID subject, String kind, String message) {
		return new Alert(consultId, subject, null, kind, message);
	}

	public static Alert forSpecialty(UUID consultId, String specialty, String kind, String message) {
		return new Alert(consultId, null, specialty, kind, message);
	}
}
