package dev.xkmc.youkaishomecoming.content.spell.analysis;

import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;

/** Ordinary spell checks, with the non-spell rank/Power spawn allowance. */
public final class NonSpellValidator {
	private NonSpellValidator() {}

	public static void validate(SpellDefinition definition, SpellCardRank rank) {
		validate(definition, rank, 0);
	}

	public static void validate(SpellDefinition definition, SpellCardRank rank, double power) {
		if (definition == null) throw new SpellAnalysisException("Non-spell definition is missing");
		if (rank == null) rank = SpellCardRank.LESSER_WISDOM;
		SpellAnalysisLimits limits = SpellAnalysisLimits.certification()
				.withMaxSpawnPerTick(rank.danmakuPerTick(power));
		SpellAnalyzer.analyzePlayerCast(definition, limits, power);
	}

	public static void validateForPlayer(SpellDefinition definition, SpellCardRank rank) {
		validate(definition, rank);
	}

	public static void validateForPlayer(SpellDefinition definition, SpellCardRank rank, double power) {
		validate(definition, rank, power);
	}

	public static void validateForPlayer(SpellDefinition definition, SpellCardRank rank, double power, int permissionLevel) {
		if (definition == null) throw new SpellAnalysisException("Non-spell definition is missing");
		if (rank == null) rank = SpellCardRank.LESSER_WISDOM;
		SpellAnalyzer.analyzePlayerCast(definition, SpellAnalysisLimits.certification()
				.withMaxSpawnPerTick(rank.danmakuPerTick(power)), power, permissionLevel);
	}
}
