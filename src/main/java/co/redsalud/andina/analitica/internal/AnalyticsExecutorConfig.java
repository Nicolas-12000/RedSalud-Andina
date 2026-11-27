package co.redsalud.andina.analitica.internal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
class AnalyticsExecutorConfig {

	/**
	 * One thread, so facts of the same consult are applied in the order the events were committed.
	 * Created after the EntityManagerFactory so shutdown drains pending facts before the database closes.
	 */
	@Bean
	@DependsOn("entityManagerFactory")
	ThreadPoolTaskExecutor analyticsExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(1);
		executor.setThreadNamePrefix("analytics-facts-");
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(10);
		return executor;
	}
}
