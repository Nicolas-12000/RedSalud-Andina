package co.redsalud.andina.agenda.internal;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(schema = "agenda", name = "booking")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Booking {

	@Id
	private UUID id;
	private UUID slotId;
	private UUID patientId;
	private UUID bookedBy;
	private Instant bookedAt;

	public Booking(UUID id, UUID slotId, UUID patientId, UUID bookedBy) {
		this.id = id;
		this.slotId = slotId;
		this.patientId = patientId;
		this.bookedBy = bookedBy;
		this.bookedAt = Instant.now();
	}

	public boolean sameRequestAs(UUID slotId, UUID patientId) {
		return this.slotId.equals(slotId) && this.patientId.equals(patientId);
	}
}
