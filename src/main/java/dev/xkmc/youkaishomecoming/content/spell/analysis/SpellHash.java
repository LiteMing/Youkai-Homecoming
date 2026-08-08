package dev.xkmc.youkaishomecoming.content.spell.analysis;

import com.google.gson.JsonElement;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.serialization.JsonOps;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Canonical definition hash (design doc §2.4, §15; D9).
 * <p>
 * Pipeline: legacy precheck → canonical encode (CODEC + JsonOps) → decode
 * (round-trip) → re-encode → structural equality of the two encodings.
 * Only stable definitions hash; the id is part of the hash (two spells differing
 * only by id produce different certificates).
 */
public final class SpellHash {

	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

	private SpellHash() {
	}

	public static String canonicalHash(SpellDefinition definition) {
		if (definition.hasLegacyTicker()) {
			throw new SpellAnalysisException("Cannot hash definition containing legacy_ticker actions");
		}
		JsonElement first = encode(definition);
		SpellDefinition decoded = SpellDefinition.CODEC.parse(JsonOps.INSTANCE, first)
				.result()
				.orElseThrow(() -> new SpellAnalysisException("Definition is not round-trippable (decode failed)"));
		JsonElement second = encode(decoded);
		if (!first.equals(second)) {
			throw new SpellAnalysisException("Definition JSON is not stable across encode/decode");
		}
		String canonical = GSON.toJson(second);
		return sha256Hex(canonical.getBytes(StandardCharsets.UTF_8));
	}

	private static JsonElement encode(SpellDefinition definition) {
		return SpellDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition)
				.result()
				.orElseThrow(() -> new SpellAnalysisException("Cannot canonical-encode definition"));
	}

	private static String sha256Hex(byte[] bytes) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(bytes);
			StringBuilder sb = new StringBuilder(hash.length * 2);
			for (byte b : hash) {
				sb.append(Character.forDigit((b >> 4) & 0xF, 16));
				sb.append(Character.forDigit(b & 0xF, 16));
			}
			return sb.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 unavailable", e);
		}
	}
}
