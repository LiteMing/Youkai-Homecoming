package dev.xkmc.youkaishomecoming.content.spell.certification.network;

import dev.xkmc.l2serial.network.SerialPacketBase;
import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.client.render.SpellCardTextureCache;
import net.minecraftforge.network.NetworkEvent;

/**
 * 服务端 -> 客户端：下发符卡 84x128 材质快照
 */
@SerialClass
public class CertifiedSpellSnapshotToClient extends SerialPacketBase {

	@SerialClass.SerialField
	public String hash = "";

	@SerialClass.SerialField
	public byte[] pngBytes = new byte[0];

	public CertifiedSpellSnapshotToClient() {
	}

	public CertifiedSpellSnapshotToClient(String hash, byte[] pngBytes) {
		this.hash = hash;
		this.pngBytes = pngBytes == null ? new byte[0] : pngBytes;
	}

	@Override
	public void handle(NetworkEvent.Context context) {
		context.enqueueWork(() -> {
			if (hash != null && !hash.isBlank() && pngBytes.length > 0) {
				SpellCardTextureCache.onSnapshotReceived(hash, pngBytes);
			}
		});
		context.setPacketHandled(true);
	}
}
