package co.redsalud.andina.episodio.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import co.redsalud.andina.episodio.AdjuntoStorage;

@Component
@Profile({ "dev", "test", "postman" })
class LocalAdjuntoStorage implements AdjuntoStorage {

	private final Path baseDir;

	LocalAdjuntoStorage(@Value("${redsalud.attachments.local-dir}") Path baseDir) {
		this.baseDir = baseDir.toAbsolutePath().normalize();
	}

	@Override
	public void store(String key, String contentType, byte[] content) {
		Path target = resolve(key);
		try {
			Files.createDirectories(target.getParent());
			Files.write(target, content);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public byte[] load(String key) {
		try {
			return Files.readAllBytes(resolve(key));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private Path resolve(String key) {
		Path path = baseDir.resolve(key).normalize();
		if (!path.startsWith(baseDir)) {
			throw new IllegalArgumentException("Invalid storage key");
		}
		return path;
	}
}
