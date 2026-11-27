package co.redsalud.andina.episodio;

/** Port to the attachment bytes. Local disk in dev and test; S3 with KMS encryption in prod. */
public interface AdjuntoStorage {

	void store(String key, String contentType, byte[] content);

	byte[] load(String key);
}
