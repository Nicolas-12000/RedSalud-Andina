package co.redsalud.andina.auditoria;

public enum AuditKind {
	/** Someone read a clinical resource. */
	READ,
	/** Two writers changed the same clinical field; both values are kept here. */
	FIELD_CONFLICT
}
