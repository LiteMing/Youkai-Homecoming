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

	private static final Map<ResourceLocation, SpellDefinition> BUILTIN = new ConcurrentHashMap<>();
	private static final Map<ResourceLocation, SpellDefinition> DATAPACK = new ConcurrentHashMap<>();
	private static final Map<ResourceLocation, SpellDefinition> REGISTRY = new ConcurrentHashMap<>();

	public static void register(SpellDefinition definition) {
		registerBuiltin(definition);
	}

	public static void register(ResourceLocation id, SpellDefinition definition) {
		registerBuiltin(id, definition);
	}

	public static synchronized void registerBuiltin(SpellDefinition definition) {
		registerBuiltin(definition.id, definition);
	}

	public static synchronized void registerBuiltin(ResourceLocation id, SpellDefinition definition) {
		BUILTIN.put(id, definition);
		rebuild();
	}

	public static synchronized void replaceDatapackDefinitions(Map<ResourceLocation, SpellDefinition> definitions) {
		DATAPACK.clear();
		DATAPACK.putAll(definitions);
		rebuild();
	}

	@Nullable
	public static SpellDefinition get(ResourceLocation id) {
		return REGISTRY.get(id);
	}

	public static Map<ResourceLocation, SpellDefinition> getAll() {
		return java.util.Collections.unmodifiableMap(REGISTRY);
	}

	public static Map<ResourceLocation, SpellDefinition> getBuiltin() {
		return java.util.Collections.unmodifiableMap(BUILTIN);
	}

	public static Map<ResourceLocation, SpellDefinition> getDatapack() {
		return java.util.Collections.unmodifiableMap(DATAPACK);
	}

	public static boolean contains(ResourceLocation id) {
		return REGISTRY.containsKey(id);
	}

	public static synchronized void clear() {
		clearAll();
	}

	public static synchronized void clearAll() {
		BUILTIN.clear();
		DATAPACK.clear();
		REGISTRY.clear();
	}

	public static int size() {
		return REGISTRY.size();
	}

	private static void rebuild() {
		REGISTRY.clear();
		REGISTRY.putAll(BUILTIN);
		REGISTRY.putAll(DATAPACK);
	}
}
