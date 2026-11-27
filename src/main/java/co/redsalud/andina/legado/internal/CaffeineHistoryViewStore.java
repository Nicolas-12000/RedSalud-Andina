package co.redsalud.andina.legado.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import co.redsalud.andina.legado.PatientHistory;

/** Level 1 of the read model: bounded and per instance. Entries outlive their TTL so they can answer while legacy is down. */
@Component
class CaffeineHistoryViewStore implements HistoryViewStore {

	private final Cache<UUID, PatientHistory> cache;

	CaffeineHistoryViewStore(LegadoProperties properties) {
		this.cache = Caffeine.newBuilder()
				.maximumSize(properties.maxEntries())
				.expireAfterWrite(properties.staleRetention())
				.build();
	}

	@Override
	public Optional<PatientHistory> find(UUID patientId) {
		return Optional.ofNullable(cache.getIfPresent(patientId));
	}

	@Override
	public void put(PatientHistory history) {
		cache.put(history.patientId(), history);
	}
}
