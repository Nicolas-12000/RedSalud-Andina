package co.redsalud.andina.legado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import co.redsalud.andina.auditoria.AuditTrail;
import co.redsalud.andina.auditoria.ReasonRequiredException;
import co.redsalud.andina.identidad.AccessPolicy;
import co.redsalud.andina.legado.internal.HistoryViewStore;
import co.redsalud.andina.legado.internal.LegacyHistory;
import co.redsalud.andina.legado.internal.LegacyTranslator;
import co.redsalud.andina.legado.internal.LegadoClient;
import co.redsalud.andina.legado.internal.LegadoConfig;
import co.redsalud.andina.legado.internal.LegadoProperties;

class LegacyHistoryServiceTests {

	static final Duration TTL = Duration.ofHours(24);

	UUID patient = UUID.randomUUID();
	MutableClock clock = new MutableClock(Instant.parse("2026-03-01T12:00:00Z"));
	AtomicInteger legacyCalls = new AtomicInteger();
	AccessPolicy accessPolicy = mock(AccessPolicy.class);
	AuditTrail auditTrail = mock(AuditTrail.class);
	Map<UUID, PatientHistory> storage = new HashMap<>();
	HistoryViewStore store = new HistoryViewStore() {
		public Optional<PatientHistory> find(UUID id) {
			return Optional.ofNullable(storage.get(id));
		}

		public void put(PatientHistory history) {
			storage.put(history.patientId(), history);
		}
	};

	SimpleMeterRegistry meters = new SimpleMeterRegistry();

	LegacyHistoryService service(LegadoClient client, Duration directTimeout) {
		return service(client, directTimeout, 5);
	}

	LegacyHistoryService service(LegadoClient client, Duration directTimeout, int breakerMinimumCalls) {
		var properties = new LegadoProperties(TTL, Duration.ofDays(7), directTimeout, 100, 50f, breakerMinimumCalls,
				breakerMinimumCalls, Duration.ofSeconds(30));
		return new LegacyHistoryService(client, store, new LegacyTranslator(), accessPolicy, auditTrail, clock, properties,
				CircuitBreaker.of("test", LegadoConfig.breakerConfig(properties)), meters);
	}

	LegadoClient fastLegacy = code -> {
		legacyCalls.incrementAndGet();
		return new LegacyHistory(code, "Name", "Address", List.of(new LegacyHistory.Encounter("E-1", "15/03/2021", "n")),
				List.of(), "2026-03-01 06:00:00");
	};

	@Test
	void secondReadIsServedFromTheViewWithoutQueryingLegacy() {
		var service = service(fastLegacy, Duration.ofSeconds(6));

		PatientHistory first = service.read(patient, "consultation");
		PatientHistory second = service.read(patient, "consultation");

		assertThat(first.source()).isEqualTo(PatientHistory.Source.LEGACY_DIRECT);
		assertThat(second.source()).isEqualTo(PatientHistory.Source.VIEW);
		assertThat(legacyCalls).hasValue(1);
		assertThat(second.expiresAt()).isEqualTo(clock.instant().plus(TTL));
		assertThat(second.freshAt()).isEqualTo(Instant.parse("2026-03-01T11:00:00Z")); // 06:00 Bogota
	}

	@Test
	void expiredCopyIsNeverServedAndIsRefreshed() {
		var service = service(fastLegacy, Duration.ofSeconds(6));
		service.read(patient, "consultation");

		clock.advance(TTL.plusSeconds(1));
		PatientHistory refreshed = service.read(patient, "consultation");

		assertThat(refreshed.source()).isEqualTo(PatientHistory.Source.LEGACY_DIRECT);
		assertThat(legacyCalls).hasValue(2);
		assertThat(refreshed.expiresAt()).isEqualTo(clock.instant().plus(TTL));
	}

	@Test
	void slowLegacyTimesOutAndNothingIsCached() {
		LegadoClient slow = code -> {
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			return null;
		};
		var service = service(slow, Duration.ofMillis(100));

		assertThatThrownBy(() -> service.read(patient, "consultation")).isInstanceOf(LegacyUnavailableException.class);
		assertThat(storage).isEmpty();
	}

	@Test
	void patientUnknownToLegacyIsReportedAndNotCached() {
		var service = service(code -> null, Duration.ofSeconds(1));

		assertThatThrownBy(() -> service.read(patient, "consultation")).isInstanceOf(PatientNotInLegacyException.class);
		assertThat(storage).isEmpty();
	}

	@Test
	void whenLegacyIsDownAnExpiredCopyStillAnswersWithItsExpiryVisible() {
		boolean[] down = { false };
		LegadoClient flaky = code -> {
			if (down[0]) {
				throw new IllegalStateException("SOAP is down");
			}
			return fastLegacy.fetchHistory(code);
		};
		var service = service(flaky, Duration.ofSeconds(1));
		PatientHistory original = service.read(patient, "consultation");

		down[0] = true;
		clock.advance(TTL.plusSeconds(60));
		PatientHistory served = service.read(patient, "consultation");

		assertThat(served.source()).isEqualTo(PatientHistory.Source.STALE_VIEW);
		assertThat(served.expiresAt()).isBefore(clock.instant()).isEqualTo(original.expiresAt());
		assertThat(served.episodes()).isEqualTo(original.episodes());
	}

	@Test
	void circuitOpensAfterRepeatedFailuresAndLegacyIsLeftAlone() {
		AtomicInteger calls = new AtomicInteger();
		LegadoClient broken = code -> {
			calls.incrementAndGet();
			throw new IllegalStateException("SOAP is down");
		};
		var service = service(broken, Duration.ofSeconds(1), 3);

		for (int i = 0; i < 3; i++) {
			assertThatThrownBy(() -> service.read(patient, "consultation")).isInstanceOf(LegacyUnavailableException.class);
		}
		assertThatThrownBy(() -> service.read(patient, "consultation"))
				.isInstanceOf(LegacyUnavailableException.class).hasMessageContaining("circuit");

		assertThat(calls).hasValue(3);
	}

	@Test
	void readLatencyIsMeasuredBySource() {
		var service = service(fastLegacy, Duration.ofSeconds(6));

		service.read(patient, "consultation");
		service.read(patient, "consultation");

		assertThat(meters.get(LegacyHistoryService.READ_TIMER).tag("source", "LEGACY_DIRECT").timer().count()).isEqualTo(1);
		assertThat(meters.get(LegacyHistoryService.READ_TIMER).tag("source", "VIEW").timer().count()).isEqualTo(1);
	}

	@Test
	void deniedAccessNeverReachesAuditNorLegacy() {
		doThrow(new AccessDeniedException("no")).when(accessPolicy).requirePatientAccess(patient);
		var service = service(fastLegacy, Duration.ofSeconds(6));

		assertThatThrownBy(() -> service.read(patient, "consultation")).isInstanceOf(AccessDeniedException.class);
		verify(auditTrail, never()).recordRead(any(), any(), any());
		assertThat(legacyCalls).hasValue(0);
	}

	@Test
	void missingReasonStopsBeforeQueryingLegacy() {
		doThrow(new ReasonRequiredException()).when(auditTrail).recordRead(any(), any(), any());
		var service = service(fastLegacy, Duration.ofSeconds(6));

		assertThatThrownBy(() -> service.read(patient, " ")).isInstanceOf(ReasonRequiredException.class);
		assertThat(legacyCalls).hasValue(0);
	}

	static class MutableClock extends Clock {
		private Instant now;

		MutableClock(Instant start) {
			this.now = start;
		}

		void advance(Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public java.time.ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}
	}
}
