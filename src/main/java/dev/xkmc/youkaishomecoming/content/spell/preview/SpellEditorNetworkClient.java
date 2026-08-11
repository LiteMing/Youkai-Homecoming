package dev.xkmc.youkaishomecoming.content.spell.preview;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.serialization.JsonOps;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

public class SpellEditorNetworkClient {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String EXPORT_DIR = "youkaishomecoming_spells";

	public static boolean save(SpellDefinition definition) {
		return trySend(definition, () -> SpellEditorSyncToServer.save(definition, false));
	}

	public static boolean saveAndReapply(SpellDefinition definition) {
		return trySend(definition, () -> SpellEditorSyncToServer.save(definition, true));
	}

	public static boolean importMarket(SpellDefinition definition) {
		return trySend(definition, () -> SpellEditorSyncToServer.importMarket(definition));
	}

	public static Path exportGlobal(SpellDefinition definition) throws IOException {
		try {
			sendChunked(SpellEditorSyncToServer.exportGlobal(definition));
			return saveLocalExportCopy(definition);
		} catch (RuntimeException e) {
			throw new IOException("Spell definition cannot be encoded as JSON", e);
		}
	}

	public static void delete(ResourceLocation spellId) {
		YoukaisHomecoming.HANDLER.toServer(SpellEditorSyncToServer.delete(spellId));
	}

	/**
	 * Send an editor packet, splitting the definition into UTF-safe chunks when it
	 * exceeds the {@code writeUtf} limit (32767 chars). The server reassembles the
	 * chunks keyed by (player, transferId) before executing the action.
	 */
	private static void sendChunked(SpellEditorSyncToServer packet) {
		String json = packet.definitionJson;
		if (json.length() <= SpellPreviewChunkToClient.MAX_CHUNK_CHARS) {
			YoukaisHomecoming.HANDLER.toServer(packet);
			return;
		}
		int transferId = (packet.spellId.hashCode() ^ json.hashCode() ^ (int) System.nanoTime()) & 0x7fff_ffff;
		int total = (json.length() + SpellPreviewChunkToClient.MAX_CHUNK_CHARS - 1)
				/ SpellPreviewChunkToClient.MAX_CHUNK_CHARS;
		for (int i = 0; i < total; i++) {
			int from = i * SpellPreviewChunkToClient.MAX_CHUNK_CHARS;
			int to = Math.min(json.length(), from + SpellPreviewChunkToClient.MAX_CHUNK_CHARS);
			YoukaisHomecoming.HANDLER.toServer(
					SpellEditorSyncToServer.chunk(packet, transferId, i, total, json.substring(from, to)));
		}
		YoukaisHomecoming.LOGGER.info("[SpellEditor] sent {} in {} chunks ({} chars)",
				packet.spellId, total, json.length());
	}

	private static boolean trySend(SpellDefinition definition,
								   Supplier<SpellEditorSyncToServer> packetFactory) {
		try {
			sendChunked(packetFactory.get());
			return true;
		} catch (RuntimeException e) {
			YoukaisHomecoming.LOGGER.error("Failed to encode spell editor update for {}",
					definition.id, e);
			Minecraft mc = Minecraft.getInstance();
			if (mc.player != null) {
				String key = definition.hasLegacyTicker()
						? "youkaishomecoming.spell_editor.error.legacy_ticker"
						: "youkaishomecoming.spell_editor.error.encode_failed";
				mc.player.displayClientMessage(Component.translatable(key), false);
			}
			return false;
		}
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
