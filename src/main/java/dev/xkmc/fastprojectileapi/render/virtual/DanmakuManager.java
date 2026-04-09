package dev.xkmc.fastprojectileapi.render.virtual;

import dev.xkmc.fastprojectileapi.entity.SimplifiedProjectile;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

public class DanmakuManager {

	/**
	 * Maximum danmaku per packet to stay well under Forge's 1MB (1,048,576 bytes) payload limit.
	 * Each danmaku serializes to ~120-200 bytes (Data fields + writeSpawnData).
	 * 2000 × 200 = 400KB, leaving ample headroom for l2serial overhead.
	 */
	private static final int MAX_PER_PACKET = 2000;

	// PE-3: Erase buffer. Collects erase requests during a tick, flushed at end.
	// Per-entity buffering is not needed because all virtual danmaku share the same
	// owner (the LivingCardHolder), so we buffer globally per flush cycle.
	private static final IntArrayList eraseIds = new IntArrayList();
	private static long eraseKillMask = 0;
	private static LivingEntity eraseUser = null;

	/**
	 * Override entity used as the tracking target for erase packets.
	 * When set, {@link #erase} ignores the {@code user} parameter and uses this instead.
	 * <p>
	 * This fixes a mismatch for DanmakuProxyEntity: spawn packets are sent via
	 * {@code toTrackingPlayers(proxy)}, but danmaku {@code getOwner()} returns
	 * the player (since {@code shooter()} returns ownerPlayer). A ServerPlayer does
	 * NOT track itself, so the caster never receives erase packets.
	 * Setting this override to the proxy entity ensures both spawn and erase packets
	 * are routed through the same tracking set.
	 */
	private static LivingEntity trackingOverride = null;

	/**
	 * Set the entity to use as the tracking target for erase packets during this tick.
	 * Call before tickAll() with the same entity used for send().
	 * Call with null after flushErases() to clear.
	 */
	public static void setTrackingOverride(@javax.annotation.Nullable LivingEntity entity) {
		trackingOverride = entity;
	}

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

	/**
	 * Buffer an erase request. Call {@link #flushErases()} at end of tick to send.
	 * <p>
	 * If {@link #trackingOverride} is set, it is used as the tracking target instead
	 * of {@code user}. This ensures erase packets are routed to the same player set
	 * that received the spawn packets (critical for DanmakuProxyEntity where the
	 * danmaku owner is a ServerPlayer that doesn't track itself).
	 */
	public static void erase(LivingEntity user, SimplifiedProjectile proj, boolean kill) {
		LivingEntity effectiveUser = trackingOverride != null ? trackingOverride : user;
		// If user changed (different owner entity), flush the previous batch first
		if (eraseUser != null && eraseUser != effectiveUser && !eraseIds.isEmpty()) {
			flushErases();
		}
		eraseUser = effectiveUser;
		int idx = eraseIds.size();
		eraseIds.add(proj.getId());
		if (kill && idx < 64) {
			eraseKillMask |= (1L << idx);
		}
	}

	/**
	 * Flush all buffered erase requests as a single batch packet.
	 * Call this at the end of tickDanmaku() and eraseAllDanmaku().
	 */
	public static void flushErases() {
		if (eraseIds.isEmpty() || eraseUser == null) {
			eraseIds.clear();
			eraseKillMask = 0;
			eraseUser = null;
			return;
		}
		YoukaisHomecoming.HANDLER.toTrackingPlayers(
				new BatchEraseDanmakuToClient(eraseIds.toIntArray(), eraseKillMask), eraseUser);
		eraseIds.clear();
		eraseKillMask = 0;
		eraseUser = null;
	}

}
