package co.redsalud.andina.legado;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Canonical model of the legacy history. It keeps only what the specialist needs (data minimization):
 * names, addresses and other legacy fields stop at the anti-corruption layer.
 * {@code freshAt} is when the legacy data was extracted; {@code expiresAt} is when this copy stops being served.
 */
public record PatientHistory(UUID patientId, List<EpisodeSummary> episodes, List<DocumentRef> documents,
		Instant freshAt, Instant expiresAt, Source source) {

	public enum Source {
		/** Served from the local read model. */
		VIEW,
		/** Fetched from the legacy system just now. */
		LEGACY_DIRECT,
		/** The legacy system did not answer, so an expired copy is served; {@code expiresAt} shows how old it is. */
		STALE_VIEW
	}

	public record EpisodeSummary(String legacyEpisodeId, LocalDate date, String summary) {
	}

	public record DocumentRef(String documentId, String title, String type) {
	}

	public PatientHistory servedFrom(Source newSource) {
		return new PatientHistory(patientId, episodes, documents, freshAt, expiresAt, newSource);
	}
}
