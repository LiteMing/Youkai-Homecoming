package dev.xkmc.youkaishomecoming.content.spell.certification;

import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellAnalysis;

/**
 * Firm server quote for one certification attempt (design doc §5.2, §18).
 * quoteId must be echoed back on start so the client cannot swap definitions.
 * <p>
 * Cost model (Phase 7, built-in-spell baseline): {@code baseCostUnits} (default
 * 100 = 5 XP levels / 1 bomb) is the reference for the weakest built-in spells;
 * {@code castCostUnits} = base × logarithmic power multiplier. startCostUnits is
 * the fixed anti-spam toll; issueCostUnits is what a successful certification
 * charges when the issue fee is enabled.
 */
public record CertificationQuote(
		String quoteId,
		String definitionHash,
		int durationTicks,
		double arenaHalfSize,
		long startCostUnits,
		long issueCostUnits,
		long castCostUnits,
		/** Final cast duration of the certified reward item (curve 1/3 → 1/10 of the break HP). */
		int rewardDurationTicks,
		/** Break HP of the certification enemy in seconds (each player hit reduces it by 1s). */
		int breakHpSeconds,
		/** Draft op-node quota that allowed run_command nodes in this certification (0 = none). */
		int opNodeQuota,
		SpellAnalysis analysis,
		long issuedAtGameTime
) {
}
