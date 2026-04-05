package dev.xkmc.youkaishomecoming.content.spell.runtime;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.serialization.JsonOps;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * JSON file-based storage for custom spell definitions.
 * Each spell is stored as a .json file under {@code <world>/youkaishomecoming_spells/<namespace>/}.
 * No NBT conversion — pure JSON, consistent with the export/import system.
 */
public class CustomSpellStorage {

	private static final Logger LOGGER = LoggerFactory.getLogger("YoukaiHomecoming/SpellStorage");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String DIR_NAME = "youkaishomecoming_spells";

	/**
	 * Get the storage root directory for the given server's world.
	 */
	public static File getStorageDir(MinecraftServer server) {
		return new File(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile(), DIR_NAME);
	}

	/**
	 * Get the JSON file path for a specific spell ID.
	 * e.g. {@code youkaishomecoming_spells/youkaishomecoming/my_spell.json}
	 */
	public static File getSpellFile(MinecraftServer server, ResourceLocation id) {
		File nsDir = new File(getStorageDir(server), id.getNamespace());
		nsDir.mkdirs();
		String fileName = id.getPath().replace('/', '_') + ".json";
		return new File(nsDir, fileName);
	}

	/**
	 * Save a spell definition as a JSON file. Overwrites if exists.
	 */
	public static void saveSpell(MinecraftServer server, SpellDefinition definition) {
		try {
			File file = getSpellFile(server, definition.id);
			var json = SpellDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition)
					.getOrThrow(false, s -> {});
			try (var writer = new FileWriter(file)) {
				GSON.toJson(json, writer);
			}
		} catch (Exception e) {
			LOGGER.error("Failed to save spell {}", definition.id, e);
		}
	}

	/**
	 * Delete a custom spell JSON file.
	 */
	public static void deleteSpell(MinecraftServer server, ResourceLocation id) {
		File file = getSpellFile(server, id);
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
		File dir = getStorageDir(server);
		if (!dir.exists() || !dir.isDirectory()) return;
		loadRecursive(dir);
	}

	private static void loadRecursive(File dir) {
		File[] files = dir.listFiles();
		if (files == null) return;
		for (File file : files) {
			if (file.isDirectory()) {
				loadRecursive(file);
			} else if (file.getName().endsWith(".json")) {
				loadSpellFile(file);
			}
		}
	}

	private static void loadSpellFile(File file) {
		try {
			String content = Files.readString(file.toPath());
			var json = com.google.gson.JsonParser.parseString(content);
			SpellDefinition.CODEC.parse(JsonOps.INSTANCE, json)
					.resultOrPartial(err -> LOGGER.warn("Failed to parse spell file {}: {}", file.getName(), err))
					.ifPresent(def -> {
						// Skip disk-cached versions of built-in spells — Java code is always authoritative.
						// This prevents stale auto-saved JSONs from overriding updated Java definitions.
						if (SpellRegistry.hasDefault(def.id)) {
							LOGGER.info("Skipping disk-cached built-in spell {} (Java definition takes priority)", def.id);
							file.delete();
							return;
						}
						SpellRegistry.register(def);
					});
		} catch (Exception e) {
			LOGGER.warn("Failed to read spell file {}: {}", file.getName(), e.getMessage());
		}
	}

}
