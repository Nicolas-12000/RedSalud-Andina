package co.redsalud.andina.legado.internal;

import java.util.Optional;
import java.util.UUID;

import co.redsalud.andina.legado.PatientHistory;

/** The read model. In-memory here; ElastiCache or DynamoDB in prod. Expiry is decided by the caller, not the store. */
public interface HistoryViewStore {

	Optional<PatientHistory> find(UUID patientId);

	void put(PatientHistory history);
}
