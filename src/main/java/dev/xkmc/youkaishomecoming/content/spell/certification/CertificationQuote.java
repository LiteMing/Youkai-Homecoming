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
		SpellAnalysis analysis,
		long issuedAtGameTime
) {
}
