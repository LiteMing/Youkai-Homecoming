package dev.xkmc.youkaishomecoming.content.spell.analysis;

/**
 * Analysis profiles (design doc §10): MARKET preserves the historical
 * SpellMarketValidator budgets and behavior; CERTIFICATION applies the
 * conservative certification hard limits and capability policy.
 */
public enum SpellAnalysisProfile {
	MARKET,
	CERTIFICATION
}
