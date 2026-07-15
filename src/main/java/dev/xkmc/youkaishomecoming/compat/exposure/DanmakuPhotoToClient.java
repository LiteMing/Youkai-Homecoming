package dev.xkmc.youkaishomecoming.compat.exposure;

import dev.xkmc.l2serial.network.SerialPacketBase;
import dev.xkmc.l2serial.serialization.SerialClass;
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

	public DanmakuPhotoToClient() {
	}

	public DanmakuPhotoToClient(int totalErased, int score) {
		this.totalErased = totalErased;
		this.score = score;
	}

	@Override
	public void handle(NetworkEvent.Context context) {
		DanmakuPhotoOverlay.trigger(totalErased, score);
	}
}
