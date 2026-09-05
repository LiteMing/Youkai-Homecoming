package dev.xkmc.youkaishomecoming.content.spell.certification;

import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellAnalysis;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellHealthPlan;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellDraftBudget;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpecialNodeCounter;

/**
 * Firm server quote for one certification attempt (design doc §5.2, §18).
 * quoteId must be echoed back on start so the client cannot swap definitions.
 * <p>
 * The spell-health plan supplies HP and timeout. Cast cost follows the timeout
 * duration, while startCostUnits remains a fixed anti-spam toll.
 */
public record CertificationQuote(
		String quoteId,
		String definitionHash,
		int durationTicks,
		double arenaHalfSize,
		long startCostUnits,
		long issueCostUnits,
		long castCostUnits,
		/** Final cast duration of the certified reward item (1:1 with the planned timeout). */
		int rewardDurationTicks,
		/** Total HP of every segment on the successful break chain. */
		int spellHp,
		/** Draft special-node quota that allowed experimental nodes in this certification (0 = none). */
		int specialNodeQuota,
		/** Frozen unfinished-card limits used for this quote and final certificate. */
		SpellDraftBudget draftBudget,
		/** Full health-plan node closure used for cost and editor feedback. */
		SpecialNodeCounter.Summary nodeSummary,
		/** True only for certificates produced by the OP-only /yhdev test path. */
		boolean operatorTest,
		/** Frozen phase/spell dependency closure used by trial and reward runtime. */
		SpellHealthPlan healthPlan,
		SpellAnalysis analysis,
		/** Effective danmaku-per-tick budget at quote time (tier + caster power). */
		int maxSpawnPerTickBudget,
		long issuedAtGameTime
) {
}
