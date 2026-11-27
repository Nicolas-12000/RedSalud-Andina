package co.redsalud.andina.legado;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import co.redsalud.andina.auditoria.AuditTrail;
import co.redsalud.andina.identidad.AccessPolicy;
import co.redsalud.andina.legado.internal.HistoryViewStore;
import co.redsalud.andina.legado.internal.LegacyHistory;
import co.redsalud.andina.legado.internal.LegacyTranslator;
import co.redsalud.andina.legado.internal.LegadoClient;
import co.redsalud.andina.legado.internal.LegadoProperties;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

/**
 * Reads the legacy history through the read model. A fresh copy is served first; without one the legacy system is
 * queried directly (timeout + circuit breaker). If legacy is down, an expired copy still answers, marked
 * {@link PatientHistory.Source#STALE_VIEW} with its expiry visible, instead of failing the specialist.
 */
@Service
@Slf4j
public class LegacyHistoryService {

	static final String READ_TIMER = "redsalud.legado.history.read";

	private final LegadoClient client;
	private final HistoryViewStore store;
	private final LegacyTranslator translator;
	private final AccessPolicy accessPolicy;
	private final AuditTrail auditTrail;
	private final Clock clock;
	private final LegadoProperties properties;
	private final CircuitBreaker breaker;
	private final MeterRegistry meters;
	private final Executor directQueries = Executors.newVirtualThreadPerTaskExecutor();

	LegacyHistoryService(LegadoClient client, HistoryViewStore store, LegacyTranslator translator,
			AccessPolicy accessPolicy, AuditTrail auditTrail, Clock clock, LegadoProperties properties,
			CircuitBreaker breaker, MeterRegistry meters) {
		this.client = client;
		this.store = store;
		this.translator = translator;
		this.accessPolicy = accessPolicy;
		this.auditTrail = auditTrail;
		this.clock = clock;
		this.properties = properties;
		this.breaker = breaker;
		this.meters = meters;
	}

	/** Access first, then the read is audited (reason required), then data. A failed legacy call still leaves the attempt logged. */
	@PreAuthorize("hasAnyRole('RURAL_DOCTOR','SPECIALIST','PATIENT')")
	public PatientHistory read(UUID patientId, String reason) {
		Timer.Sample sample = Timer.start(meters);
		String source = "ERROR";
		try {
			accessPolicy.requirePatientAccess(patientId);
			auditTrail.recordRead("legacy-history", patientId.toString(), reason);
			PatientHistory history = resolve(patientId);
			source = history.source().name();
			return history;
		} finally {
			sample.stop(Timer.builder(READ_TIMER).tag("source", source).publishPercentiles(0.95).register(meters));
		}
	}

	private PatientHistory resolve(UUID patientId) {
		Instant now = clock.instant();
		Optional<PatientHistory> cached = store.find(patientId);
		if (cached.isPresent() && cached.get().expiresAt().isAfter(now)) {
			return cached.get().servedFrom(PatientHistory.Source.VIEW);
		}
		try {
			return queryLegacy(patientId, now);
		} catch (LegacyUnavailableException e) {
			if (cached.isPresent()) {
				log.warn("Legacy unavailable for patient {}; serving an expired copy", patientId);
				return cached.get().servedFrom(PatientHistory.Source.STALE_VIEW);
			}
			throw e;
		}
	}

	// shortcut: concurrent misses for one patient each query the legacy system; add per-patient single-flight if that load matters.
	private PatientHistory queryLegacy(UUID patientId, Instant now) {
		LegacyHistory legacy;
		try {
			legacy = breaker.executeSupplier(() -> fetchWithTimeout(patientId));
		} catch (CallNotPermittedException e) {
			throw new LegacyUnavailableException("Legacy circuit breaker is open", e);
		}
		PatientHistory history = translator.toCanonical(patientId, legacy, now.plus(properties.viewTtl()));
		store.put(history);
		return history;
	}

	private LegacyHistory fetchWithTimeout(UUID patientId) {
		// shortcut: the legacy patient code is the patient UUID; change here if the legacy system uses its own codes.
		String legacyCode = patientId.toString();
		CompletableFuture<LegacyHistory> call = CompletableFuture.supplyAsync(() -> client.fetchHistory(legacyCode), directQueries);
		try {
			LegacyHistory legacy = call.get(properties.directTimeout().toMillis(), TimeUnit.MILLISECONDS);
			if (legacy == null) {
				throw new PatientNotInLegacyException(patientId);
			}
			return legacy;
		} catch (TimeoutException e) {
			call.cancel(true);
			throw new LegacyUnavailableException("Legacy system did not answer within " + properties.directTimeout(), e);
		} catch (ExecutionException e) {
			throw new LegacyUnavailableException("Legacy system failed", e.getCause());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new LegacyUnavailableException("Interrupted while waiting for the legacy system", e);
		}
	}
}
