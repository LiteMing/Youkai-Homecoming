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
 * <p>
 * Vanilla projectile inertia follows MovesLikeMafuyu:
 * {@code getDeltaMovement().lengthSqr() < MIN_PROJECTILE_SPEED_SQR} → ignore.
 * No AT / {@code inGround} required — stuck arrows simply report near-zero velocity.
 * <p>
 * YH lasers and static danmaku are exempt (may be stationary but still lethal).
 */
public final class ThreatFilters {

	/**
	 * Same threshold as MovesLikeMafuyu AutoDodgeEvent.MIN_PROJECTILE_SPEED_SQR.
	 * Flying arrows are typically well above; stuck arrows sit near zero.
	 */
	public static final double MIN_PROJECTILE_SPEED_SQR = 0.0025;

	private ThreatFilters() {
	}

	/**
	 * @param self entity being protected (player / preview target / boss)
	 * @param projectile candidate threat entity
	 * @return true if pilot should treat it as a threat
	 */
	public static boolean isHostileTo(Entity self, Entity projectile) {
		if (self == null || projectile == null || projectile == self) return false;

		if (projectile instanceof SimplifiedProjectile sp) {
			if (!sp.isValid()) return false;
		} else if (!projectile.isAlive()) {
			return false;
		}

		// MLM-style: no meaningful motion → not a threat (arrows/tridents on ground)
		if (isInertProjectile(projectile)) return false;

		Entity owner = resolveOwner(projectile);
		if (owner != null) {
			if (sameEntity(self, owner)) return false;
			if (isAllied(self, owner)) return false;
		}

		if (projectile instanceof IYHDanmaku yh) {
			float dmg = damageOf(yh, projectile, self);
			if (dmg <= 0f) return false;
		}

		return true;
	}

	/**
	 * Vanilla projectiles only: require speed ≥ MLM threshold.
	 * YH lasers / SimplifiedProjectile danmaku may be still and still hurt.
	 */
	public static boolean isInertProjectile(Entity projectile) {
		if (projectile instanceof YHBaseLaserEntity) return false;
		if (projectile instanceof SimplifiedProjectile) return false;
		if (!(projectile instanceof Projectile)) return false;
		return projectile.getDeltaMovement().lengthSqr() < MIN_PROJECTILE_SPEED_SQR;
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

	private static boolean sameEntity(Entity a, Entity b) {
		if (a == b) return true;
		return a.getUUID().equals(b.getUUID());
	}

	private static boolean isAllied(Entity self, Entity owner) {
		if (self instanceof LivingEntity se && owner instanceof LivingEntity oe) {
			return se.isAlliedTo(oe) || oe.isAlliedTo(se);
		}
		return false;
	}

	private static float damageOf(IYHDanmaku yh, Entity projectile, Entity self) {
		if (projectile instanceof YHBaseDanmakuEntity d) return d.damage;
		if (projectile instanceof ItemDanmakuEntity d) return d.damage;
		if (projectile instanceof YHBaseLaserEntity d) return d.damage;
		try {
			return yh.damage(self);
		} catch (Throwable ignored) {
			return 1f;
		}
	}
}
