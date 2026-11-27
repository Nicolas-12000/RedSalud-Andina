package co.redsalud.andina.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AuditTrailTests {

	@Autowired AuditTrail auditTrail;
	@Autowired AuditStore store;
	@Autowired MockMvc mockMvc;

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void readWithoutReasonIsRejectedAndNothingIsStored() {
		authenticateAs(UUID.randomUUID(), "rural-doctor");
		String resourceId = UUID.randomUUID().toString();

		assertThatThrownBy(() -> auditTrail.recordRead("episode", resourceId, "  ")).isInstanceOf(ReasonRequiredException.class);
		assertThat(store.findByResource("episode", resourceId)).isEmpty();
	}

	@Test
	void readIsStoredWithUserDateResourceAndReason() {
		UUID doctor = UUID.randomUUID();
		authenticateAs(doctor, "rural-doctor");
		String resourceId = UUID.randomUUID().toString();

		auditTrail.recordRead("episode", resourceId, "follow-up visit");

		assertThat(store.findByResource("episode", resourceId)).singleElement().satisfies(r -> {
			assertThat(r.kind()).isEqualTo(AuditKind.READ);
			assertThat(r.actor()).isEqualTo(doctor);
			assertThat(r.reason()).isEqualTo("follow-up visit");
			assertThat(r.occurredAt()).isNotNull();
		});
	}

	@Test
	void reportIsReproducibleAndListsOnlyReads() throws Exception {
		authenticateAs(UUID.randomUUID(), "specialist");
		String resourceId = UUID.randomUUID().toString();
		auditTrail.recordRead("episode", resourceId, "second opinion");
		auditTrail.recordFieldConflict("episode", resourceId, "notes", "kept", "lost");
		SecurityContextHolder.clearContext();

		String first = mockMvc.perform(get("/api/audit/resources/episode/{id}/reads", resourceId)
						.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_AUDITOR"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.reads.length()").value(1))
				.andExpect(jsonPath("$.reads[0].reason").value("second opinion"))
				.andExpect(jsonPath("$.reportVersion").value("1"))
				.andReturn().getResponse().getContentAsString();
		String second = mockMvc.perform(get("/api/audit/resources/episode/{id}/reads", resourceId)
						.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_AUDITOR"))))
				.andReturn().getResponse().getContentAsString();

		assertThat(second).isEqualTo(first);
	}

	@Test
	void hashIsStableForTheSameEntries() {
		var entries = List.of(new ReadReport.Entry(new UUID(0, 1), java.time.Instant.parse("2026-01-01T00:00:00Z"), "r"));
		assertThat(ReadReportService.hash(entries))
				.isEqualTo(ReadReportService.hash(entries))
				.hasSize(64);
	}

	@Test
	void reportIsForbiddenForNonAuditors() throws Exception {
		mockMvc.perform(get("/api/audit/resources/episode/x/reads")
						.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_RURAL_DOCTOR"))))
				.andExpect(status().isForbidden());
	}

	private static void authenticateAs(UUID subject, String group) {
		Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(subject.toString())
				.claim("cognito:groups", List.of(group)).build();
		SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
				List.of(new SimpleGrantedAuthority("ROLE_X")), subject.toString()));
	}
}
