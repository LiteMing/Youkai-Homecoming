package dev.xkmc.youkaishomecoming.content.spell.certification;

/**
 * Certification state machine (design doc §7). Server-authoritative; the client
 * only projects {@link CertificationStateToClient}.
 */
public enum CertificationState {
	DRAFT,
	QUOTED,
	DEPOSIT_PAID,
	PREPARE,
	ACTIVE,
	SUCCESS,
	FAILED,
	ABORTED,
	SYSTEM_ERROR
}
