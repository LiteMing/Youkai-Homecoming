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

	/** Transport text unchanged; the editor owns JSON parsing and node recovery on import. */
	List<SpellAiGenerateResultToClient> split() {
		String body = success ? json : message;
		if (body.length() <= SpellPreviewChunkToClient.MAX_CHUNK_CHARS) return List.of(this);
		var packets = new ArrayList<SpellAiGenerateResultToClient>();
		for (int from = 0; from < body.length();) {
			int to = Math.min(body.length(), from + SpellPreviewChunkToClient.MAX_CHUNK_CHARS);
			if (to < body.length() && Character.isHighSurrogate(body.charAt(to - 1)) && Character.isLowSurrogate(body.charAt(to))) to--;
			String part = body.substring(from, to);
			var packet = new SpellAiGenerateResultToClient(transferId, success, success ? part : "", success ? "" : part);
			packet.chunkIndex = packets.size();
			packets.add(packet);
			from = to;
		}
		for (var packet : packets) packet.totalChunks = packets.size();
		return packets;
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
			assembly.body.append(packet.success ? packet.json : packet.message);
			if (++assembly.next < assembly.total) return null;
			pending.remove(packet.transferId);
			String body = assembly.body.toString();
			return new SpellAiGenerateResultToClient(packet.transferId, packet.success, packet.success ? body : "", packet.success ? "" : body);
		}
		void clear() { pending.clear(); }
	}

	private static final class Assembly {
		final int total;
		final StringBuilder body = new StringBuilder();
		int next;
		Assembly(int total) { this.total = total; }
	}

	@Override
	public void handle(NetworkEvent.Context context) {
		SpellPreviewClientHandler.onAiResult(this);
	}
}
