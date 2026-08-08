package dev.xkmc.youkaishomecoming.content.spell.analysis;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.serialization.JsonOps;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;

/**
 * Canonical definition hash (design doc §2.4, §15; D9).
 * <p>
 * Pipeline: legacy precheck (shared {@link SpellEligibility}, covers every
 * container incl. Burst/SpawnShooter/hook lists) → canonical encode (CODEC +
 * JsonOps) → decode (round-trip) → re-encode → structural equality of the two
 * encodings → recursive object-key sorting (map insertion order and JSON field
 * order must not affect the hash; action array order is preserved because it is
 * semantic) → SHA-256.
 * <p>
 * Only stable definitions hash; the id is part of the hash (two spells differing
 * only by id produce different certificates).
 */
public final class SpellHash {

	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

	private SpellHash() {
	}

	public static String canonicalHash(SpellDefinition definition) {
		if (SpellEligibility.hasLegacyTicker(definition)) {
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
		String canonical = GSON.toJson(sortKeys(second));
		return sha256Hex(canonical.getBytes(StandardCharsets.UTF_8));
	}

	private static JsonElement encode(SpellDefinition definition) {
		return SpellDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition)
				.result()
				.orElseThrow(() -> new SpellAnalysisException("Cannot canonical-encode definition"));
	}

	/**
	 * Recursively sort JSON object keys (TreeMap lexical order). Arrays keep their
	 * order — action list order has semantic meaning.
	 */
	private static JsonElement sortKeys(JsonElement element) {
		if (element instanceof JsonObject obj) {
			JsonObject sorted = new JsonObject();
			Map<String, JsonElement> ordered = new TreeMap<>();
			for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
				ordered.put(entry.getKey(), sortKeys(entry.getValue()));
			}
			for (Map.Entry<String, JsonElement> entry : ordered.entrySet()) {
				sorted.add(entry.getKey(), entry.getValue());
			}
			return sorted;
		}
		if (element instanceof JsonArray array) {
			JsonArray out = new JsonArray();
			for (JsonElement child : array) {
				out.add(sortKeys(child));
			}
			return out;
		}
		return element;
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
