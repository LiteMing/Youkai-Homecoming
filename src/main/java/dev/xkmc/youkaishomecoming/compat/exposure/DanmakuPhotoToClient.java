package dev.xkmc.youkaishomecoming.compat.exposure;

import dev.xkmc.l2serial.network.SerialPacketBase;
import dev.xkmc.l2serial.serialization.SerialClass;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server → Client packet: notifies the client that danmaku were erased by photography.
 * Triggers the photo overlay and score display on the client.
 */
@SerialClass
public class DanmakuPhotoToClient extends SerialPacketBase {

	@SerialClass.SerialField
	public int totalErased;

	@SerialClass.SerialField
	public int score;

	@SerialClass.SerialField
	public CompoundTag frame = new CompoundTag();

	public DanmakuPhotoToClient() {
	}

	public DanmakuPhotoToClient(int totalErased, int score, CompoundTag frame) {
		this.totalErased = totalErased;
		this.score = score;
		this.frame = frame == null ? new CompoundTag() : frame;
	}

	@Override
	public void handle(NetworkEvent.Context context) {
		DanmakuPhotoOverlay.trigger(totalErased, score, frame);
	}
}
