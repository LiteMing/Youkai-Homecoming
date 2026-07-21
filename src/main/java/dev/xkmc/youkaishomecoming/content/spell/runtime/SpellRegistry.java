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
	/**
	 * Live snapshots for spells with {@code legacy_ticker} actions.
	 * JSON encode/decode drops the Java factory; these keep the original instance.
	 */
	private static final Map<ResourceLocation, SpellDefinition> LEGACY_DEFAULTS = new ConcurrentHashMap<>();

	public static void register(SpellDefinition definition) {
		putSafely(definition.id, definition);
		ORIGINS.put(definition.id, hasDefault(definition.id) ? Origin.BUILTIN : Origin.CUSTOM);
	}

	public static void register(ResourceLocation id, SpellDefinition definition) {
		putSafely(id, definition);
		ORIGINS.put(id, hasDefault(id) ? Origin.BUILTIN : Origin.CUSTOM);
	}

	public static void registerDefault(SpellDefinition definition) {
		putSafely(definition.id, definition);
		saveDefault(definition);
		ORIGINS.put(definition.id, Origin.BUILTIN);
	}

	public static void registerDefault(ResourceLocation id, SpellDefinition definition) {
		putSafely(id, definition);
		saveDefault(definition);
		ORIGINS.put(id, Origin.BUILTIN);
	}

	/**
	 * Refuse to replace a bound legacy_ticker definition with a decoded empty shell.
	 */
	private static void putSafely(ResourceLocation id, SpellDefinition definition) {
		SpellDefinition existing = REGISTRY.get(id);
		if (existing != null && existing.hasLegacyTicker() && isBoundLegacy(existing)
				&& definition.hasLegacyTicker() && !isBoundLegacy(definition)) {
			return;
		}
		REGISTRY.put(id, definition);
	}

	private static boolean isBoundLegacy(SpellDefinition def) {
		for (var phase : def.phases.values()) {
			for (var action : phase.onTick) {
				if (action instanceof dev.xkmc.youkaishomecoming.content.spell.action.LegacyTickerAction legacy
						&& legacy.isBound()) {
					return true;
				}
			}
		}
		return false;
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
		if (LEGACY_DEFAULTS.containsKey(def.id) || DEFAULTS.containsKey(def.id)) {
			return;
		}
		if (def.hasLegacyTicker()) {
			// Keep live instance — factory is not JSON-serializable
			LEGACY_DEFAULTS.put(def.id, def);
			return;
		}
		SpellDefinition.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, def)
				.result().ifPresent(json -> DEFAULTS.put(def.id, json));
	}

	/**
	 * Returns true if a built-in (code-defined) default exists for this spell ID.
	 * Used to distinguish built-in spells from user-created custom spells.
	 */
	public static boolean hasDefault(ResourceLocation id) {
		return DEFAULTS.containsKey(id) || LEGACY_DEFAULTS.containsKey(id);
	}

	/**
	 * Get the original default definition (before any editor changes).
	 * Returns null for custom spells that were never registered from code.
	 * Legacy-ticker defaults return the live instance (factory cannot survive JSON).
	 */
	@Nullable
	public static SpellDefinition getDefault(ResourceLocation id) {
		SpellDefinition legacy = LEGACY_DEFAULTS.get(id);
		if (legacy != null) {
			return legacy;
		}
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
		// DEFAULTS / LEGACY_DEFAULTS intentionally retained across reloads of custom content
	}

	public static int size() {
		return REGISTRY.size();
	}

	public enum Origin {
		BUILTIN, CUSTOM, MARKET
	}
}
