package co.redsalud.andina.identidad;

import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Role + attribute rules, used from {@code @PreAuthorize("@accessPolicy.canAccessPatient(#patientId)")}
 * on the methods that touch the data, so the check does not depend on the controller.
 */
@Component("accessPolicy")
@RequiredArgsConstructor
public class AccessPolicy {

	private final CareRelationships careRelationships;

	public boolean canAccessPatient(Authentication authentication, UUID patientId) {
		CurrentUser user = currentUser(authentication);
		return switch (user.role()) {
			case PATIENT -> user.subject().equals(patientId);
			case AUDITOR, NETWORK_MANAGER -> false;
			case RURAL_DOCTOR, SPECIALIST -> careRelationships.exists(user.subject(), patientId);
		};
	}

	/** Programmatic form for services that must check access before reading, then audit. */
	public void requirePatientAccess(UUID patientId) {
		if (!canAccessPatient(SecurityContextHolder.getContext().getAuthentication(), patientId)) {
			throw new AccessDeniedException("No access to patient " + patientId);
		}
	}

	public boolean hasSpecialty(Authentication authentication, String specialty) {
		return specialty.equals(currentUser(authentication).specialty());
	}

	public boolean inCenter(Authentication authentication, String centerId) {
		return centerId.equals(currentUser(authentication).centerId());
	}

	private static CurrentUser currentUser(Authentication authentication) {
		return CurrentUser.from((Jwt) authentication.getPrincipal());
	}
}
