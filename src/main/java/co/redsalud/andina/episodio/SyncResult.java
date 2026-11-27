package co.redsalud.andina.episodio;

import java.util.UUID;

public record SyncResult(UUID eventId, Status status, String message, boolean conflict) {

	public enum Status {
		APPLIED,
		/** Not applied; the client keeps the event and retries (or goes to the server for server-only types). */
		REJECTED
	}

	static SyncResult applied(UUID eventId, String message, boolean conflict) {
		return new SyncResult(eventId, Status.APPLIED, message, conflict);
	}

	static SyncResult rejected(UUID eventId, String message) {
		return new SyncResult(eventId, Status.REJECTED, message, false);
	}
}
