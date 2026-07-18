package dev.xkmc.youkaishomecoming.content.spell.market;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Server-side validation and amplification budgets for untrusted market JSON. */
public class SpellMarketValidator {

	public static final int MAX_JSON_BYTES = 1024 * 1024;
	private static final int MAX_PHASES = 64;
	private static final int MAX_ACTIONS = 4096;
	private static final int MAX_DEPTH = 32;
	private static final int MAX_REPEAT = 256;
	private static final int MAX_PROJECTILES = 8192;
	private static final int MAX_SHOOTERS = 256;
	private static final int MAX_LIFETIME = 12000;
	private static final int MAX_EXPRESSION_LENGTH = 512;
	private static final Set<String> BANNED_ACTIONS = Set.of("run_command", "force_spell", "fire_spell");

	private int actions;
	private long projectiles;
	private long shooters;

	public static void validate(String rawJson, JsonElement json, SpellDefinition definition) {
		if (rawJson.getBytes(StandardCharsets.UTF_8).length > MAX_JSON_BYTES) {
			throw new IllegalArgumentException("Spell JSON exceeds " + MAX_JSON_BYTES + " bytes");
		}
		if (definition.phases.isEmpty() || definition.phases.size() > MAX_PHASES) {
			throw new IllegalArgumentException("Spell phase count must be between 1 and " + MAX_PHASES);
		}
		if (!definition.phases.containsKey(definition.entryPhase)) {
			throw new IllegalArgumentException("Entry phase is not present in phases: " + definition.entryPhase);
		}
		definition.phases.forEach((id, phase) -> {
			if (!id.equals(phase.id)) {
				throw new IllegalArgumentException("Phase key/id mismatch: " + id + " != " + phase.id);
			}
		});
		SpellMarketValidator validator = new SpellMarketValidator();
		validator.visit(json, 0, 1);
		if (validator.actions > MAX_ACTIONS) {
			throw new IllegalArgumentException("Spell contains too many actions: " + validator.actions);
		}
		if (validator.projectiles > MAX_PROJECTILES) {
			throw new IllegalArgumentException("Spell projectile budget exceeds " + MAX_PROJECTILES);
		}
		if (validator.shooters > MAX_SHOOTERS) {
			throw new IllegalArgumentException("Spell shooter budget exceeds " + MAX_SHOOTERS);
		}
	}

	private void visit(JsonElement element, int depth, long multiplier) {
		if (depth > MAX_DEPTH) throw new IllegalArgumentException("Spell nesting exceeds " + MAX_DEPTH);
		if (element == null || element.isJsonNull()) return;
		if (element instanceof JsonPrimitive primitive) {
			if (primitive.isString() && primitive.getAsString().length() > MAX_EXPRESSION_LENGTH) {
				throw new IllegalArgumentException("Spell string/expression exceeds " + MAX_EXPRESSION_LENGTH + " characters");
			}
			return;
		}
		if (element instanceof JsonArray array) {
			for (JsonElement child : array) visit(child, depth + 1, multiplier);
			return;
		}
		JsonObject object = element.getAsJsonObject();
		String type = stringValue(object.get("type"));
		long childMultiplier = multiplier;
		if (type != null) {
			actions++;
			if (BANNED_ACTIONS.contains(type)) {
				throw new IllegalArgumentException("Automatic market imports may not use action: " + type);
			}
			if (type.equals("repeat") || type.equals("burst")) {
				long count = boundedLiteral(object.get("count"), type + " count", MAX_REPEAT);
				childMultiplier = multiply(multiplier, count);
			}
			if (type.equals("fire_danmaku") || type.equals("fire_laser") || type.equals("fire_text_danmaku")) {
				long count = boundedLiteral(object.get("count"), type + " count", MAX_REPEAT);
				projectiles = add(projectiles, multiply(multiplier, count));
			}
			if (type.equals("spawn_shooter")) {
				long count = boundedLiteral(object.get("count"), "shooter count", MAX_REPEAT);
				shooters = add(shooters, multiply(multiplier, count));
				childMultiplier = multiply(multiplier, count);
			}
		}
		for (var entry : object.entrySet()) {
			String key = entry.getKey();
			JsonElement value = entry.getValue();
			if ((key.equals("lifetime") || key.equals("duration") || key.equals("health")) && value.isJsonPrimitive()
					&& value.getAsJsonPrimitive().isNumber() && value.getAsDouble() > MAX_LIFETIME) {
				throw new IllegalArgumentException(key + " exceeds " + MAX_LIFETIME);
			}
			visit(value, depth + 1, childMultiplier);
		}
	}

	private static long boundedLiteral(JsonElement value, String label, int max) {
		if (value == null) return 1;
		if (!value.isJsonPrimitive()) {
			throw new IllegalArgumentException(label + " must be a bounded numeric literal for automatic imports");
		}
		JsonPrimitive primitive = value.getAsJsonPrimitive();
		try {
			long count = primitive.isNumber() ? primitive.getAsLong() : Long.parseLong(primitive.getAsString());
			if (count < 0 || count > max) throw new IllegalArgumentException(label + " exceeds " + max);
			return count;
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(label + " must be a bounded numeric literal");
		}
	}

	private static String stringValue(JsonElement value) {
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
				? value.getAsString() : null;
	}

	private static long multiply(long a, long b) {
		if (a == 0 || b == 0) return 0;
		if (a > MAX_PROJECTILES / Math.max(1, b)) return MAX_PROJECTILES + 1L;
		return a * b;
	}

	private static long add(long a, long b) {
		return Math.min(MAX_PROJECTILES + 1L, a + b);
	}
}
