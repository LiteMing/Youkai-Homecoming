package dev.xkmc.youkaishomecoming.content.spell.definition;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.JsonOps;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellAction;
import dev.xkmc.youkaishomecoming.content.spell.condition.SpellCondition;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;

/** The Raw JSON editor's content diagnostics, also used for AI revision feedback. */
public final class SpellJsonChecker {
	/** Existing Raw JSON editor capacity; also bounds a draft sent back for AI repair. */
	public static final int MAX_JSON_LENGTH = 1_048_576;

	private SpellJsonChecker() {}

	public record Result(@Nullable SpellDefinition definition, @Nullable SpellJsonSalvage.Result salvage,
			String errorKey, String detail) {
		public boolean clean() { return definition != null; }

		public String feedback() {
			if (clean()) return "";
			String message = errorKey + (detail.isBlank() ? "" : ": " + detail);
			if (salvage != null && !salvage.messages().isEmpty()) message += "\n" + String.join("\n", salvage.messages());
			return message;
		}
	}

	/** Parse and diagnose only. Never registers, certifies, or executes the definition. */
	public static Result check(String text) {
		JsonElement json = null;
		try {
			json = JsonParser.parseString(text);
			String[] parseError = new String[1];
			Optional<SpellDefinition> parsed = SpellDefinition.CODEC.parse(JsonOps.INSTANCE, json)
					.resultOrPartial(msg -> parseError[0] = msg);
			if (parsed.isEmpty()) return problem(json, text, "Invalid spell JSON", parseError[0]);
			String[] encodeError = new String[1];
			Optional<JsonElement> encoded = SpellDefinition.CODEC.encodeStart(JsonOps.INSTANCE, parsed.get())
					.resultOrPartial(msg -> encodeError[0] = msg);
			if (encoded.isEmpty()) return problem(null, text, "Invalid spell JSON", encodeError[0]);
			DroppedField dropped = findDroppedField(json, encoded.get(), "$");
			if (dropped != null) return problem(json, text,
					dropped.parseError() ? "Invalid spell JSON" : "Raw JSON has unsupported field", dropped.message());
			if (parseError[0] != null) return problem(json, text, "Invalid spell JSON", parseError[0]);
			if (SpellJsonSalvage.containsBrokenNodes(parsed.get())) {
				return problem(json, text, "Invalid spell JSON", "Unrepaired broken nodes: repair their saved raw content");
			}
			return new Result(parsed.get(), null, "", "");
		} catch (JsonSyntaxException error) {
			return problem(null, text, "Invalid JSON", error.getMessage());
		} catch (RuntimeException error) {
			return problem(json, text, "Invalid spell JSON", error.getMessage());
		}
	}

	private static Result problem(@Nullable JsonElement json, String text, String key, @Nullable String detail) {
		SpellJsonSalvage.Result salvage = null;
		if (json != null) {
			try {
				salvage = SpellJsonSalvage.salvage(json, text);
				if (salvage != null && salvage.brokenCount() == 0) salvage = null;
			} catch (RuntimeException ignored) {
				// An unreadable skeleton still leaves the original text and Codec error intact.
			}
		}
		return new Result(null, salvage, key, detail == null ? "" : detail);
	}

	private static DroppedField findDroppedField(JsonElement input, JsonElement encoded, String path) {
		if (input == null || encoded == null) {
			return null;
		}
		if (input.isJsonObject() && encoded.isJsonObject()) {
			JsonObject inObj = input.getAsJsonObject();
			JsonObject outObj = encoded.getAsJsonObject();
			for (Map.Entry<String, JsonElement> entry : inObj.entrySet()) {
				String key = entry.getKey();
				String childPath = path + "." + key;
				if (!outObj.has(key)) {
					if ("size".equals(key) && "fire_danmaku".equals(getStringField(inObj, "type"))
							&& outObj.has("base_scale") && NumberProvider.CODEC.parse(JsonOps.INSTANCE, entry.getValue()).result().isPresent()) {
						continue; // Read-compatible name; the Codec exports the effective base_scale.
					}
					if (isActionDefaultOmitted(inObj, key, entry.getValue())
							|| isConditionDefaultOmitted(outObj, key, entry.getValue())
							|| isCodecDefaultOmitted(childPath, entry.getValue())) {
						continue;
					}
					DroppedField parseError = diagnoseDroppedActionList(entry.getValue(), childPath);
					return parseError != null ? parseError : DroppedField.unsupported(childPath);
				}
				DroppedField child = findDroppedField(entry.getValue(), outObj.get(key), childPath);
				if (child != null) {
					return child;
				}
			}
		} else if (input.isJsonArray() && encoded.isJsonArray()) {
			JsonArray inArray = input.getAsJsonArray();
			JsonArray outArray = encoded.getAsJsonArray();
			for (int i = 0; i < inArray.size(); i++) {
				String childPath = path + "[" + i + "]";
				if (i >= outArray.size()) {
					if (isCodecDefaultOmitted(childPath, inArray.get(i))) {
						continue;
					}
					return DroppedField.unsupported(childPath);
				}
				DroppedField child = findDroppedField(inArray.get(i), outArray.get(i), childPath);
				if (child != null) {
					return child;
				}
			}
		}
		return null;
	}

