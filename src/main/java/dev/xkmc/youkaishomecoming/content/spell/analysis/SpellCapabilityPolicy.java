package dev.xkmc.youkaishomecoming.content.spell.analysis;

/**
 * Policy values for spell capabilities (design doc §11).
 * ALLOW: always allowed (still subject to hard performance limits).
 * EXPERIMENTAL: denied by default for survival certification; a server config/script
 * may promote it to ALLOW later (Phase 6 wiring). Never allowed while in default state.
 * DENY: always denied for certification; cannot be unlocked by normal play.
 * OP_ONLY: only operators may use it; survival certification never unlocks it.
 */
public enum SpellCapabilityPolicy {

	ALLOW,
	EXPERIMENTAL,
	DENY,
	OP_ONLY;

	/**
	 * Whether a normal survival certification is allowed to use this capability
	 * under the current policy state.
	 */
	public boolean allowsCertification() {
		return this == ALLOW;
	}
}
