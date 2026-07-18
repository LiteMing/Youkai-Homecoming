package dev.xkmc.youkaishomecoming.content.spell.runtime;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.DanmakuProxyEntity;
import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/**
 * Runtime-facing helpers for command and KubeJS spell edits.
 * The canonical interchange format is SpellDefinition JSON.
 */
public class SpellRuntimeAccess {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	@Nullable
	public static JsonElement toJson(String spellId) {
		return toJson(parseId(spellId));
	}

	@Nullable
	public static JsonElement toJson(ResourceLocation spellId) {
		SpellDefinition def = SpellRegistry.get(spellId);
		if (def == null) return null;
		return encodeDefinition(def);
	}

	public static String exportJson(String spellId) {
		JsonElement json = toJson(spellId);
		if (json == null) {
			throw new IllegalArgumentException("Unknown spell: " + spellId);
		}
		return GSON.toJson(json);
	}

	public static int importJson(MinecraftServer server, String json, boolean save, boolean reapply) {
		SpellDefinition def = parseDefinition(parseJson(json));
		SpellRegistry.register(def);
		if (save) {
			CustomSpellStorage.saveSpell(server, def);
		}
		return reapply ? reapply(server, def.id, true) : 0;
	}

	public static int patch(MinecraftServer server, String spellId, String pointer, String jsonValue,
							boolean save, boolean reapply) {
		SpellDefinition def = patchRegistry(spellId, pointer, jsonValue);
		if (save) {
			CustomSpellStorage.saveSpell(server, def);
		}
		return reapply ? reapply(server, def.id, true) : 0;
	}

	public static int patchNumber(MinecraftServer server, String spellId, String pointer, double value,
								  boolean save, boolean reapply) {
		return patchElement(server, spellId, pointer, new JsonPrimitive(value), save, reapply);
	}

	public static int patchString(MinecraftServer server, String spellId, String pointer, String value,
								  boolean save, boolean reapply) {
		return patchElement(server, spellId, pointer, new JsonPrimitive(value), save, reapply);
	}

	public static int patchBoolean(MinecraftServer server, String spellId, String pointer, boolean value,
								   boolean save, boolean reapply) {
		return patchElement(server, spellId, pointer, new JsonPrimitive(value), save, reapply);
	}

	public static SpellDefinition patchRegistry(String spellId, String pointer, String jsonValue) {
		return patchRegistryElement(spellId, pointer, parseJsonValue(jsonValue));
	}

	public static SpellDefinition patchRegistryNumber(String spellId, String pointer, double value) {
		return patchRegistryElement(spellId, pointer, new JsonPrimitive(value));
	}

	public static SpellDefinition patchRegistryString(String spellId, String pointer, String value) {
		return patchRegistryElement(spellId, pointer, new JsonPrimitive(value));
	}

	public static SpellDefinition patchRegistryBoolean(String spellId, String pointer, boolean value) {
		return patchRegistryElement(spellId, pointer, new JsonPrimitive(value));
	}

	public static int restoreDefault(MinecraftServer server, String spellId, boolean deleteSaved, boolean reapply) {
		ResourceLocation id = parseId(spellId);
		SpellDefinition def = SpellRegistry.getDefault(id);
		if (def == null) {
			throw new IllegalArgumentException("No default spell exists for: " + spellId);
		}
		SpellRegistry.register(id, def);
		if (deleteSaved) {
			CustomSpellStorage.deleteSpell(server, id);
		}
		return reapply ? reapply(server, id, true) : 0;
	}

	public static int reapply(MinecraftServer server, String spellId, boolean clearScreen) {
		return reapply(server, parseId(spellId), clearScreen);
	}

	public static int reapply(MinecraftServer server, ResourceLocation spellId, boolean clearScreen) {
		SpellDefinition def = SpellRegistry.get(spellId);
		if (def == null) {
			throw new IllegalArgumentException("Unknown spell: " + spellId);
		}
		String spellIdStr = spellId.toString();
		int count = 0;
		for (var level : server.getAllLevels()) {
			for (var entity : level.getAllEntities()) {
				if (entity instanceof YoukaiEntity youkai) {
					boolean match = youkai.spellRuntime != null
							&& youkai.spellRuntime.getDefinition().id.equals(spellId);
					if (!match && youkai.spellCard != null) {
						match = spellIdStr.equals(youkai.spellCard.modelId)
								|| spellId.equals(youkai.spellCard.spellId);
					}
					if (match) {
						if (clearScreen) {
							youkai.eraseAllDanmaku(null);
						}
						youkai.setSpellRuntime(new SpellRuntime(def));
						count++;
					}
					continue;
				}
				if (entity instanceof SpellRuntimeHost host && host.hasSpell(spellId)) {
					host.switchSpellDefinition(def, clearScreen);
					count++;
				}
			}
		}
		return count;
	}

	public static int stop(MinecraftServer server, ResourceLocation spellId, boolean eraseProjectiles) {
		int count = 0;
		for (var level : server.getAllLevels()) {
			for (var entity : level.getAllEntities()) {
				if (!(entity instanceof SpellRuntimeHost host) || !host.hasSpell(spellId)) continue;
				if (eraseProjectiles) host.eraseDanmaku(null);
				if (entity instanceof YoukaiEntity youkai) youkai.spellCard = null;
				host.setSpellRuntime(null);
				host.syncSpellState();
				count++;
			}
		}
		return count;
	}

