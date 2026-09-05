package dev.xkmc.youkaishomecoming.content.entity.danmaku;

import dev.xkmc.fastprojectileapi.collision.UserCacheHolder;
import dev.xkmc.fastprojectileapi.entity.AsyncProjectile;
import dev.xkmc.fastprojectileapi.entity.ParallelTicker;
import dev.xkmc.fastprojectileapi.entity.SimplifiedProjectile;
import dev.xkmc.fastprojectileapi.render.virtual.DanmakuManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * Reusable composition class that manages virtual danmaku for any {@code SpellRuntimeHost}.
 * <p>
 * Extracted from the duplicated danmaku management code in {@link YoukaiEntity}
 * and {@link DanmakuProxyEntity}. Holds the virtual danmaku list, handles ticking,
 * sending to clients, and erasing.
 * <p>
 * The caller provides two entity references:
 * <ul>
 *   <li>{@code trackingHost} - the entity whose tracking players receive danmaku packets
 *       (used by {@link DanmakuManager#send} and {@link DanmakuManager#setTrackingOverride}).</li>
 *   <li>{@code preheatEntity} - the entity around which the collision preheat cache is built
 *       (typically the shooter or the host itself).</li>
 * </ul>
 */
public class VirtualDanmakuHolder {

	private final ArrayList<AsyncProjectile> allDanmakus = new ArrayList<>();
	private ArrayList<AsyncProjectile> temp;
	private final ArrayList<SimplifiedProjectile> toBeSent = new ArrayList<>();
	private boolean removeDanmaku = false;
	private final UserCacheHolder cache = new UserCacheHolder();

	// ==================== Shoot ====================

	/**
	 * Accept a danmaku entity into the virtual list.
	 * Call this from {@code SpellRuntimeHost.shoot()}.
	 *
	 * @return true if the danmaku was added to the virtual list (AsyncProjectile),
	 *         false if it should be handled by the default path (added to world).
	 */
	public boolean shoot(Entity danmaku) {
		if (danmaku instanceof AsyncProjectile proj) {
			if (temp != null) temp.add(proj);
			else allDanmakus.add(proj);
			toBeSent.add(proj);
			return true;
		}
		return false;
	}

	/** Number of virtual projectiles currently tracked by this holder (certification
	 * active-threat accounting, design doc D6). */
	public int activeProjectileCount() {
		return allDanmakus.size();
	}

	// ==================== Tick ====================

	/**
	 * Tick all virtual danmaku and send new danmaku to clients.
	 *
	 * @param trackingHost  the entity whose tracking players receive packets
	 * @param preheatEntity the entity around which to build the collision preheat cache
	 */
	public void tickDanmaku(LivingEntity trackingHost, LivingEntity preheatEntity) {
		if (!(trackingHost.level() instanceof ServerLevel sl)) return;
		removeDanmaku = false;
		temp = new ArrayList<>();
		var preheatCache = cache.get(sl, preheatEntity);
		DanmakuManager.setTrackingOverride(trackingHost);
		ParallelTicker.tickAll(allDanmakus, () -> removeDanmaku, preheatCache);
		if (!removeDanmaku) {
			int w = 0;
			for (int i = 0; i < allDanmakus.size(); i++) {
				var e = allDanmakus.get(i);
				if ((e.isAddedToWorld() && !e.isRemoved()) || e.isValid()) {
					allDanmakus.set(w++, e);
				}
			}
			allDanmakus.subList(w, allDanmakus.size()).clear();
			allDanmakus.addAll(temp);
			DanmakuManager.send(trackingHost, toBeSent);
		}
		temp = null;
		toBeSent.clear();
		DanmakuManager.flushErases();
		DanmakuManager.setTrackingOverride(null);
	}

	// ==================== Erase ====================

	public void eraseAllDanmaku(LivingEntity trackingHost, @Nullable Player player) {
		eraseAllDanmakuAndCount(trackingHost, player);
	}

	public int eraseAllDanmakuAndCount(LivingEntity trackingHost, @Nullable Player player) {
		int erased = allDanmakus.size();
		DanmakuManager.setTrackingOverride(trackingHost);
		for (var e : allDanmakus) {
			if (player == null) e.markErased(true);
			else e.erase(player);
		}
		allDanmakus.clear();
		if (temp != null) temp.clear();
		toBeSent.clear();
		removeDanmaku = true;
		DanmakuManager.flushErases();
		DanmakuManager.setTrackingOverride(null);
		return erased;
	}

	public int eraseDanmakuInRadius(LivingEntity trackingHost, Vec3 center, double radius, @Nullable Player player) {
		double radiusSq = radius * radius;
		int erased = 0;
		int w = 0;
		DanmakuManager.setTrackingOverride(trackingHost);
		for (int i = 0; i < allDanmakus.size(); i++) {
			var e = allDanmakus.get(i);
			if (e.position().distanceToSqr(center) <= radiusSq) {
				if (player == null) e.markErased(true);
				else e.erase(player);
				erased++;
			} else {
				allDanmakus.set(w++, e);
			}
		}
		if (erased > 0) {
			allDanmakus.subList(w, allDanmakus.size()).clear();
			DanmakuManager.flushErases();
		}
		DanmakuManager.setTrackingOverride(null);
		return erased;
	}

	// ==================== Exposure compat ====================

	public void countDanmakuInFrustum(LivingEntity trackingHost,
			dev.xkmc.youkaishomecoming.compat.exposure.DanmakuFrustum frustum, int limit,
			dev.xkmc.youkaishomecoming.compat.exposure.EraseResult result,
			@Nullable net.minecraft.resources.ResourceLocation source) {
		int counted = 0;
		for (var e : allDanmakus) {
			if (counted >= limit) break;
			if (frustum.contains(e.position())) {
				String[] info = dev.xkmc.youkaishomecoming.compat.exposure.DanmakuCaptureService.getDanmakuTypeAndColor(e);
				result.record(info[0], info[1], source, trackingHost, e);
				counted++;
			}
		}
	}

	public void eraseDanmakuInFrustum(LivingEntity trackingHost, dev.xkmc.youkaishomecoming.compat.exposure.DanmakuFrustum frustum, @Nullable Player player, int limit) {
		DanmakuManager.setTrackingOverride(trackingHost);
		int erased = 0;
		int w = 0;
		for (int i = 0; i < allDanmakus.size(); i++) {
			var e = allDanmakus.get(i);
			if (erased < limit && frustum.contains(e.position())) {
				if (player == null) e.markErased(true);
				else e.erase(player);
				erased++;
			} else {
				allDanmakus.set(w++, e);
			}
		}
		if (erased > 0) {
			allDanmakus.subList(w, allDanmakus.size()).clear();
			DanmakuManager.flushErases();
		}
		DanmakuManager.setTrackingOverride(null);
	}

	// ==================== Cleanup ====================

	/**
	 * Erase all danmaku and clear internal state.
	 */
	public void cleanup(LivingEntity trackingHost) {
		eraseAllDanmaku(trackingHost, null);
	}

	/**
	 * @return the underlying {@link UserCacheHolder} for {@code EntityCachingUser} delegation.
	 */
	public UserCacheHolder entityCache() {
		return cache;
	}

	/**
	 * @return true if the virtual danmaku list is empty
	 */
	public boolean isEmpty() {
		return allDanmakus.isEmpty();
	}

	/**
	 * @return the current number of virtual danmaku
	 */
	public int size() {
		return allDanmakus.size();
	}

	// ==================== Serialization support ====================

	public void clearSentQueue() {
		toBeSent.clear();
	}
}
