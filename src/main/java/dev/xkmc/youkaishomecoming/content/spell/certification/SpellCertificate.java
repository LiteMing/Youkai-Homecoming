package dev.xkmc.youkaishomecoming.content.spell.certification;

import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellCapability;

import java.util.Set;
import java.util.UUID;

/**
 * Immutable spell certificate (design doc §15). Bound to the definition hash:
 * modifying the definition invalidates the certificate. Written to
 * {@code <world>/youkaishomecoming_certified_spells/<hash>.json} together with
 * the canonical definition JSON.
 */
public record SpellCertificate(
		UUID certificateId,
		String definitionHash,
		UUID authorId,
		String authorName,
		int certifiedDuration,
		double certifiedArenaHalfSize,
		String arenaShape,
		long costUnits,
		Set<SpellCapability> capabilities,
		/** Draft special-node quota used by normal survival certification. */
		int specialNodeQuota,
		/** True only for rewards issued by the OP-only /yhdev certification path. */
		boolean operatorTest,
		/** 1 = definitionHash covers the frozen set_spell_health dependency closure. */
		int healthPlanVersion,
		int analysisVersion,
		int certificationRulesVersion,
		long issuedAtGameTime
) {

	public static final int CURRENT_ANALYSIS_VERSION = 1;
	public static final int CURRENT_RULES_VERSION = 1;
	public static final int CURRENT_HEALTH_PLAN_VERSION = 1;
}
