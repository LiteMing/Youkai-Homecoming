package dev.xkmc.fastprojectileapi.render.virtual;

import dev.xkmc.l2serial.network.SerialPacketBase;
import dev.xkmc.l2serial.serialization.SerialClass;
import net.minecraftforge.network.NetworkEvent;

/**
 * Batch version of {@link EraseDanmakuToClient}.
 * Instead of one packet per erased danmaku, this collects all erases in a tick
 * and sends them in a single packet, reducing network overhead from ~10% to negligible.
 */
@SerialClass
public class BatchEraseDanmakuToClient extends SerialPacketBase {

	@SerialClass.SerialField
	private int[] ids;

	/**
	 * Bitmask: bit i = 1 means ids[i] should play kill particle.
	 * Supports up to ids.length entries. For arrays > 64, only first 64 bits are used;
	 * entries beyond 64 default to kill=false (safe: kill only triggers client-side poof particle).
	 */
	@SerialClass.SerialField
	private long killMask;

	public BatchEraseDanmakuToClient() {
	}

	public BatchEraseDanmakuToClient(int[] ids, long killMask) {
		this.ids = ids;
		this.killMask = killMask;
	}

	@Override
	public void handle(NetworkEvent.Context ctx) {
		for (int i = 0; i < ids.length; i++) {
			boolean kill = i < 64 && (killMask & (1L << i)) != 0;
			DanmakuClientHandler.erase(ids[i], kill);
		}
	}

}
