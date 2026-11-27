package co.redsalud.andina.legado.internal;

/** Read-only port to the legacy system (nightly SOAP + read database). The legacy system is never modified. */
public interface LegadoClient {

	/** Direct query to the legacy system; slow (objective <= 6 s). Returns null when the patient is unknown there. */
	LegacyHistory fetchHistory(String patientCode);
}
