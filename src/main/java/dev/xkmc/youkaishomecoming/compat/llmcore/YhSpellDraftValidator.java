package dev.xkmc.youkaishomecoming.compat.llmcore;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpecialNodeCounter;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellAnalysis;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellAnalysisLimits;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellAnalyzer;
import dev.xkmc.youkaishomecoming.content.spell.analysis.CertificationActionPlacement;
import dev.xkmc.youkaishomecoming.content.spell.analysis.NonSpellValidator;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellCardRank;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellCardType;

import java.util.List;

/**
 * Server-side gate for JSON returned by an external generator. This class does
 * not save, register, certify, or grant a card; callers must explicitly perform
 * those actions after presenting the draft to the player.
 */
public final class YhSpellDraftValidator {

	public enum Status {
		GENERATED,
		PARSE_FAILED,
		CODEC_FAILED,
		ANALYSIS_FAILED,
		POLICY_DENIED,
		NEEDS_USER_CONFIRMATION
	}

	public record Result(Status status, String normalizedJson, SpellDefinition definition,
			SpellAnalysis analysis, SpecialNodeCounter.Summary nodes, List<String> diagnostics) {
		public boolean acceptedForPreview() {
			return status == Status.NEEDS_USER_CONFIRMATION && definition != null && analysis != null;
		}
	}

	private YhSpellDraftValidator() {
	}

	/** Parse and analyze at certification limits, without mutating any server state. */
	public static Result validate(String rawJson) {
		if (rawJson == null || rawJson.isBlank()) {
			return failure(Status.PARSE_FAILED, "empty JSON");
		}
		final JsonElement json;
		try {
			json = JsonParser.parseString(rawJson);
		} catch (RuntimeException e) {
			return failure(Status.PARSE_FAILED, message(e));
		}
		final SpellDefinition definition;
		try {
			definition = SpellDefinition.CODEC.parse(JsonOps.INSTANCE, json)
					.getOrThrow(false, error -> { });
		} catch (RuntimeException e) {
			return failure(Status.CODEC_FAILED, message(e));
		}
		SpecialNodeCounter.Summary nodes = SpecialNodeCounter.summarize(definition);
		final SpellAnalysis analysis;
		try {
			analysis = SpellAnalyzer.analyze(definition, dev.xkmc.youkaishomecoming.content.spell.analysis.SpellAnalysisProfile.CERTIFICATION,
					SpellAnalysisLimits.certification());
			CertificationActionPlacement.validate(definition);
			if (definition.itemForm.cardType() == SpellCardType.NON_SPELL) {
				NonSpellValidator.validate(definition, SpellCardRank.LESSER_WISDOM);
			}
		} catch (IllegalArgumentException e) {
			Status status = nodes.operatorOnlyNodes() > 0 || nodes.deniedNodes() > 0 || nodes.brokenNodes() > 0
					? Status.POLICY_DENIED : Status.ANALYSIS_FAILED;
			return new Result(status, json.toString(), definition, null, nodes, List.of(message(e)));
		}
		if (nodes.operatorOnlyNodes() > 0 || nodes.deniedNodes() > 0 || nodes.brokenNodes() > 0) {
			return new Result(Status.POLICY_DENIED, json.toString(), definition, analysis, nodes,
					List.of("operator-only, denied, or broken nodes require explicit server authorization"));
		}
		return new Result(Status.NEEDS_USER_CONFIRMATION, json.toString(), definition, analysis, nodes,
				List.of("draft parsed and analyzed; user confirmation is required before save/certification"));
	}

	private static Result failure(Status status, String diagnostic) {
		return new Result(status, "", null, null, null, List.of(diagnostic));
	}

	private static String message(Throwable error) {
		String message = error.getMessage();
		return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
	}
}
