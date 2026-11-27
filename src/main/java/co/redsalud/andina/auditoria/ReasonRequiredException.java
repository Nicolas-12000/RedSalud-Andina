package co.redsalud.andina.auditoria;

public class ReasonRequiredException extends RuntimeException {

	public ReasonRequiredException() {
		super("A reason is required to read clinical records");
	}
}
