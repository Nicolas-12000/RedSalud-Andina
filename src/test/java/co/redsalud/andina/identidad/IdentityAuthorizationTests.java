package co.redsalud.andina.identidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(IdentityAuthorizationTests.ProtectedPatientData.class)
class IdentityAuthorizationTests {

	@Autowired CareRelationships careRelationships;
	@Autowired ProtectedPatientData protectedData;

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void mapsCognitoGroupAndAttributes() {
		UUID subject = UUID.randomUUID();

		CurrentUser user = CurrentUser.from(token(subject, "specialist", "center-1", "cardiology"));

		assertThat(user).isEqualTo(new CurrentUser(subject, Role.SPECIALIST, "center-1", "cardiology"));
	}

	@Test
	void convertsGroupsToAuthorities() {
		var authentication = new CognitoJwtConverter().convert(token(UUID.randomUUID(), "rural-doctor", null, null));

		assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("ROLE_RURAL_DOCTOR");
	}

	@Test
	void rejectsTokenWithoutRecognizedRole() {
		Jwt unknown = token(UUID.randomUUID(), "unknown-group", null, null);

		assertThatThrownBy(() -> CurrentUser.from(unknown)).isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void patientReadsOnlyOwnData() {
		UUID patientId = UUID.randomUUID();
		authenticateAs(token(patientId, "patient", null, null));

		assertThat(protectedData.read(patientId)).isEqualTo("record");
		assertThatThrownBy(() -> protectedData.read(UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void professionalReadsOnlyPatientsWithRelationship() {
		UUID doctorId = UUID.randomUUID();
		UUID linkedPatient = UUID.randomUUID();
		careRelationships.grant(doctorId, linkedPatient);
		careRelationships.grant(doctorId, linkedPatient); // idempotent
		authenticateAs(token(doctorId, "rural-doctor", "center-1", null));

		assertThat(protectedData.read(linkedPatient)).isEqualTo("record");
		assertThatThrownBy(() -> protectedData.read(UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void specialtyAttributeIsEnforcedAtTheDataAccessPoint() {
		authenticateAs(token(UUID.randomUUID(), "specialist", "center-1", "cardiology"));
		assertThat(protectedData.cardiologyOnly()).isEqualTo("ok");

		authenticateAs(token(UUID.randomUUID(), "specialist", "center-1", "dermatology"));
		assertThatThrownBy(protectedData::cardiologyOnly).isInstanceOf(AccessDeniedException.class);
	}

	private static Jwt token(UUID subject, String group, String center, String specialty) {
		return Jwt.withTokenValue("t").header("alg", "none").subject(subject.toString())
				.claim("cognito:groups", List.of(group))
				.claims(claims -> {
					if (center != null) claims.put("custom:center", center);
					if (specialty != null) claims.put("custom:specialty", specialty);
				}).build();
	}

	private void authenticateAs(Jwt jwt) {
		SecurityContextHolder.getContext().setAuthentication(new CognitoJwtConverter().convert(jwt));
	}

	@Service
	static class ProtectedPatientData {

		@PreAuthorize("@accessPolicy.canAccessPatient(authentication, #patientId)")
		public String read(UUID patientId) {
			return "record";
		}

		@PreAuthorize("@accessPolicy.hasSpecialty(authentication, 'cardiology')")
		public String cardiologyOnly() {
			return "ok";
		}
	}
}