	private static DroppedField diagnoseDroppedActionList(JsonElement input, String path) {
		if (!isActionListPath(path) || !input.isJsonArray()) {
			return null;
		}
		JsonArray actions = input.getAsJsonArray();
		for (int i = 0; i < actions.size(); i++) {
			String actionPath = path + "[" + i + "]";
			DroppedField child = diagnoseAction(actions.get(i), actionPath);
			if (child != null) {
				return child;
			}
		}
		return null;
	}

	private static DroppedField diagnoseAction(JsonElement action, String path) {
		String[] error = new String[1];
		Optional<SpellAction> parsed = SpellAction.CODEC.parse(JsonOps.INSTANCE, action)
				.resultOrPartial(msg -> error[0] = msg);
		if (parsed.isEmpty()) {
			DroppedField child = diagnoseActionChildren(action, path);
			if (child != null) {
				return child;
			}
			String detail = path;
			if (error[0] != null && !error[0].isBlank()) {
				detail += ": " + error[0];
			}
			return DroppedField.parseError(detail);
		}
		return diagnoseActionChildren(action, path);
	}

	private static DroppedField diagnoseActionChildren(JsonElement action, String path) {
		if (!action.isJsonObject()) {
			return null;
		}
		JsonObject object = action.getAsJsonObject();
		String type = getStringField(object, "type");
		if ("conditional".equals(type)) {
			if (object.has("condition")) {
				DroppedField condition = diagnoseCondition(object.get("condition"), path + ".condition");
				if (condition != null) {
					return condition;
				}
			}
			DroppedField ifTrue = diagnoseActionListField(object, "if_true", path + ".if_true");
			if (ifTrue != null) {
				return ifTrue;
			}
			return diagnoseActionListField(object, "if_false", path + ".if_false");
		}
		if ("sequence".equals(type)) {
			return diagnoseActionListField(object, "actions", path + ".actions");
		}
		if ("repeat".equals(type) || "delay".equals(type) || "burst".equals(type) || "spawn_shooter".equals(type)) {
			return diagnoseActionListField(object, "body", path + ".body");
		}
		if ("hold_source".equals(type)) return diagnoseActionListField(object, "on_release", path + ".on_release");
		if ("fire_danmaku".equals(type) || "fire_laser".equals(type)) {
			for (String key : new String[]{"on_expiry", "on_trail", "on_hit_entity", "on_hit_block"}) {
				DroppedField child = diagnoseActionListField(object, key, path + "." + key);
				if (child != null) {
					return child;
				}
			}
		}
		if ("disabled".equals(type) && object.has("inner")) {
			return diagnoseAction(object.get("inner"), path + ".inner");
		}
		return null;
	}

	private static DroppedField diagnoseActionListField(JsonObject object, String key, String path) {
		if (!object.has(key)) {
			return null;
		}
		JsonElement value = object.get(key);
		if (!value.isJsonArray()) {
			return DroppedField.parseError(path + ": expected JSON array");
		}
		return diagnoseDroppedActionList(value, path);
	}

	private static DroppedField diagnoseCondition(JsonElement condition, String path) {
		String[] error = new String[1];
		Optional<SpellCondition> parsed = SpellCondition.CODEC.parse(JsonOps.INSTANCE, condition)
				.resultOrPartial(msg -> error[0] = msg);
		if (condition.isJsonObject()) {
			JsonObject object = condition.getAsJsonObject();
			String type = getStringField(object, "type");
			if (("and".equals(type) || "or".equals(type)) && object.has("conditions")) {
				JsonElement conditions = object.get("conditions");
				if (!conditions.isJsonArray()) {
					return DroppedField.parseError(path + ".conditions: expected JSON array");
				}
				JsonArray array = conditions.getAsJsonArray();
				for (int i = 0; i < array.size(); i++) {
					DroppedField child = diagnoseCondition(array.get(i), path + ".conditions[" + i + "]");
					if (child != null) {
						return child;
					}
				}
			}
			if ("not".equals(type) && object.has("condition")) {
				DroppedField child = diagnoseCondition(object.get("condition"), path + ".condition");
				if (child != null) {
					return child;
				}
			}
		}
		if (parsed.isEmpty()) {
			String detail = path;
			if (error[0] != null && !error[0].isBlank()) {
				detail += ": " + error[0];
			}
			return DroppedField.parseError(detail);
		}
		return null;
	}

