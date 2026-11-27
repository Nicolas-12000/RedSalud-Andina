package co.redsalud.andina;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Default executor for @Async and @ApplicationModuleListener. It must be named "taskExecutor": with two
 * TaskExecutor beans (this one and the single-thread analytics one) that name is how Spring picks the default.
 * Created after the EntityManagerFactory so shutdown drains listeners before the database closes.
 */
@Configuration
@EnableAsync
class AsyncConfig {

	@Bean("taskExecutor")
	@DependsOn("entityManagerFactory")
	ThreadPoolTaskExecutor taskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(4);
		executor.setThreadNamePrefix("module-listener-");
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(10);
		return executor;
	}
}
