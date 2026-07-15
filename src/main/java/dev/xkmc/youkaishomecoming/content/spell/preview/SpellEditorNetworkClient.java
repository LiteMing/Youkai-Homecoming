package dev.xkmc.youkaishomecoming.content.spell.preview;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.serialization.JsonOps;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class SpellEditorNetworkClient {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String EXPORT_DIR = "youkaishomecoming_spells";

	public static void save(SpellDefinition definition) {
		YoukaisHomecoming.HANDLER.toServer(SpellEditorSyncToServer.save(definition, false));
	}

	public static void saveAndReapply(SpellDefinition definition) {
		YoukaisHomecoming.HANDLER.toServer(SpellEditorSyncToServer.save(definition, true));
	}

	public static void importMarket(SpellDefinition definition) {
		YoukaisHomecoming.HANDLER.toServer(SpellEditorSyncToServer.importMarket(definition));
	}

	public static Path exportGlobal(SpellDefinition definition) throws IOException {
		YoukaisHomecoming.HANDLER.toServer(SpellEditorSyncToServer.exportGlobal(definition));
		return saveLocalExportCopy(definition);
	}

	public static void delete(ResourceLocation spellId) {
		YoukaisHomecoming.HANDLER.toServer(SpellEditorSyncToServer.delete(spellId));
	}

	private static Path saveLocalExportCopy(SpellDefinition definition) throws IOException {
		Path file = localExportPath(definition.id);
		Files.createDirectories(file.getParent());
		var json = SpellDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition)
				.getOrThrow(false, s -> {});
		Files.writeString(file, GSON.toJson(json), StandardCharsets.UTF_8);
		return file;
	}

	private static Path localExportPath(ResourceLocation id) {
		return FMLPaths.GAMEDIR.get()
				.resolve(EXPORT_DIR)
				.resolve(sanitizePathPart(id.getNamespace()))
				.resolve(sanitizePathPart(id.getPath()) + ".json");
	}

	private static String sanitizePathPart(String raw) {
		if (raw == null || raw.isBlank()) {
			return "untitled";
		}
		return raw.replaceAll("[^a-zA-Z0-9._-]+", "_");
	}

}
