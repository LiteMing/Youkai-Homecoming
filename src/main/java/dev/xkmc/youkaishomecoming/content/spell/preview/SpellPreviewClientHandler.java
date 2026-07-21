package dev.xkmc.youkaishomecoming.content.spell.preview;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRegistry;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SpellPreviewClientHandler {

	private static final Map<Integer, Assembly> ASSEMBLIES = new ConcurrentHashMap<>();

	public static void open(OpenSpellPreviewToClient packet) {
		Minecraft.getInstance().execute(() -> openOnClient(packet));
	}

	public static void onChunk(SpellPreviewChunkToClient packet) {
		Minecraft.getInstance().execute(() -> handleChunk(packet));
	}

	private static void openOnClient(OpenSpellPreviewToClient packet) {
		Minecraft mc = Minecraft.getInstance();
		if (packet.draft) {
			mc.setScreen(SpellPreviewScreen.createDraftEditor());
			return;
		}
		// Legacy single-packet path (definitionJson) if still present
		if (packet.definitionJson != null && !packet.definitionJson.isBlank()) {
			SpellDefinition def = parseJson(packet.spellId, packet.definitionJson);
			if (def != null) {
				openDefinition(def);
				return;
			}
		}
		// Id-only open (used for legacy_ticker spells — factory cannot survive JSON)
		if (packet.spellId != null && !packet.spellId.isBlank()) {
			ResourceLocation id = ResourceLocation.tryParse(packet.spellId);
			if (id != null) {
				SpellDefinition local = SpellRegistry.get(id);
				if (local != null) {
					openLocalDefinition(local);
					return;
				}
			}
		}
		if (mc.player != null) {
			mc.player.displayClientMessage(Component.literal("[YH] Unknown spell: " + packet.spellId), false);
		}
	}

	private static void handleChunk(SpellPreviewChunkToClient packet) {
		if (packet.totalChunks <= 0 || packet.chunkIndex < 0 || packet.chunkIndex >= packet.totalChunks) {
			YoukaisHomecoming.LOGGER.warn("[SpellPreview] bad chunk meta id={} idx={}/{}",
					packet.spellId, packet.chunkIndex, packet.totalChunks);
			return;
		}
		Assembly ass = ASSEMBLIES.computeIfAbsent(packet.transferId,
				id -> new Assembly(packet.spellId, packet.totalChunks));
		if (ass.total != packet.totalChunks || !ass.spellId.equals(packet.spellId)) {
			// Stale / overlapping transfer — replace
			ass = new Assembly(packet.spellId, packet.totalChunks);
			ASSEMBLIES.put(packet.transferId, ass);
		}
		ass.parts[packet.chunkIndex] = packet.chunk != null ? packet.chunk : "";
		ass.received++;
		if (ass.received < ass.total) return;

		ASSEMBLIES.remove(packet.transferId);
		StringBuilder sb = new StringBuilder(ass.total * SpellPreviewChunkToClient.MAX_CHUNK_CHARS);
		for (int i = 0; i < ass.total; i++) {
			if (ass.parts[i] == null) {
				YoukaisHomecoming.LOGGER.warn("[SpellPreview] missing chunk {}/{} for {}",
						i, ass.total, ass.spellId);
				return;
			}
			sb.append(ass.parts[i]);
		}
		SpellDefinition def = parseJson(ass.spellId, sb.toString());
		if (def == null) {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player != null) {
				mc.player.displayClientMessage(
						Component.literal("[YH] Failed to decode spell: " + ass.spellId), false);
			}
			return;
		}
		openDefinition(def);
	}

	private static void openDefinition(SpellDefinition definition) {
		// Do not overwrite a live legacy_ticker definition with a decoded empty shell
		if (definition.hasLegacyTicker()) {
			SpellDefinition local = SpellRegistry.get(definition.id);
			if (local != null && local.hasLegacyTicker()) {
				openLocalDefinition(local);
				return;
			}
		}
		SpellRegistry.register(definition);
		Minecraft.getInstance().setScreen(new SpellPreviewScreen(definition));
	}

	/** Open preview without re-registering (keeps non-serializable factory intact). */
	private static void openLocalDefinition(SpellDefinition definition) {
		Minecraft.getInstance().setScreen(new SpellPreviewScreen(definition));
	}

	private static SpellDefinition parseJson(String spellId, String body) {
		try {
			var json = JsonParser.parseString(body);
			return SpellDefinition.CODEC.parse(JsonOps.INSTANCE, json)
					.resultOrPartial(msg -> YoukaisHomecoming.LOGGER.warn(
							"Failed to open spell preview {}: {}", spellId, msg))
					.orElse(null);
		} catch (Exception e) {
			YoukaisHomecoming.LOGGER.warn("Failed to decode spell preview {}", spellId, e);
			return null;
		}
	}

	private static final class Assembly {
		final String spellId;
		final int total;
		final String[] parts;
		int received;

		Assembly(String spellId, int total) {
			this.spellId = spellId != null ? spellId : "";
			this.total = total;
			this.parts = new String[total];
		}
	}
}
