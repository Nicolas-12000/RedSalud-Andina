package co.redsalud.andina.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ApiSecurityTests {

	@Autowired MockMvc mockMvc;

	@Test
	void everyApiRouteRequiresAToken() throws Exception {
		mockMvc.perform(get("/api/agenda/slots").param("specialty", "cardiology")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/audit/resources/episode/x/reads")).andExpect(status().isUnauthorized());
	}
}
