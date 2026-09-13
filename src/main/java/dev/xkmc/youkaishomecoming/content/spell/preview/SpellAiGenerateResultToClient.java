package dev.xkmc.youkaishomecoming.content.spell.preview;

import dev.xkmc.l2serial.network.SerialPacketBase;
import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SerialClass
public class SpellAiGenerateResultToClient extends SerialPacketBase {
	@SerialClass.SerialField public boolean success;
	@SerialClass.SerialField public String json = "";
	@SerialClass.SerialField public String message = "";
	@SerialClass.SerialField public int transferId;
	@SerialClass.SerialField public int totalChunks;
	@SerialClass.SerialField public int chunkIndex;

	@Deprecated public SpellAiGenerateResultToClient() {}

	public SpellAiGenerateResultToClient(int transferId, boolean success, String json, String message) {
		this.transferId = transferId;
		this.success = success;
		this.json = json == null ? "" : json;
		this.message = message == null ? "" : message;
	}

	public void sendTo(ServerPlayer player) {
		for (var packet : split()) YoukaisHomecoming.HANDLER.toClientPlayer(packet, player);
	}

	/** Deliver both original draft text and any repair diagnostics without truncation. */
	List<SpellAiGenerateResultToClient> split() {
		var jsonParts = splitText(json);
		var messageParts = splitText(message);
		int count = Math.max(jsonParts.size(), messageParts.size());
		if (count <= 1) return List.of(this);
		var packets = new ArrayList<SpellAiGenerateResultToClient>();
		for (int i = 0; i < count; i++) {
			var packet = new SpellAiGenerateResultToClient(transferId, success,
					i < jsonParts.size() ? jsonParts.get(i) : "", i < messageParts.size() ? messageParts.get(i) : "");
			packet.chunkIndex = i;
			packet.totalChunks = count;
			packets.add(packet);
		}
		return packets;
	}

	private static List<String> splitText(String body) {
		var parts = new ArrayList<String>();
		for (int from = 0; from < body.length();) {
			int to = Math.min(body.length(), from + SpellPreviewChunkToClient.MAX_CHUNK_CHARS);
			if (to < body.length() && Character.isHighSurrogate(body.charAt(to - 1)) && Character.isLowSurrogate(body.charAt(to))) to--;
			parts.add(body.substring(from, to));
			from = to;
		}
		return parts;
	}

	/** Network packets arrive in order, but separate completed requests can interleave. */
	static final class Collector {
		private final Map<Integer, Assembly> pending = new HashMap<>();
		SpellAiGenerateResultToClient accept(SpellAiGenerateResultToClient packet) {
			if (packet.totalChunks <= 1) return packet;
			if (packet.chunkIndex == 0) pending.put(packet.transferId, new Assembly(packet.totalChunks));
			var assembly = pending.get(packet.transferId);
			if (assembly == null) return null;
			if (assembly.total != packet.totalChunks || assembly.next != packet.chunkIndex) {
				pending.remove(packet.transferId);
				return null;
			}
			assembly.json.append(packet.json);
			assembly.message.append(packet.message);
			if (++assembly.next < assembly.total) return null;
			pending.remove(packet.transferId);
			return new SpellAiGenerateResultToClient(packet.transferId, packet.success, assembly.json.toString(), assembly.message.toString());
		}
		void clear() { pending.clear(); }
	}

	private static final class Assembly {
		final int total;
		final StringBuilder json = new StringBuilder();
		final StringBuilder message = new StringBuilder();
		int next;
		Assembly(int total) { this.total = total; }
	}

	@Override
	public void handle(NetworkEvent.Context context) {
		SpellPreviewClientHandler.onAiResult(this);
	}
}
