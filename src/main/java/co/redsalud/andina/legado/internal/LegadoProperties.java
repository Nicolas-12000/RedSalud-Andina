package co.redsalud.andina.legado.internal;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param viewTtl        how long a copy is served as fresh
 * @param staleRetention how long an expired copy is kept to answer when the legacy system is down
 * @param directTimeout  upper bound for the direct query (objective <= 6 s)
 */
@ConfigurationProperties("redsalud.legado")
public record LegadoProperties(@DefaultValue("PT24H") Duration viewTtl, @DefaultValue("P7D") Duration staleRetention,
		@DefaultValue("PT6S") Duration directTimeout, @DefaultValue("10000") long maxEntries,
		@DefaultValue("50") float breakerFailureRate, @DefaultValue("10") int breakerWindow,
		@DefaultValue("5") int breakerMinimumCalls, @DefaultValue("PT30S") Duration breakerOpenFor) {
}
