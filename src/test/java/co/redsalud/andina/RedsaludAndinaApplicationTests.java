package co.redsalud.andina;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class RedsaludAndinaApplicationTests {

	@Autowired ApplicationContext context;

	@Test
	void contextLoads() {
	}

	@Test
	void moduleListenersDoNotShareTheSingleAnalyticsThread() {
		assertThat(context.getBean("taskExecutor")).isNotSameAs(context.getBean("analyticsExecutor"));
	}

}
