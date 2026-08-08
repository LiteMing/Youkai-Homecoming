package dev.xkmc.youkaishomecoming.content.spell.certification;

import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellAnalysis;

/**
 * Firm server quote for one certification attempt (design doc §5.2, §18).
 * quoteId must be echoed back on start so the client cannot swap definitions.
 */
public record CertificationQuote(
		String quoteId,
		String definitionHash,
		int durationTicks,
		double arenaHalfSize,
		long startCostUnits,
		long issueCostUnits,
		SpellAnalysis analysis,
		long issuedAtGameTime
) {
}
