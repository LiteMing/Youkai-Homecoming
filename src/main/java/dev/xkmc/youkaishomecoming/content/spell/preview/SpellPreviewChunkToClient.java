package dev.xkmc.youkaishomecoming.content.spell.preview;

import dev.xkmc.l2serial.network.SerialPacketBase;
import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * One UTF-safe fragment of a spell definition for opening the preview editor.
 * Large definitions are split so each {@code chunk} stays under {@code writeUtf} max (32767).
 */
@SerialClass
public class SpellPreviewChunkToClient extends SerialPacketBase {

	/** Safe margin under FriendlyByteBuf.writeUtf(32767). */
	public static final int MAX_CHUNK_CHARS = 28_000;

	@SerialClass.SerialField
	public String spellId = "";
	@SerialClass.SerialField
	public int transferId;
	@SerialClass.SerialField
	public int chunkIndex;
	@SerialClass.SerialField
	public int totalChunks;
	@SerialClass.SerialField
	public String chunk = "";

	@Deprecated
	public SpellPreviewChunkToClient() {
	}

	private SpellPreviewChunkToClient(String spellId, int transferId, int chunkIndex, int totalChunks, String chunk) {
		this.spellId = spellId;
		this.transferId = transferId;
		this.chunkIndex = chunkIndex;
		this.totalChunks = totalChunks;
		this.chunk = chunk;
	}

	/**
	 * Encode definition and push one or more packets to the player, then client opens preview.
	 * Legacy-ticker definitions must not use this path — factory is lost on encode.
	 */
	public static void sendOpenPreview(ServerPlayer player, SpellDefinition definition) {
		if (definition.hasLegacyTicker()) {
			OpenSpellPreviewToClient.sendPreview(player, definition);
			return;
		}
		String id = definition.id != null ? definition.id.toString() : "";
		String body;
		try {
			var json = SpellDefinition.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, definition)
					.getOrThrow(false, s -> {});
			body = new com.google.gson.Gson().toJson(json);
		} catch (Exception e) {
			YoukaisHomecoming.LOGGER.error("[SpellPreview] encode failed for {}", id, e);
			return;
		}
		int transferId = (id.hashCode() ^ body.hashCode() ^ (int) System.nanoTime()) & 0x7fff_ffff;
		int total = Math.max(1, (body.length() + MAX_CHUNK_CHARS - 1) / MAX_CHUNK_CHARS);
		for (int i = 0; i < total; i++) {
			int from = i * MAX_CHUNK_CHARS;
			int to = Math.min(body.length(), from + MAX_CHUNK_CHARS);
			String part = body.substring(from, to);
			YoukaisHomecoming.HANDLER.toClientPlayer(
					new SpellPreviewChunkToClient(id, transferId, i, total, part), player);
		}
		if (total > 1) {
			YoukaisHomecoming.LOGGER.info("[SpellPreview] sent {} in {} chunks ({} chars) to {}",
					id, total, body.length(), player.getGameProfile().getName());
		}
	}

	@Override
	public void handle(NetworkEvent.Context context) {
		SpellPreviewClientHandler.onChunk(this);
	}
}
