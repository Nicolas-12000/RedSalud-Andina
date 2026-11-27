package co.redsalud.andina.interconsulta;

import java.time.Instant;
import java.util.UUID;

/** Published on creation (from = null) and on every transition. */
public record ConsultStatusChanged(UUID consultId, UUID patientId, String specialty, String centerId, ConsultStatus from,
		ConsultStatus to, UUID specialistId, Instant occurredAt) {
}
