package co.redsalud.andina.identidad.internal;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(schema = "identidad", name = "care_relationship")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CareRelationship {

	@Id
	private UUID id;
	private UUID professionalSubject;
	private UUID patientId;
	private Instant grantedAt;

	public CareRelationship(UUID professionalSubject, UUID patientId) {
		this.id = UUID.randomUUID();
		this.professionalSubject = professionalSubject;
		this.patientId = patientId;
		this.grantedAt = Instant.now();
	}
}
