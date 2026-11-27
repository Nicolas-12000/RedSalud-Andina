package co.redsalud.andina.identidad;

import java.util.Arrays;
import java.util.Optional;

/** Roles of the platform. {@code cognitoGroup} is the Cognito group name that maps to each role. */
public enum Role {
	PATIENT("patient"),
	RURAL_DOCTOR("rural-doctor"),
	SPECIALIST("specialist"),
	AUDITOR("auditor"),
	NETWORK_MANAGER("network-manager");

	private final String cognitoGroup;

	Role(String cognitoGroup) {
		this.cognitoGroup = cognitoGroup;
	}

	public String cognitoGroup() {
		return cognitoGroup;
	}

	public String authority() {
		return "ROLE_" + name();
	}

	static Optional<Role> fromCognitoGroup(String group) {
		return Arrays.stream(values()).filter(role -> role.cognitoGroup.equals(group)).findFirst();
	}
}
