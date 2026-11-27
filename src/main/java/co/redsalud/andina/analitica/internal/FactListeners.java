package co.redsalud.andina.analitica.internal;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import co.redsalud.andina.episodio.EpisodeOpened;
import co.redsalud.andina.interconsulta.ConsultStatusChanged;
import lombok.RequiredArgsConstructor;

/**
 * Builds the analytics export from events. Same shape as @ApplicationModuleListener (async, own transaction,
 * tracked by the event registry) but on a single thread, see AnalyticsExecutorConfig.
 */
@Component
@RequiredArgsConstructor
class FactListeners {

	static final String UNKNOWN_CENTER = "unknown";

	private final EpisodeFactRepository episodeFacts;
	private final ConsultFactRepository consultFacts;
	private final Pseudonymizer pseudonymizer;

	@Async("analyticsExecutor")
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	void on(EpisodeOpened event) {
		if (!episodeFacts.existsById(event.episodeId())) {
			episodeFacts.save(new EpisodeFact(event.episodeId(), centerOf(event.centerId()),
					pseudonymizer.of(event.patientId()), event.openedAt()));
		}
	}

	@Async("analyticsExecutor")
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	void on(ConsultStatusChanged event) {
		ConsultFact fact = consultFacts.findById(event.consultId())
				.orElseGet(() -> new ConsultFact(event.consultId(), centerOf(event.centerId()), event.specialty()));
		fact.apply(event.to(), event.occurredAt());
		consultFacts.save(fact);
	}

	private static String centerOf(String centerId) {
		return centerId == null || centerId.isBlank() ? UNKNOWN_CENTER : centerId;
	}
}
