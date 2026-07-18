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
	private static final Map<ResourceLocation, Origin> ORIGINS = new ConcurrentHashMap<>();
	/** Original definitions from code/datapack registration, before any editor modifications. */
	private static final Map<ResourceLocation, com.google.gson.JsonElement> DEFAULTS = new ConcurrentHashMap<>();

	public static void register(SpellDefinition definition) {
		REGISTRY.put(definition.id, definition);
		ORIGINS.put(definition.id, hasDefault(definition.id) ? Origin.BUILTIN : Origin.CUSTOM);
	}

	public static void register(ResourceLocation id, SpellDefinition definition) {
		REGISTRY.put(id, definition);
		ORIGINS.put(id, hasDefault(id) ? Origin.BUILTIN : Origin.CUSTOM);
	}

	public static void registerDefault(SpellDefinition definition) {
		REGISTRY.put(definition.id, definition);
		saveDefault(definition);
		ORIGINS.put(definition.id, Origin.BUILTIN);
	}

	public static void registerDefault(ResourceLocation id, SpellDefinition definition) {
		REGISTRY.put(id, definition);
		saveDefault(definition);
		ORIGINS.put(id, Origin.BUILTIN);
	}

	public static void registerMarket(SpellDefinition definition) {
		if (hasDefault(definition.id)) {
			throw new IllegalArgumentException("Cannot replace built-in spell: " + definition.id);
		}
		REGISTRY.put(definition.id, definition);
		ORIGINS.put(definition.id, Origin.MARKET);
	}

	public static Origin getOrigin(ResourceLocation id) {
		if (hasDefault(id)) return Origin.BUILTIN;
		return ORIGINS.getOrDefault(id, REGISTRY.containsKey(id) ? Origin.CUSTOM : null);
	}

	private static void saveDefault(SpellDefinition def) {
		// Only save the first time (original built-in definition)
		if (!DEFAULTS.containsKey(def.id)) {
			SpellDefinition.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, def)
					.result().ifPresent(json -> DEFAULTS.put(def.id, json));
		}
	}

	/**
	 * Returns true if a built-in (code-defined) default exists for this spell ID.
	 * Used to distinguish built-in spells from user-created custom spells.
	 */
	public static boolean hasDefault(ResourceLocation id) {
		return DEFAULTS.containsKey(id);
	}

	/**
	 * Get the original default definition (before any editor changes).
	 * Returns null for custom spells that were never registered from code.
	 */
	@Nullable
	public static SpellDefinition getDefault(ResourceLocation id) {
		var json = DEFAULTS.get(id);
		if (json == null) return null;
		return SpellDefinition.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, json)
				.result().orElse(null);
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
		if (hasDefault(id)) return;
		REGISTRY.remove(id);
		ORIGINS.remove(id);
	}

	public static void clear() {
		REGISTRY.clear();
		ORIGINS.clear();
	}

	public static int size() {
		return REGISTRY.size();
	}

	public enum Origin {
		BUILTIN, CUSTOM, MARKET
	}
}
