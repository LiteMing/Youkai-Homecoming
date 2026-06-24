package dev.xkmc.fastprojectileapi.spellcircle;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;

public class CustomSpellCircleStorage {

	private static final Logger LOGGER = LoggerFactory.getLogger("YoukaiHomecoming/SpellCircleStorage");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String DIR_NAME = "youkaishomecoming_spell_circles";

	public static File getGlobalStorageDir() {
		return new File(FMLPaths.GAMEDIR.get().toFile(), DIR_NAME);
	}

	public static File getWorldStorageDir(MinecraftServer server) {
		return new File(server.getWorldPath(LevelResource.ROOT).toFile(), DIR_NAME);
	}

	public static File getCircleFile(MinecraftServer server, ResourceLocation id) {
		return getCircleFile(getWorldStorageDir(server), id);
	}

	public static File getGlobalCircleFile(ResourceLocation id) {
		return getCircleFile(getGlobalStorageDir(), id);
	}

	private static File getCircleFile(File root, ResourceLocation id) {
		File nsDir = new File(root, id.getNamespace());
		nsDir.mkdirs();
		String fileName = id.getPath().replace('/', '_') + ".json";
		return new File(nsDir, fileName);
	}

	public static void saveCircle(MinecraftServer server, ResourceLocation id, SpellComponent component) {
		saveCircle(getCircleFile(server, id), id, component);
	}

	public static File saveGlobalCircle(ResourceLocation id, SpellComponent component) {
		File file = getGlobalCircleFile(id);
		saveCircle(file, id, component);
		return file;
	}

	private static void saveCircle(File file, ResourceLocation id, SpellComponent component) {
		try {
			component.invalidateCache();
			JsonObject map = new JsonObject();
			map.add(id.toString(), GSON.toJsonTree(component));
			JsonObject root = new JsonObject();
			root.add("map", map);
			try (var writer = new FileWriter(file)) {
				GSON.toJson(root, writer);
			}
			LOGGER.info("Saved custom spell circle {} to {}", id, file.getPath());
		} catch (Exception e) {
			LOGGER.error("Failed to save spell circle {} to {}", id, file.getPath(), e);
		}
	}

	public static void loadAllIntoConfig(MinecraftServer server) {
		loadStorageDir("global", getGlobalStorageDir());
		loadStorageDir("world", getWorldStorageDir(server));
	}

	private static void loadStorageDir(String label, File dir) {
		LOGGER.info("Loading {} custom spell circles from {}", label, dir.getPath());
		if (!dir.exists() || !dir.isDirectory()) {
			LOGGER.info("No {} custom spell circle directory found at {}", label, dir.getPath());
			return;
		}
		loadRecursive(dir);
	}

	private static void loadRecursive(File dir) {
		File[] files = dir.listFiles();
		if (files == null) return;
		for (File file : files) {
			if (file.isDirectory()) {
				loadRecursive(file);
			} else if (file.getName().endsWith(".json")) {
				loadCircleFile(file);
			}
		}
	}

	private static void loadCircleFile(File file) {
		try {
			String content = Files.readString(file.toPath());
			var json = JsonParser.parseString(content);
			if (!json.isJsonObject()) {
				return;
			}
			JsonObject object = json.getAsJsonObject();
			JsonObject map = object.has("map") && object.get("map").isJsonObject()
					? object.getAsJsonObject("map") : object;
			for (var entry : map.entrySet()) {
				ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
				if (id == null) {
					LOGGER.warn("Skipping custom spell circle with invalid id {} in {}", entry.getKey(), file.getPath());
					continue;
				}
				SpellComponent component = GSON.fromJson(entry.getValue(), SpellComponent.class);
				if (component == null) {
					continue;
				}
				component.invalidateCache();
				YoukaisHomecoming.SPELL.getMerged().map.put(id.toString(), component);
				LOGGER.info("Loaded custom spell circle {} from {}", id, file.getPath());
			}
		} catch (Exception e) {
			LOGGER.warn("Failed to read spell circle file {}: {}", file.getPath(), e.getMessage());
		}
	}

}
