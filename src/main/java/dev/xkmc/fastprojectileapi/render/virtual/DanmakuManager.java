package dev.xkmc.fastprojectileapi.render.virtual;

import dev.xkmc.fastprojectileapi.entity.SimplifiedProjectile;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

public class DanmakuManager {

	/**
	 * Maximum danmaku per packet to stay well under Forge's 1MB (1,048,576 bytes) payload limit.
	 * Each danmaku serializes to ~120-200 bytes (Data fields + writeSpawnData).
	 * 2000 × 200 = 400KB, leaving ample headroom for l2serial overhead.
	 */
	private static final int MAX_PER_PACKET = 2000;

	public static void send(LivingEntity user, List<SimplifiedProjectile> proj) {
		int size = proj.size();
		if (size <= MAX_PER_PACKET) {
			YoukaisHomecoming.HANDLER.toTrackingPlayers(new DanmakuToClientPacket(proj), user);
		} else {
			// Split into multiple packets to avoid exceeding 1MB payload limit
			for (int i = 0; i < size; i += MAX_PER_PACKET) {
				int end = Math.min(i + MAX_PER_PACKET, size);
				YoukaisHomecoming.HANDLER.toTrackingPlayers(
						new DanmakuToClientPacket(proj.subList(i, end)), user);
			}
		}
	}

	public static void erase(LivingEntity user, SimplifiedProjectile proj, boolean kill) {
		YoukaisHomecoming.HANDLER.toTrackingPlayers(new EraseDanmakuToClient(proj, kill), user);
	}

}
