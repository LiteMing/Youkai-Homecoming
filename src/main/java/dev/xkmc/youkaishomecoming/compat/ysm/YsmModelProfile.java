package dev.xkmc.youkaishomecoming.compat.ysm;

import com.google.gson.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Portable YH data, not an OYSM model file. Shared by SavedData, scripts and the editor. */
public record YsmModelProfile(String model, Map<String, Preset> presets, Map<Trigger, String> triggers) {

	public static final int FORMAT = 1;
	public static final int MAX_JSON_LENGTH = 24_000;
	public static final int MAX_PRESETS = 128;
	private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	public enum Trigger {
		IDLE, WALK, FLY, NORMAL_COMBAT, STG_COMBAT, ENTER_COMBAT, SPELL_SWITCH, MELEE_ATTACK, HURT,
		BOSS_VICTORY, DEFEAT, FALLING, PRONE;

		public String id() { return name().toLowerCase(Locale.ROOT); }
		public boolean event() { return this == ENTER_COMBAT || this == SPELL_SWITCH || this == MELEE_ATTACK || this == HURT || this == BOSS_VICTORY; }
		public boolean combatCondition() { return this == NORMAL_COMBAT || this == STG_COMBAT; }
		public boolean beaten() { return this == DEFEAT || this == FALLING || this == PRONE; }
		public static Trigger parse(String id) { return valueOf(id.toUpperCase(Locale.ROOT)); }
	}

	public record Preset(String description, String clip, int ticks, Map<String, Float> parameters) {
		public Preset {
			Objects.requireNonNull(description);
			if (description.length() > 256 || ticks < 0 || parameters.size() > YsmPresentationState.WIRE_MAX_PARAMETERS)
				throw new IllegalArgumentException("Preset description, duration or parameter count exceeds its bound");
			clip = clip == null || clip.isBlank() ? "" : YsmPresentationState.normalizeClip(clip);
			Map<String, Float> copy = new LinkedHashMap<>();
			parameters.forEach((key, value) -> {
				if (value == null || !Float.isFinite(value) || Math.abs(value) > YsmPresentationState.WIRE_MAX_PARAMETER_VALUE)
					throw new IllegalArgumentException("Invalid numeric preset parameter: " + key);
				String name = YsmPresentationState.normalizeParameter(key);
				if (copy.put(name, value) != null) throw new IllegalArgumentException("Duplicate parameter: " + name);
			});
			parameters = Collections.unmodifiableMap(copy);
		}
	}

	public YsmModelProfile {
		model = modelId(model);
		if (presets.size() > MAX_PRESETS) throw new IllegalArgumentException("Too many presets");
		Map<String, Preset> copy = new LinkedHashMap<>();
		presets.forEach((key, value) -> copy.put(presetId(key), Objects.requireNonNull(value)));
		presets = Collections.unmodifiableMap(copy);
		Map<Trigger, String> routes = new LinkedHashMap<>();
		triggers.forEach((key, id) -> {
			Preset preset = copy.get(id);
			if (preset == null) throw new IllegalArgumentException("Unknown preset for " + key.id() + ": " + id);
			if (key.event() && preset.ticks() == 0) throw new IllegalArgumentException("Event presets need a finite duration: " + key.id());
			routes.put(key, id);
		});
		triggers = Collections.unmodifiableMap(routes);
	}

	public static YsmModelProfile empty(String model) { return new YsmModelProfile(model, Map.of(), Map.of()); }

	public static String modelId(String value) {
		String id = value == null ? "" : value.trim();
		if (id.isEmpty() || id.length() > 256 || id.chars().anyMatch(Character::isISOControl))
			throw new IllegalArgumentException("Invalid model ID (1..256 characters)");
		return id;
	}

	public static String presetId(String id) {
		if (id == null || id.length() > 128 || !id.matches("[a-z0-9_.:/-]+"))
			throw new IllegalArgumentException("Preset ID must use lowercase letters, digits, _ . : / -");
		return id;
	}

	public String toJson() {
		JsonObject root = new JsonObject();
		root.addProperty("format", FORMAT);
		root.addProperty("model", model);
		JsonObject groups = new JsonObject();
		presets.forEach((id, preset) -> {
			JsonObject group = new JsonObject();
			group.addProperty("description", preset.description());
			group.addProperty("clip", preset.clip());
			group.addProperty("ticks", preset.ticks());
			JsonObject values = new JsonObject();
			preset.parameters().forEach(values::addProperty);
			group.add("parameters", values);
			groups.add(id, group);
		});
		root.add("presets", groups);
		JsonObject routes = new JsonObject();
		triggers.forEach((trigger, id) -> routes.addProperty(trigger.id(), id));
		root.add("triggers", routes);
		return JSON.toJson(root);
	}

	/** All-or-nothing validation; no expression execution and no silently discarded trigger typos. */
	public static YsmModelProfile fromJson(String json) {
		if (json == null || json.length() > MAX_JSON_LENGTH) throw new IllegalArgumentException("Profile JSON is too large");
		try {
			JsonObject root = JsonParser.parseString(json).getAsJsonObject();
			keys(root, "format", "model", "presets", "triggers");
			if (integer(root.get("format")) != FORMAT) throw new IllegalArgumentException("Unsupported profile format");
			Map<String, Preset> presets = new LinkedHashMap<>();
			JsonObject groups = root.getAsJsonObject("presets");
			if (groups.size() > MAX_PRESETS) throw new IllegalArgumentException("Too many presets");
			for (var entry : groups.entrySet()) {
				JsonObject group = entry.getValue().getAsJsonObject();
				keys(group, "description", "clip", "ticks", "parameters");
				Map<String, Float> values = new LinkedHashMap<>();
				for (var value : group.getAsJsonObject("parameters").entrySet()) {
					if (!value.getValue().isJsonPrimitive() || !value.getValue().getAsJsonPrimitive().isNumber())
						throw new IllegalArgumentException("Parameters must be numeric literals");
					values.put(value.getKey(), value.getValue().getAsFloat());
				}
				presets.put(entry.getKey(), new Preset(string(group, "description"), string(group, "clip"), integer(group.get("ticks")), values));
			}
			Map<Trigger, String> routes = new LinkedHashMap<>();
			root.getAsJsonObject("triggers").entrySet().forEach(entry -> routes.put(Trigger.parse(entry.getKey()), string(root.getAsJsonObject("triggers"), entry.getKey())));
			return new YsmModelProfile(string(root, "model"), presets, routes);
		} catch (RuntimeException ex) {
			throw new IllegalArgumentException("Invalid YH model profile: " + ex.getMessage(), ex);
		}
	}

	private static String string(JsonObject object, String key) {
		JsonElement value = object.get(key);
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
			throw new IllegalArgumentException("Expected string: " + key);
		return value.getAsString();
	}

	private static void keys(JsonObject object, String... allowed) {
		var keys = java.util.Set.of(allowed);
		for (String key : object.keySet()) if (!keys.contains(key)) throw new IllegalArgumentException("Unknown profile field: " + key);
	}

	private static int integer(JsonElement element) {
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber())
			throw new IllegalArgumentException("Expected integer");
		return element.getAsBigDecimal().intValueExact();
	}
}
