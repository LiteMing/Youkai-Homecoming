package dev.xkmc.youkaishomecoming.content.spell.market;

import com.google.gson.JsonElement;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellAnalysisProfile;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellAnalyzer;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;

import java.nio.charset.StandardCharsets;

/**
 * Server-side validation facade for untrusted market JSON.
 * <p>
 * Keeps the historical static signature (D10); the byte-size transport check stays
 * here, everything else delegates to the shared {@link SpellAnalyzer} with the
 * MARKET profile, preserving the previous budget constants and error messages.
 */
public class SpellMarketValidator {

	public static final int MAX_JSON_BYTES = 1024 * 1024;

	private SpellMarketValidator() {
	}

	public static void validate(String rawJson, JsonElement json, SpellDefinition definition) {
		if (rawJson.getBytes(StandardCharsets.UTF_8).length > MAX_JSON_BYTES) {
			throw new IllegalArgumentException("Spell JSON exceeds " + MAX_JSON_BYTES + " bytes");
		}
		SpellAnalyzer.analyze(definition, SpellAnalysisProfile.MARKET);
	}
}
