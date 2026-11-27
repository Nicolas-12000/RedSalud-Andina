package co.redsalud.andina.episodio;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.redsalud.andina.auditoria.AuditTrail;
import co.redsalud.andina.episodio.internal.Episode;
import co.redsalud.andina.episodio.internal.EpisodeField;
import co.redsalud.andina.episodio.internal.EpisodeFieldRepository;
import co.redsalud.andina.episodio.internal.EpisodeRepository;
import co.redsalud.andina.episodio.internal.ProcessedEvent;
import co.redsalud.andina.episodio.internal.ProcessedEventRepository;
import co.redsalud.andina.identidad.AccessPolicy;
import co.redsalud.andina.identidad.CareRelationships;
import co.redsalud.andina.identidad.CurrentUser;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;

/**
 * Applies the offline queue of a center. Idempotent per event id, ordered per aggregate. Clinical fields are
 * resolved last-writer-wins field by field and the losing value goes to the audit trail. Server-authoritative
 * events are rejected so the client retries against the server.
 */
@Service
@RequiredArgsConstructor
public class EpisodeSyncService {

	static final int MAX_VALUE_LENGTH = 4000;

	private final EpisodeRepository episodeRepository;
	private final EpisodeFieldRepository fieldRepository;
	private final ProcessedEventRepository processedEventRepository;
	private final AuditTrail auditTrail;
	private final AccessPolicy accessPolicy;
	private final CareRelationships careRelationships;
	private final ApplicationEventPublisher events;
	private final MeterRegistry meters;

	@PreAuthorize("hasAnyRole('RURAL_DOCTOR','SPECIALIST')")
	@Transactional
	public List<SyncResult> sync(List<SyncEvent> batch) {
		CurrentUser user = CurrentUser.fromContext();
		List<SyncEvent> ordered = batch.stream()
				.sorted(Comparator.comparing(SyncEvent::aggregateId).thenComparingLong(SyncEvent::sequence))
				.toList();
		Set<UUID> blockedAggregates = new HashSet<>();
		List<SyncResult> results = new ArrayList<>();
		for (SyncEvent event : ordered) {
			SyncResult result = blockedAggregates.contains(event.aggregateId())
					? SyncResult.rejected(event.eventId(), "A previous event of this aggregate was rejected; order is kept")
					: apply(user, event);
			if (result.status() == SyncResult.Status.REJECTED) {
				blockedAggregates.add(event.aggregateId());
			}
			results.add(result);
			meters.counter("redsalud.sync.events", "status", result.status().name()).increment();
		}
		return results;
	}

	private SyncResult apply(CurrentUser user, SyncEvent event) {
		Optional<ProcessedEvent> already = processedEventRepository.findById(event.eventId());
		if (already.isPresent()) {
			return SyncResult.applied(event.eventId(), already.get().getMessage(), already.get().isConflict());
		}
		return switch (event.type()) {
			case BOOKING_REQUEST, PRESCRIPTION_SIGN -> SyncResult.rejected(event.eventId(),
					"SERVER_AUTHORITATIVE: " + event.type() + " is not resolved at the edge; retry against the server");
			case EPISODE_OPENED -> open(user, event);
			case FIELD_UPDATED -> updateField(event);
			case EPISODE_STATUS_CHANGED -> changeStatus(event);
		};
	}

	private SyncResult open(CurrentUser user, SyncEvent event) {
		if (event.patientId() == null) {
			return SyncResult.rejected(event.eventId(), "patientId is required");
		}
		Optional<Episode> existing = episodeRepository.findById(event.aggregateId());
		if (existing.isPresent()) {
			if (!existing.get().getPatientId().equals(event.patientId())) {
				return SyncResult.rejected(event.eventId(), "Episode id already used for another patient");
			}
			return remember(event, "Episode already existed", false);
		}
		episodeRepository.save(new Episode(event.aggregateId(), event.patientId(), user.centerId(), user.subject(),
				event.occurredAt()));
		careRelationships.grant(user.subject(), event.patientId());
		events.publishEvent(new EpisodeOpened(event.aggregateId(), event.patientId(), user.centerId(), event.occurredAt()));
		return remember(event, "Episode opened", false);
	}

	private SyncResult changeStatus(SyncEvent event) {
		Optional<EpisodeStatus> target = event.value() == null ? Optional.empty() : EpisodeStatus.parse(event.value());
		if (target.isEmpty()) {
			return SyncResult.rejected(event.eventId(), "A known episode status is required");
		}
		Optional<Episode> found = episodeRepository.findById(event.aggregateId());
		if (found.isEmpty()) {
			return SyncResult.rejected(event.eventId(), "Episode not found yet; send EPISODE_OPENED first");
		}
		Episode episode = found.get();
		accessPolicy.requirePatientAccess(episode.getPatientId());
		if (target.get().ordinal() <= episode.getStatus().ordinal()) {
			return remember(event, "Already at or past " + target.get(), false);
		}
		if (!episode.getStatus().canMoveTo(target.get())) {
			return SyncResult.rejected(event.eventId(), "Cannot move episode from " + episode.getStatus() + " to " + target.get() + ": a step is missing");
		}
		episode.moveTo(target.get(), event.occurredAt());
		return remember(event, "Status " + target.get(), false);
	}

	private SyncResult updateField(SyncEvent event) {
		Optional<ClinicalField> field = event.field() == null ? Optional.empty() : ClinicalField.parse(event.field());
		if (field.isEmpty() || event.value() == null || event.value().length() > MAX_VALUE_LENGTH) {
			return SyncResult.rejected(event.eventId(), "A known field and a value of at most 4000 chars are required");
		}
		Optional<Episode> episode = episodeRepository.findById(event.aggregateId());
		if (episode.isEmpty()) {
			return SyncResult.rejected(event.eventId(), "Episode not found yet; send EPISODE_OPENED first");
		}
		accessPolicy.requirePatientAccess(episode.get().getPatientId());

		String name = field.get().name();
		Optional<EpisodeField> current = fieldRepository.findByEpisodeIdAndFieldName(event.aggregateId(), name);
		if (current.isEmpty()) {
			fieldRepository.save(new EpisodeField(event.aggregateId(), name, event.value(), event.occurredAt()));
			return remember(event, "Field set", false);
		}
		EpisodeField stored = current.get();
		if (stored.getValue().equals(event.value())) {
			return remember(event, "Field unchanged", false);
		}
		boolean concurrent = event.baseUpdatedAt() == null || stored.getUpdatedAt().isAfter(event.baseUpdatedAt());
		boolean incomingWins = !event.occurredAt().isBefore(stored.getUpdatedAt());
		String kept = incomingWins ? event.value() : stored.getValue();
		String discarded = incomingWins ? stored.getValue() : event.value();
		if (incomingWins) {
			stored.overwrite(event.value(), event.occurredAt());
		}
		if (concurrent) {
			auditTrail.recordFieldConflict("episode", event.aggregateId().toString(), name, kept, discarded);
		}
		return remember(event, incomingWins ? "Field updated" : "Older update ignored (last writer wins)", concurrent);
	}

	private SyncResult remember(SyncEvent event, String message, boolean conflict) {
		processedEventRepository.save(new ProcessedEvent(event.eventId(), message, conflict));
		return SyncResult.applied(event.eventId(), message, conflict);
	}
}
