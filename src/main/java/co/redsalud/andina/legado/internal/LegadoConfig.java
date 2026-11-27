package co.redsalud.andina.legado.internal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import co.redsalud.andina.legado.LegacyUnavailableException;
import co.redsalud.andina.legado.PatientNotInLegacyException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;

@Configuration
@EnableConfigurationProperties(LegadoProperties.class)
public class LegadoConfig {

	/** Opens after repeated timeouts or failures so a down legacy system is not hammered. An unknown patient is not a failure. */
	@Bean
	CircuitBreaker legadoCircuitBreaker(LegadoProperties properties) {
		return CircuitBreaker.of("legado", breakerConfig(properties));
	}

	public static CircuitBreakerConfig breakerConfig(LegadoProperties properties) {
		return CircuitBreakerConfig.custom()
				.slidingWindowSize(properties.breakerWindow())
				.minimumNumberOfCalls(properties.breakerMinimumCalls())
				.failureRateThreshold(properties.breakerFailureRate())
				.waitDurationInOpenState(properties.breakerOpenFor())
				.recordExceptions(LegacyUnavailableException.class)
				.ignoreExceptions(PatientNotInLegacyException.class)
				.build();
	}
}