	/** Stops active runtimes, optionally erases their projectiles and saved world/global files, and does not broadcast a registry snapshot. */
	public static boolean deleteCustomDestructive(MinecraftServer server, String spellId, boolean confirm,
										 boolean eraseProjectiles, boolean deleteSavedFiles) {
		if (!confirm) throw new IllegalArgumentException("Destructive spell deletion requires confirm=true");
		ResourceLocation id = parseId(spellId);
		if (SpellRegistry.hasDefault(id)) throw new IllegalArgumentException("Cannot delete built-in/KJS default spell: " + id);
		if (SpellRegistry.getOrigin(id) == SpellRegistry.Origin.MARKET) {
			throw new IllegalArgumentException("Use YHSpellMarket.unloadManaged for market-owned spells: " + id);
		}
		if (!SpellRegistry.contains(id)) return false;
		stop(server, id, eraseProjectiles);
		SpellRegistry.remove(id);
		if (deleteSavedFiles) CustomSpellStorage.deleteSpell(server, id);
		return true;
	}

	public static String pointer(String... parts) {
		StringBuilder builder = new StringBuilder();
		for (String part : parts) {
			builder.append('/').append(escapePointer(part));
		}
		return builder.toString();
	}

	public static String actionFieldPointer(String phaseId, String listName, int actionIndex, String field) {
		return pointer("phases", phaseId, listName, Integer.toString(actionIndex), field);
	}

	public static String escapePointer(String part) {
		return part.replace("~", "~0").replace("/", "~1");
	}

	public static String unescapePointer(String part) {
		return part.replace("~1", "/").replace("~0", "~");
	}

	private static int patchElement(MinecraftServer server, String spellId, String pointer, JsonElement value,
									boolean save, boolean reapply) {
		SpellDefinition def = patchRegistryElement(spellId, pointer, value);
		if (save) {
			CustomSpellStorage.saveSpell(server, def);
		}
		return reapply ? reapply(server, def.id, true) : 0;
	}

	private static SpellDefinition patchRegistryElement(String spellId, String pointer, JsonElement value) {
		ResourceLocation id = parseId(spellId);
		JsonElement root = toJson(id);
		if (root == null) {
			throw new IllegalArgumentException("Unknown spell: " + spellId);
		}
		JsonElement patched = applyJsonPointer(root.deepCopy(), pointer, normalize(value));
		SpellDefinition def = parseDefinition(patched);
		if (!def.id.equals(id)) {
			throw new IllegalArgumentException("Patch changed spell id from " + id + " to " + def.id);
		}
		SpellRegistry.register(id, def);
		return def;
	}

	private static JsonElement applyJsonPointer(JsonElement root, String pointer, JsonElement value) {
		if (pointer == null || pointer.isEmpty()) {
			return value;
		}
		if (!pointer.startsWith("/")) {
			throw new IllegalArgumentException("JSON pointer must start with '/': " + pointer);
		}
		String[] parts = pointer.substring(1).split("/", -1);
		JsonElement parent = root;
		for (int i = 0; i < parts.length - 1; i++) {
			String key = unescapePointer(parts[i]);
			parent = getChild(parent, key);
		}
		String leaf = unescapePointer(parts[parts.length - 1]);
		if (parent instanceof JsonObject obj) {
			obj.add(leaf, value);
			return root;
		}
		if (parent instanceof JsonArray arr) {
			int index = parseIndex(leaf, arr.size());
			arr.set(index, value);
			return root;
		}
		throw new IllegalArgumentException("Cannot patch child of non-container at: " + pointer);
	}

	private static JsonElement getChild(JsonElement parent, String key) {
		if (parent instanceof JsonObject obj) {
			JsonElement child = obj.get(key);
			if (child == null) {
				throw new IllegalArgumentException("Missing object key in JSON pointer: " + key);
			}
			return child;
		}
		if (parent instanceof JsonArray arr) {
			return arr.get(parseIndex(key, arr.size()));
		}
		throw new IllegalArgumentException("Cannot traverse non-container JSON value at: " + key);
	}

	private static int parseIndex(String key, int size) {
		try {
			int index = Integer.parseInt(key);
			if (index < 0 || index >= size) {
				throw new IllegalArgumentException("Array index out of range: " + key + " (size " + size + ")");
			}
			return index;
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Invalid array index in JSON pointer: " + key);
		}
	}

	private static JsonElement parseJson(String json) {
		try {
			return JsonParser.parseString(json);
		} catch (Exception e) {
			throw new IllegalArgumentException("Invalid spell JSON: " + e.getMessage(), e);
		}
	}

	private static JsonElement parseJsonValue(String jsonValue) {
		try {
			return JsonParser.parseString(jsonValue);
		} catch (Exception ignored) {
			return new JsonPrimitive(jsonValue);
		}
	}

	private static JsonElement encodeDefinition(SpellDefinition def) {
		var result = SpellDefinition.CODEC.encodeStart(JsonOps.INSTANCE, def);
		if (result.error().isPresent()) {
			throw new IllegalArgumentException("Failed to encode spell " + def.id + ": "
					+ result.error().get().message());
		}
		return result.result().orElseThrow(() -> new IllegalArgumentException("Spell encode returned no result"));
	}

	private static SpellDefinition parseDefinition(JsonElement json) {
		var result = SpellDefinition.CODEC.parse(JsonOps.INSTANCE, json);
		if (result.error().isPresent()) {
			throw new IllegalArgumentException("Failed to parse spell JSON: " + result.error().get().message());
		}
		return result.result().orElseThrow(() -> new IllegalArgumentException("Spell parse returned no result"));
	}

	private static JsonElement normalize(@Nullable JsonElement value) {
		return value == null ? JsonNull.INSTANCE : value.deepCopy();
	}

	private static ResourceLocation parseId(String spellId) {
		ResourceLocation id = ResourceLocation.tryParse(spellId);
		if (id == null) {
			throw new IllegalArgumentException("Invalid spell id: " + spellId);
		}
		return id;
	}
}
