package co.redsalud.andina.auditoria;

public enum AuditKind {
	/** Someone read a clinical resource. */
	READ,
	/** Two writers changed the same clinical field; both values are kept here. */
	FIELD_CONFLICT,
	/** An authenticated caller was refused access. Recorded for every denial, whatever the reason. */
	ACCESS_DENIED
}