	private static String getStringField(JsonObject object, String key) {
		JsonElement value = object.get(key);
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
				? value.getAsString() : "";
	}

	private static boolean isActionListPath(String path) {
		return path.endsWith(".on_enter") || path.endsWith(".on_tick") ||
				path.endsWith(".on_exit") || path.endsWith(".on_damage") ||
				path.endsWith(".if_true") || path.endsWith(".if_false") ||
				path.endsWith(".actions") || path.endsWith(".body") ||
				path.endsWith(".on_expiry") || path.endsWith(".on_trail") ||
				path.endsWith(".on_hit_entity") || path.endsWith(".on_hit_block") || path.endsWith(".on_release");
	}

	private static boolean isActionDefaultOmitted(JsonObject object, String key, JsonElement value) {
		String type = getStringField(object, "type");
		if ("spawn_shooter".equals(type)) return switch (key) {
			case "damage" -> isNumber(value, 4);
			case "health" -> isNumber(value, 40);
			case "lifetime" -> isNumber(value, 100);
			case "targetable" -> isBoolean(value, true);
			default -> false;
		};
		if ("bounce_source".equals(type) || "bounce".equals(type)) return switch (key) {
			case "max_bounces", "tangent_factor" -> isNumber(value, 1);
			case "normal_factor" -> isNumber(value, -1);
			case "tangent_offset_x", "tangent_offset_y", "tangent_offset_z" -> isNumber(value, 0);
			case "retarget" -> isBoolean(value, false);
			default -> false;
		};
		return false;
	}

	private static boolean isConditionDefaultOmitted(JsonObject encoded, String key, JsonElement value) {
		// The same condition can appear under .condition or any depth of .conditions[index].
		return switch (getStringField(encoded, "type")) {
			case "tick_interval" -> "offset".equals(key) && isNumber(value, 0);
			case "dynamic_tick_interval" -> "offset".equals(key) && isZeroNumberProvider(value);
			case "always" -> "value".equals(key) && isBoolean(value, true);
			// A fieldless NumberProvider shares this type ID; only the condition has a threshold.
			case "target_speed" -> encoded.has("threshold") && "op".equals(key) && isString(value, ">");
			default -> false;
		};
	}

	private static boolean isZeroNumberProvider(JsonElement value) {
		if (value.isJsonObject()) {
			JsonObject object = value.getAsJsonObject();
			// A typed constant also encodes to a bare number; don't hide extra fields inside it.
			if (object.size() != 2 || !"constant".equals(getStringField(object, "type")) || !object.has("value")) return false;
		}
		return NumberProvider.CODEC.parse(JsonOps.INSTANCE, value).result()
				.filter(NumberProvider.constant(0)::equals).isPresent();
	}

