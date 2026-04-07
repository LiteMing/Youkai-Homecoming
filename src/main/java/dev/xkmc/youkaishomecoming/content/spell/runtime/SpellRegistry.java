package dev.xkmc.youkaishomecoming.content.spell.runtime;

import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry for all spell definitions.
 * Populated at startup from Java registrations, and later from datapacks/KJS.
 */
public class SpellRegistry {

	private static final Map<ResourceLocation, SpellDefinition> REGISTRY = new ConcurrentHashMap<>();
	/** Authoritative startup defaults from Java/KubeJS registrations. */
	private static final Map<ResourceLocation, com.google.gson.JsonElement> BUILTIN_DEFAULTS = new ConcurrentHashMap<>();
	/** World-scoped defaults loaded from datapacks, overriding built-ins when present. */
	private static final Map<ResourceLocation, com.google.gson.JsonElement> DATAPACK_DEFAULTS = new ConcurrentHashMap<>();

	public static void register(SpellDefinition definition) {
		REGISTRY.put(definition.id, definition);
	}

	public static void register(ResourceLocation id, SpellDefinition definition) {
		REGISTRY.put(id, definition);
	}

	public static void registerBuiltin(SpellDefinition definition) {
		registerBuiltin(definition.id, definition);
	}

	public static void registerBuiltin(ResourceLocation id, SpellDefinition definition) {
		REGISTRY.put(id, definition);
		saveDefault(BUILTIN_DEFAULTS, definition);
	}

	public static void applyDatapackDefaults(Map<ResourceLocation, SpellDefinition> definitions) {
		for (var id : java.util.Set.copyOf(DATAPACK_DEFAULTS.keySet())) {
			DATAPACK_DEFAULTS.remove(id);
			SpellDefinition builtin = decodeDefault(BUILTIN_DEFAULTS.get(id));
			if (builtin != null) {
				REGISTRY.put(id, builtin);
			} else {
				REGISTRY.remove(id);
			}
		}
		for (var entry : definitions.entrySet()) {
			SpellDefinition def = entry.getValue();
			REGISTRY.put(entry.getKey(), def);
			saveDefault(DATAPACK_DEFAULTS, def);
		}
	}

	private static void saveDefault(Map<ResourceLocation, com.google.gson.JsonElement> defaults, SpellDefinition def) {
		SpellDefinition.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, def)
				.result().ifPresent(json -> defaults.put(def.id, json));
	}

	@Nullable
	private static SpellDefinition decodeDefault(@Nullable com.google.gson.JsonElement json) {
		if (json == null) return null;
		return SpellDefinition.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, json)
				.result().orElse(null);
	}

	/**
	 * Returns true if an authoritative built-in/datapack default exists for this spell ID.
	 * Used to distinguish source-authored spells from user-created custom spells.
	 */
	public static boolean hasDefault(ResourceLocation id) {
		return DATAPACK_DEFAULTS.containsKey(id) || BUILTIN_DEFAULTS.containsKey(id);
	}

	/**
	 * Get the authoritative default definition for this spell.
	 * Datapack definitions override built-ins when both exist.
	 */
	@Nullable
	public static SpellDefinition getDefault(ResourceLocation id) {
		SpellDefinition datapack = decodeDefault(DATAPACK_DEFAULTS.get(id));
		if (datapack != null) return datapack;
		return decodeDefault(BUILTIN_DEFAULTS.get(id));
	}

	@Nullable
	public static SpellDefinition get(ResourceLocation id) {
		return REGISTRY.get(id);
	}

	public static Map<ResourceLocation, SpellDefinition> getAll() {
		return java.util.Collections.unmodifiableMap(REGISTRY);
	}

	public static boolean contains(ResourceLocation id) {
		return REGISTRY.containsKey(id);
	}

	public static void remove(ResourceLocation id) {
		REGISTRY.remove(id);
	}

	public static void clear() {
		REGISTRY.clear();
		BUILTIN_DEFAULTS.clear();
		DATAPACK_DEFAULTS.clear();
	}

	public static int size() {
		return REGISTRY.size();
	}
}
