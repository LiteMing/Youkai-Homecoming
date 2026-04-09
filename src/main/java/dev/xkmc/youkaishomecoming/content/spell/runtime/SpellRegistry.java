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

	private record RegistryState(
			Map<ResourceLocation, SpellDefinition> registry,
			Map<ResourceLocation, com.google.gson.JsonElement> builtinDefaults,
			Map<ResourceLocation, com.google.gson.JsonElement> datapackDefaults
	) {
	}

	private static final Object WRITE_LOCK = new Object();

	private static volatile RegistryState STATE = new RegistryState(
			new ConcurrentHashMap<>(),
			new ConcurrentHashMap<>(),
			new ConcurrentHashMap<>()
	);

	public static void register(SpellDefinition definition) {
		register(definition.id, definition);
	}

	/**
	 * Register a transient spell definition.
	 * This updates the live registry only and does not persist authoritative defaults.
	 * Datapack reload or explicit reset may replace it.
	 */
	public static void register(ResourceLocation id, SpellDefinition definition) {
		synchronized (WRITE_LOCK) {
			STATE.registry().put(id, definition);
		}
	}

	public static void registerBuiltin(SpellDefinition definition) {
		registerBuiltin(definition.id, definition);
	}

	public static void registerBuiltin(ResourceLocation id, SpellDefinition definition) {
		synchronized (WRITE_LOCK) {
			STATE.registry().put(id, definition);
			saveDefault(STATE.builtinDefaults(), id, definition);
		}
	}

	public static void applyDatapackDefaults(Map<ResourceLocation, SpellDefinition> definitions) {
		synchronized (WRITE_LOCK) {
			RegistryState current = STATE;
			Map<ResourceLocation, SpellDefinition> registry = new ConcurrentHashMap<>(current.registry());
			Map<ResourceLocation, com.google.gson.JsonElement> datapackDefaults = new ConcurrentHashMap<>();

			for (var id : current.datapackDefaults().keySet()) {
				SpellDefinition builtin = decodeDefault(current.builtinDefaults().get(id));
				if (builtin != null) {
					registry.put(id, builtin);
				} else {
					registry.remove(id);
				}
			}

			for (var entry : definitions.entrySet()) {
				SpellDefinition def = entry.getValue();
				registry.put(entry.getKey(), def);
				saveDefault(datapackDefaults, entry.getKey(), def);
			}

			STATE = new RegistryState(registry, current.builtinDefaults(), datapackDefaults);
		}
	}

	private static void saveDefault(Map<ResourceLocation, com.google.gson.JsonElement> defaults, ResourceLocation id, SpellDefinition def) {
		SpellDefinition.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, def)
				.result().ifPresent(json -> defaults.put(id, json));
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
		RegistryState state = STATE;
		return state.datapackDefaults().containsKey(id) || state.builtinDefaults().containsKey(id);
	}

	/**
	 * Get the authoritative default definition for this spell.
	 * Datapack definitions override built-ins when both exist.
	 */
	@Nullable
	public static SpellDefinition getDefault(ResourceLocation id) {
		RegistryState state = STATE;
		SpellDefinition datapack = decodeDefault(state.datapackDefaults().get(id));
		if (datapack != null) return datapack;
		return decodeDefault(state.builtinDefaults().get(id));
	}

	@Nullable
	public static SpellDefinition get(ResourceLocation id) {
		return STATE.registry().get(id);
	}

	public static Map<ResourceLocation, SpellDefinition> getAll() {
		return java.util.Collections.unmodifiableMap(STATE.registry());
	}

	public static boolean contains(ResourceLocation id) {
		return STATE.registry().containsKey(id);
	}

	public static void remove(ResourceLocation id) {
		synchronized (WRITE_LOCK) {
			STATE.registry().remove(id);
		}
	}

	public static void clear() {
		synchronized (WRITE_LOCK) {
			STATE = new RegistryState(
					new ConcurrentHashMap<>(),
					new ConcurrentHashMap<>(),
					new ConcurrentHashMap<>()
			);
		}
	}

	public static int size() {
		return STATE.registry().size();
	}
}