	private static boolean isCodecDefaultOmitted(String path, JsonElement value) {
		if (value == null || value.isJsonNull()) {
			return false;
		}
		if (value.isJsonArray() && value.getAsJsonArray().isEmpty() && isActionListPath(path)) {
			return true;
		}
		if (path.endsWith(".transitions") && value.isJsonArray() && value.getAsJsonArray().isEmpty()) {
			return true;
		}
		if (path.endsWith(".difficulty") && isDefaultDifficulty(value)) {
			return true;
		}
		if (path.endsWith(".item_form") && isDefaultItemForm(value)) {
			return true;
		}
		if (endsWithAny(path, ".difficulty.speed_base", ".difficulty.frequency_base", ".difficulty.count_base")) {
			return isNumber(value, 1);
		}
		if (endsWithAny(path, ".difficulty.speed_per_health_lost", ".difficulty.frequency_per_health_lost",
				".difficulty.count_per_health_lost")) {
			return isNumber(value, 0);
		}
		if (endsWithAny(path, ".origin.offset_x", ".origin.offset_y", ".origin.offset_z",
				".origin.rotation", ".destination.offset_x", ".destination.offset_y", ".destination.offset_z",
				".destination.rotation", ".angle_offset", ".elevation", ".group_rotation.rot_x",
				".group_rotation.rot_y", ".group_rotation.rot_z")) {
			return isNumberOrNumericString(value, 0);
		}
		if (endsWithAny(path, ".mover.x", ".mover.y", ".mover.z", ".mover.speed")) {
			return isNumberOrNumericString(value, 0) || isString(value, "0");
		}
		if (path.endsWith(".spread")) {
			return isNumberOrNumericString(value, 360);
		}
		if (path.endsWith(".length")) {
			return isNumberOrNumericString(value, 80);
		}
		if (path.endsWith(".pattern")) {
			return isString(value, "ring");
		}
		if (path.endsWith(".aim_mode")) {
			return isString(value, "target");
		}
		if (endsWithAny(path, ".origin.mode", ".destination.mode")) {
			return isString(value, "caster");
		}
		if (endsWithAny(path, ".trail_interval", ".color.interval", ".volume", ".pitch", ".size")) {
			return isNumberOrNumericString(value, 1);
		}
		if (endsWithAny(path, ".hit_behavior_entity", ".hit_behavior_block")) {
			return isString(value, "continue");
		}
		if (path.endsWith(".laser")) {
			return isString(value, "laser");
		}
		if (path.endsWith(".index_variable")) {
			return isString(value, "i");
		}
		if (path.endsWith(".if_false")) {
			return isNumberOrNumericString(value, 0);
		}
		if (endsWithAny(path, ".item_form.generate", ".item_form.requires_target", ".item_form.caster_moves", ".item_form.ex_spell")) {
			return isBoolean(value, false);
		}
		if (path.endsWith(".item_form.card_type")) return isString(value, "normal");
		if (endsWithAny(path, ".item_form.duration", ".item_form.hp")) return isNumber(value, 0);
		if (path.endsWith(".custom_names")) return value.isJsonObject() && value.getAsJsonObject().size() == 0;
		if (path.endsWith(".random_axis")) return isBoolean(value, true);
		if (path.endsWith(".item_form.cooldown")) {
			return isNumber(value, 100);
		}
		if (path.endsWith(".mover.aim")) {
			return isString(value, "none");
		}
		return false;
	}

	private static boolean endsWithAny(String path, String... suffixes) {
		for (String suffix : suffixes) {
			if (path.endsWith(suffix)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isDefaultDifficulty(JsonElement value) {
		if (!value.isJsonObject()) {
			return false;
		}
		JsonObject obj = value.getAsJsonObject();
		for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
			String key = entry.getKey();
			boolean ok = switch (key) {
				case "speed_base", "frequency_base", "count_base" -> isNumber(entry.getValue(), 1);
				case "speed_per_health_lost", "frequency_per_health_lost", "count_per_health_lost" ->
						isNumber(entry.getValue(), 0);
				default -> false;
			};
			if (!ok) {
				return false;
			}
		}
		return true;
	}

	private static boolean isDefaultItemForm(JsonElement value) {
		if (!value.isJsonObject()) {
			return false;
		}
		JsonObject obj = value.getAsJsonObject();
		for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
			String key = entry.getKey();
			boolean ok = switch (key) {
				case "generate", "requires_target", "caster_moves", "ex_spell" -> isBoolean(entry.getValue(), false);
				case "cooldown" -> isNumber(entry.getValue(), 0) || isNumber(entry.getValue(), 100);
				case "duration", "hp" -> isNumber(entry.getValue(), 0);
				case "card_type" -> isString(entry.getValue(), "normal");
				default -> false;
			};
			if (!ok) {
				return false;
			}
		}
		return true;
	}

	private static boolean isString(JsonElement value, String expected) {
		return value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() &&
				expected.equals(value.getAsString());
	}

	private static boolean isBoolean(JsonElement value, boolean expected) {
		return value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() &&
				value.getAsBoolean() == expected;
	}

	private static boolean isNumber(JsonElement value, double expected) {
		return value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() &&
				Double.compare(value.getAsDouble(), expected) == 0;
	}

	private static boolean isNumberOrNumericString(JsonElement value, double expected) {
		if (isNumber(value, expected)) {
			return true;
		}
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
			return false;
		}
		try {
			return Double.compare(Double.parseDouble(value.getAsString().trim()), expected) == 0;
		} catch (NumberFormatException ignored) {
			return false;
		}
	}

	private record DroppedField(String message, boolean parseError) {
		private static DroppedField unsupported(String path) {
			return new DroppedField(path, false);
		}

		private static DroppedField parseError(String message) {
			return new DroppedField(message, true);
		}
	}
}
