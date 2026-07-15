package dev.xkmc.youkaishomecoming.content.spell.action;

import dev.xkmc.fastprojectileapi.entity.SimplifiedProjectile;
import dev.xkmc.fastprojectileapi.render.virtual.DanmakuManager;
import dev.xkmc.youkaishomecoming.content.capability.GrazeCapability;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.DanmakuProxyEntity;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.EntitySpellProxyEntity;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.IYHDanmaku;
import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntimeHost;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.CardHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

final class EnemyDanmakuEraser {

	private static final double ACTIVE_DANMAKU_HOST_SEARCH_RANGE = 128.0;

	private EnemyDanmakuEraser() {
	}

	static int erase(CardHolder holder, Vec3 center, double radius, boolean sessionsOnly) {
		var unwrapped = unwrap(holder);
		var self = unwrapped.self();
		if (!(self.level() instanceof ServerLevel sl)) return 0;
		double range = Math.max(0, radius);
		Entity owner = ownerOf(unwrapped);
		Player ownerPlayer = owner instanceof Player player ? player : null;
		int erased = 0;
		if (ownerPlayer != null) {
			erased += GrazeCapability.HOLDER.get(ownerPlayer).eraseEnemyDanmakuInRadius(center, range, sessionsOnly);
		} else if (owner instanceof YoukaiEntity youkai && !sessionsOnly) {
			erased += eraseOtherYoukaiDanmaku(sl, youkai, center, range);
		}
		if (!sessionsOnly) {
			if (ownerPlayer == null) {
				erased += eraseProxyDanmaku(sl, owner, center, range);
			}
			erased += eraseEntityDanmaku(sl, owner, center, range);
		}
		if (erased > 0) DanmakuManager.flushErases();
		return erased;
	}

	private static CardHolder unwrap(CardHolder holder) {
		if (holder instanceof TrailCardHolder trail) {
			return unwrap(trail.delegate());
		}
		return holder;
	}

	private static Entity ownerOf(CardHolder holder) {
		if (holder instanceof SpellRuntimeHost host) {
			LivingEntity owner = host.owner();
			if (owner != null) return owner;
		}
		return holder.self();
	}

	private static int eraseOtherYoukaiDanmaku(ServerLevel sl, YoukaiEntity owner, Vec3 center, double radius) {
		Set<UUID> erasedHosts = new HashSet<>();
		int erased = 0;
		erased += eraseOtherYoukaiDanmaku(sl, owner, center, radius, hostSearchArea(owner.position(), radius), erasedHosts);
		erased += eraseOtherYoukaiDanmaku(sl, owner, center, radius, hostSearchArea(center, radius), erasedHosts);
		return erased;
	}

	private static int eraseOtherYoukaiDanmaku(ServerLevel sl, YoukaiEntity owner, Vec3 center, double radius,
											  AABB hostArea, Set<UUID> erasedHosts) {
		int erased = 0;
		for (var youkai : sl.getEntitiesOfClass(YoukaiEntity.class, hostArea)) {
			if (!erasedHosts.add(youkai.getUUID())) continue;
			if (youkai == owner || owner.isAlliedTo(youkai)) continue;
			if (!youkai.targets.contains(owner)) continue;
			erased += youkai.eraseDanmakuInRadius(center, radius, null);
		}
		return erased;
	}

	private static int eraseProxyDanmaku(ServerLevel sl, Entity owner, Vec3 center, double radius) {
		Set<UUID> erasedHosts = new HashSet<>();
		int erased = 0;
		erased += eraseProxyDanmaku(sl, owner, center, radius, hostSearchArea(owner.position(), radius), erasedHosts);
		erased += eraseProxyDanmaku(sl, owner, center, radius, hostSearchArea(center, radius), erasedHosts);
		return erased;
	}

	private static int eraseProxyDanmaku(ServerLevel sl, Entity owner, Vec3 center, double radius,
										AABB hostArea, Set<UUID> erasedHosts) {
		int erased = 0;
		for (var proxy : sl.getEntitiesOfClass(DanmakuProxyEntity.class, hostArea)) {
			if (!erasedHosts.add(proxy.getUUID())) continue;
			LivingEntity proxyOwner = proxy.owner();
			if (proxyOwner == owner || proxyOwner != null && proxyOwner.isAlliedTo(owner)) continue;
			erased += proxy.eraseDanmakuInRadius(center, radius, owner instanceof Player player ? player : null);
		}
		for (var proxy : sl.getEntitiesOfClass(EntitySpellProxyEntity.class, hostArea)) {
			if (!erasedHosts.add(proxy.getUUID())) continue;
			LivingEntity proxyOwner = proxy.owner();
			if (proxyOwner == owner || proxyOwner != null && proxyOwner.isAlliedTo(owner)) continue;
			erased += proxy.eraseDanmakuInRadius(center, radius, owner instanceof Player player ? player : null);
		}
		return erased;
	}

	private static int eraseEntityDanmaku(ServerLevel sl, Entity owner, Vec3 center, double radius) {
		double radiusSq = radius * radius;
		int erased = 0;
		for (var projectile : sl.getEntitiesOfClass(SimplifiedProjectile.class, area(center, radius))) {
			if (!(projectile instanceof IYHDanmaku)) continue;
			Entity projectileOwner = projectile.getOwner();
			if (projectileOwner == owner || projectileOwner != null && projectileOwner.isAlliedTo(owner)) continue;
			if (radius > 0 && projectile.position().distanceToSqr(center) > radiusSq) continue;
			projectile.markErased(true);
			erased++;
		}
		return erased;
	}

	private static AABB area(Vec3 center, double radius) {
		return new AABB(center, center).inflate(Math.max(0.1, radius));
	}

	private static AABB hostSearchArea(Vec3 center, double radius) {
		double range = Math.max(ACTIVE_DANMAKU_HOST_SEARCH_RANGE, radius);
		return AABB.ofSize(center, range * 2, range * 2, range * 2);
	}

}
