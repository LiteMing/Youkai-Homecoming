package dev.xkmc.youkaishomecoming.content.spell.certification.network;

import dev.xkmc.l2serial.network.SerialPacketBase;
import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.content.spell.certification.CertifiedSpellStorage;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * 客户端 -> 服务端：按 hash 请求获取符卡 84x128 材质快照
 */
@SerialClass
public class CertifiedSpellSnapshotRequestToServer extends SerialPacketBase {

	@SerialClass.SerialField
	public String hash = "";

	public CertifiedSpellSnapshotRequestToServer() {
	}

	public CertifiedSpellSnapshotRequestToServer(String hash) {
		this.hash = hash;
	}

	@Override
	public void handle(NetworkEvent.Context context) {
		ServerPlayer player = context.getSender();
		if (player == null) return;
		context.enqueueWork(() -> {
			if (hash == null || hash.isBlank()) return;
			byte[] bytes = CertifiedSpellStorage.loadSnapshot(player.server, hash);
			if (bytes != null && bytes.length > 0) {
				YoukaisHomecoming.HANDLER.toClientPlayer(new CertifiedSpellSnapshotToClient(hash, bytes), player);
			}
		});
		context.setPacketHandled(true);
	}
}
