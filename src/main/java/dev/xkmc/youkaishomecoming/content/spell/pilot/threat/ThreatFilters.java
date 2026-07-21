package dev.xkmc.youkaishomecoming.content.spell.pilot.threat;

import dev.xkmc.fastprojectileapi.entity.SimplifiedProjectile;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.IYHDanmaku;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.ItemDanmakuEntity;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.YHBaseDanmakuEntity;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.YHBaseLaserEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import org.jetbrains.annotations.Nullable;

/**
 * Shared hostility filters for pilot threat collection.
 * Drops self-owned, allied, and non-damaging projectiles so pilot does not dodge own bullets.
 */
public final class ThreatFilters {

	private ThreatFilters() {
	}

	/**
	 * @param self entity being protected (player / preview target / boss)
	 * @param projectile candidate threat entity
	 * @return true if pilot should treat it as a threat
	 */
	public static boolean isHostileTo(Entity self, Entity projectile) {
		if (self == null || projectile == null || projectile == self) return false;
		if (!projectile.isAlive()) return false;
		if (projectile instanceof SimplifiedProjectile sp && !sp.isValid()) return false;

		Entity owner = resolveOwner(projectile);
		if (owner != null) {
			if (owner == self || owner.getUUID().equals(self.getUUID())) return false;
			if (isAllied(self, owner)) return false;
		}

		// YH danmaku: respect shouldHurt + zero damage
		if (projectile instanceof IYHDanmaku yh) {
			if (!yh.shouldHurt(owner, self)) return false;
			float dmg = damageOf(yh, projectile, self);
			if (dmg <= 0f) return false;
		}

		return true;
	}

	@Nullable
	public static Entity resolveOwner(Entity projectile) {
		if (projectile instanceof Projectile p) {
			return p.getOwner();
		}
		if (projectile instanceof SimplifiedProjectile sp) {
			return sp.getOwner();
		}
		return null;
	}

	private static boolean isAllied(Entity self, Entity owner) {
		if (self instanceof LivingEntity se && owner instanceof LivingEntity oe) {
			return se.isAlliedTo(oe) || oe.isAlliedTo(se);
		}
		return false;
	}

	private static float damageOf(IYHDanmaku yh, Entity projectile, Entity self) {
		// Prefer field on concrete types (stable on client virtual danmaku)
		if (projectile instanceof YHBaseDanmakuEntity d) return d.damage;
		if (projectile instanceof ItemDanmakuEntity d) return d.damage;
		if (projectile instanceof YHBaseLaserEntity d) return d.damage;
		try {
			return yh.damage(self);
		} catch (Throwable ignored) {
			return 1f; // if damage() needs server state, keep as potential threat
		}
	}
}
