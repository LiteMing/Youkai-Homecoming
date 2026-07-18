package dev.xkmc.youkaishomecoming.content.spell.market;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class SpellMarketStorage {

	private static final Logger LOGGER = LoggerFactory.getLogger("SpellMarket/Storage");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String DIR_NAME = "youkaishomecoming_spell_market";

	public static Path root(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve(DIR_NAME);
	}

	public static MarketImportManifest loadManifest(MinecraftServer server) {
		Path path = root(server).resolve("manifest.json");
		if (!Files.exists(path)) return new MarketImportManifest();
		try {
			MarketImportManifest manifest = GSON.fromJson(Files.readString(path), MarketImportManifest.class);
			return manifest == null ? new MarketImportManifest() : manifest;
		} catch (Exception e) {
			LOGGER.error("Failed to load market manifest {}; keeping market imports disabled", path, e);
			return new MarketImportManifest();
		}
	}

	public static void loadManagedSpells(MinecraftServer server, MarketImportManifest manifest) {
		for (MarketImportManifest.Entry entry : manifest.entries.values()) {
			ResourceLocation id = ResourceLocation.tryParse(entry.localSpellId);
			if (id == null || SpellRegistry.hasDefault(id)) continue;
			if (SpellRegistry.contains(id) && SpellRegistry.getOrigin(id) != SpellRegistry.Origin.MARKET) {
				LOGGER.error("Skipping market spell {} because a custom definition owns the ID", id);
				continue;
			}
			Path file = spellPath(server, id);
			try {
				String raw = Files.readString(file);
				var json = JsonParser.parseString(raw);
				SpellDefinition definition = SpellDefinition.CODEC.parse(JsonOps.INSTANCE, json)
						.getOrThrow(false, msg -> LOGGER.warn("Failed to parse {}: {}", file, msg));
				SpellMarketValidator.validate(raw, json, definition);
				SpellRegistry.registerMarket(definition);
			} catch (Exception e) {
				LOGGER.error("Failed to load managed market spell {}", file, e);
			}
		}
	}

	public static void saveSpell(MinecraftServer server, SpellDefinition definition, String rawJson) throws IOException {
		writeAtomic(spellPath(server, definition.id), rawJson);
	}

	public static void deleteSpell(MinecraftServer server, ResourceLocation id) throws IOException {
		Files.deleteIfExists(spellPath(server, id));
	}

	public static void saveManifest(MinecraftServer server, MarketImportManifest manifest) throws IOException {
		writeAtomic(root(server).resolve("manifest.json"), GSON.toJson(manifest));
	}

	private static Path spellPath(MinecraftServer server, ResourceLocation id) {
		return root(server).resolve("spells").resolve(id.getNamespace())
				.resolve(id.getPath().replace('/', '_') + ".json");
	}

	private static void writeAtomic(Path path, String content) throws IOException {
		Files.createDirectories(path.getParent());
		Path temp = path.resolveSibling(path.getFileName() + ".tmp");
		Files.writeString(temp, content, StandardCharsets.UTF_8);
		try {
			Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
