package co.redsalud.andina.episodio;

public enum SyncEventType {
	EPISODE_OPENED,
	FIELD_UPDATED,
	/** Moves the episode forward; {@code value} is the target status. */
	EPISODE_STATUS_CHANGED,
	/** Server-authoritative: never resolved at the edge, always rejected here. */
	BOOKING_REQUEST,
	/** Server-authoritative: a signed prescription is never resolved at the edge. */
	PRESCRIPTION_SIGN
}
