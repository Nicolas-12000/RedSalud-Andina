package co.redsalud.andina.interconsulta;

/** REQUESTED -> SCHEDULED -> ATTENDED -> CLOSED. No skipping, no going back. */
public enum ConsultStatus {
	REQUESTED, SCHEDULED, ATTENDED, CLOSED;

	public boolean canMoveTo(ConsultStatus target) {
		return target.ordinal() == ordinal() + 1;
	}
}
