package co.redsalud.andina.legado;

/** The legacy payload does not match its documented format. Strict on purpose: a silently dropped episode is worse than an error. */
public class LegacyTranslationException extends RuntimeException {

	public LegacyTranslationException(String message, Throwable cause) {
		super(message, cause);
	}
}
