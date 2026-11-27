package co.redsalud.andina.analitica;

/**
 * Aggregated indicators of one center: no patient-level data. {@code closeRate} and {@code averageWaitSeconds}
 * are null while there is nothing to compute them from.
 */
public record CenterIndicators(String centerId, long teleconsults, long uniquePatients, long interconsultasRequested,
		long interconsultasClosed, Double closeRate, Double averageWaitSeconds) {
}
