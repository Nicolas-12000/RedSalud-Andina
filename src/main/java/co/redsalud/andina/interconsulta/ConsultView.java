package co.redsalud.andina.interconsulta;

import java.time.Instant;
import java.util.UUID;

import co.redsalud.andina.interconsulta.internal.Consult;

public record ConsultView(UUID id, UUID episodeId, UUID patientId, UUID requestedBy, String specialty,
		ConsultStatus status, UUID bookingId, UUID specialistId, Instant updatedAt) {

	static ConsultView of(Consult c) {
		return new ConsultView(c.getId(), c.getEpisodeId(), c.getPatientId(), c.getRequestedBy(), c.getSpecialty(),
				c.getStatus(), c.getBookingId(), c.getSpecialistId(), c.getUpdatedAt());
	}
}
