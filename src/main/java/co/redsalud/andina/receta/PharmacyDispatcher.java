package co.redsalud.andina.receta;

/** Port to the pharmacy. Logging stub in dev and test; an SQS publisher in prod. Must be safe to call twice. */
public interface PharmacyDispatcher {

	void send(PrescriptionSigned message);
}
