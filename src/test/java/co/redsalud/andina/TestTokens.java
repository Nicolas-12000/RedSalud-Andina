package co.redsalud.andina;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import co.redsalud.andina.identidad.Role;

/** Builds the authenticated caller for MockMvc: same claims and authorities a Cognito token would produce. */
public final class TestTokens {

	private TestTokens() {
	}

	public static RequestPostProcessor as(UUID subject, Role role, String center, String specialty) {
		return jwt().jwt(j -> {
			j.subject(subject.toString()).claim("cognito:groups", List.of(role.cognitoGroup()));
			if (center != null) j.claim("custom:center", center);
			if (specialty != null) j.claim("custom:specialty", specialty);
		}).authorities(new SimpleGrantedAuthority(role.authority()));
	}

	public static RequestPostProcessor as(UUID subject, Role role) {
		return as(subject, role, null, null);
	}
}
