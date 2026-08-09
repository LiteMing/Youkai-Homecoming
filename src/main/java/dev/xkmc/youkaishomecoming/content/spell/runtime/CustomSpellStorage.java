package dev.xkmc.youkaishomecoming.content.spell.runtime;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.serialization.JsonOps;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;

/**
 * JSON file-based storage for custom spell definitions.
 * Regular saves are stored under {@code <world>/youkaishomecoming_spells/<namespace>/}.
 * Exported spells are stored under {@code <game>/youkaishomecoming_spells/<namespace>/}
 * and are loaded for every save on the same game/server instance.
 * No NBT conversion — pure JSON, consistent with the export/import system.
 */
public class CustomSpellStorage {

	private static final Logger LOGGER = LoggerFactory.getLogger("YoukaiHomecoming/SpellStorage");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String DIR_NAME = "youkaishomecoming_spells";

	/**
	 * Get the global storage root directory shared by all saves on this game/server instance.
	 */
	public static File getGlobalStorageDir() {
		return new File(FMLPaths.GAMEDIR.get().toFile(), DIR_NAME);
	}

	/**
	 * Get the storage root directory for the given server's world.
	 */
	public static File getWorldStorageDir(MinecraftServer server) {
		return new File(server.getWorldPath(LevelResource.ROOT).toFile(), DIR_NAME);
	}

	/**
	 * Backwards-compatible name for world-local spell storage.
	 */
	public static File getStorageDir(MinecraftServer server) {
		return getWorldStorageDir(server);
	}

	/**
	 * Get the JSON file path for a specific spell ID.
	 * e.g. {@code youkaishomecoming_spells/youkaishomecoming/my_spell.json}
	 */
	public static File getSpellFile(MinecraftServer server, ResourceLocation id) {
		return getSpellFile(getWorldStorageDir(server), id);
	}

	public static File getGlobalSpellFile(ResourceLocation id) {
		return getSpellFile(getGlobalStorageDir(), id);
	}

	private static File getSpellFile(File root, ResourceLocation id) {
		File nsDir = new File(root, id.getNamespace());
		nsDir.mkdirs();
		String fileName = id.getPath().replace('/', '_') + ".json";
		return new File(nsDir, fileName);
	}

	/**
	 * Ownership metadata lives in a sidecar file next to the spell JSON
	 * ({@code <spell>.json.owner}) so the definition format itself stays intact.
	 * Created by the editor save path; non-OP players may only edit/delete spells
	 * they created themselves.
	 */
	private static File getOwnerFile(File spellFile) {
		return new File(spellFile.getPath() + ".owner");
	}

	public static void saveOwner(MinecraftServer server, ResourceLocation id, UUID owner) {
		File file = getOwnerFile(getSpellFile(server, id));
		try {
			Files.writeString(file.toPath(), owner.toString());
		} catch (IOException e) {
			LOGGER.error("Failed to save owner for spell {}: {}", id, file.getPath(), e);
		}
	}

	@Nullable
	public static UUID loadOwner(MinecraftServer server, ResourceLocation id) {
		File file = getOwnerFile(getSpellFile(server, id));
		if (!file.exists()) {
			return null;
		}
		try {
			return UUID.fromString(Files.readString(file.toPath()).trim());
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Save a spell definition as a JSON file. Overwrites if exists.
	 */
	public static void saveSpell(MinecraftServer server, SpellDefinition definition) {
		saveSpell(getSpellFile(server, definition.id), definition);
	}

	/**
	 * Export a spell definition into the game/server directory so every save loads it.
	 */
	public static File saveGlobalSpell(SpellDefinition definition) {
		File file = getGlobalSpellFile(definition.id);
		saveSpell(file, definition);
		return file;
	}

	private static void saveSpell(File file, SpellDefinition definition) {
		try {
			var json = SpellDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition)
					.getOrThrow(false, s -> {});
			try (var writer = new FileWriter(file)) {
				GSON.toJson(json, writer);
			}
			LOGGER.info("Saved custom spell {} to {}", definition.id, file.getPath());
		} catch (Exception e) {
			LOGGER.error("Failed to save spell {} to {}", definition.id, file.getPath(), e);
		}
	}

	/**
	 * Delete a custom spell JSON file.
	 */
	public static void deleteSpell(MinecraftServer server, ResourceLocation id) {
		deleteSpellFile(getSpellFile(server, id));
		deleteSpellFile(getGlobalSpellFile(id));
	}

	private static void deleteSpellFile(File file) {
		if (file.exists()) {
			file.delete();
		}
		// Clean up empty namespace directory
		File nsDir = file.getParentFile();
		if (nsDir != null && nsDir.isDirectory() && nsDir.list().length == 0) {
			nsDir.delete();
		}
	}

	/**
	 * Load all custom spell JSON files into SpellRegistry.
	 * Call on server/world start.
	 */
	public static void loadAllIntoRegistry(MinecraftServer server) {
		loadStorageDir("global", getGlobalStorageDir(), true);
		loadStorageDir("world", getWorldStorageDir(server), false);
	}

	private static void loadStorageDir(String label, File dir, boolean allowDefaultOverrides) {
		LOGGER.info("Loading {} custom spells from {}", label, dir.getPath());
		if (!dir.exists() || !dir.isDirectory()) {
			LOGGER.info("No {} custom spell directory found at {}", label, dir.getPath());
			return;
		}
		loadRecursive(dir, allowDefaultOverrides);
	}

	private static void loadRecursive(File dir, boolean allowDefaultOverrides) {
		File[] files = dir.listFiles();
		if (files == null) return;
		for (File file : files) {
			if (file.isDirectory()) {
				loadRecursive(file, allowDefaultOverrides);
			} else if (file.getName().endsWith(".json")) {
				loadSpellFile(file, allowDefaultOverrides);
			}
		}
	}

	private static void loadSpellFile(File file, boolean allowDefaultOverrides) {
		try {
			String content = Files.readString(file.toPath());
			var json = com.google.gson.JsonParser.parseString(content);
			SpellDefinition.CODEC.parse(JsonOps.INSTANCE, json)
					.resultOrPartial(err -> LOGGER.warn("Failed to parse spell file {}: {}", file.getPath(), err))
					.ifPresent(def -> {
						// Skip disk-cached versions of built-in spells — Java code is always authoritative.
						// This prevents stale auto-saved JSONs from overriding updated Java definitions.
						if (!allowDefaultOverrides && SpellRegistry.hasDefault(def.id)) {
							LOGGER.info("Skipping disk-cached built-in spell {} (Java definition takes priority)", def.id);
							file.delete();
							return;
						}
						SpellRegistry.register(def);
						LOGGER.info("Loaded custom spell {} from {}", def.id, file.getPath());
					});
		} catch (Exception e) {
			LOGGER.warn("Failed to read spell file {}: {}", file.getPath(), e.getMessage());
		}
	}

}
