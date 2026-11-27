package co.redsalud.andina.identidad;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Authenticated caller, built from a validated Cognito JWT. {@code centerId} and {@code specialty} may be null. */
public record CurrentUser(UUID subject, Role role, String centerId, String specialty) {

	static final String GROUPS_CLAIM = "cognito:groups";
	static final String CENTER_CLAIM = "custom:center";
	static final String SPECIALTY_CLAIM = "custom:specialty";

	public static CurrentUser from(Jwt jwt) {
		Role role = rolesOf(jwt).stream().findFirst()
				.orElseThrow(() -> new AccessDeniedException("Token has no recognized role"));
		return new CurrentUser(UUID.fromString(jwt.getSubject()), role,
				jwt.getClaimAsString(CENTER_CLAIM), jwt.getClaimAsString(SPECIALTY_CLAIM));
	}

	/** The authenticated caller of the current request or thread. */
	public static CurrentUser fromContext() {
		return from((Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal());
	}

	static List<Role> rolesOf(Jwt jwt) {
		List<String> groups = jwt.getClaimAsStringList(GROUPS_CLAIM);
		if (groups == null) {
			return List.of();
		}
		return groups.stream().map(Role::fromCognitoGroup).flatMap(java.util.Optional::stream).toList();
	}
}
