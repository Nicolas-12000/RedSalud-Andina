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
@Table(schema = "agenda", name = "slot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Slot {

	@Id
	private UUID id;
	private UUID specialistId;
	private String specialty;
	private Instant startAt;
	private Instant endAt;

	public Slot(UUID specialistId, String specialty, Instant startAt, Instant endAt) {
		this.id = UUID.randomUUID();
		this.specialistId = specialistId;
		this.specialty = specialty;
		this.startAt = startAt;
		this.endAt = endAt;
	}
}
