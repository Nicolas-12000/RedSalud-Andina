package co.redsalud.andina.agenda;

/** The request cannot be applied because the slot is taken or the id was already used for something else. */
public class AgendaConflictException extends RuntimeException {

	public AgendaConflictException(String message) {
		super(message);
	}
}
