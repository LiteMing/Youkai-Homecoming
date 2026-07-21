package dev.xkmc.youkaishomecoming.content.spell.pilot.threat;

import dev.xkmc.fastprojectileapi.entity.SimplifiedProjectile;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.IYHDanmaku;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.ItemDanmakuEntity;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.YHBaseDanmakuEntity;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.YHBaseLaserEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Shared hostility filters for pilot threat collection.
 * <p>
 * Intentionally does <b>not</b> call {@code IYHDanmaku.shouldHurt} /
 * {@code YoukaiEntity.shouldHurt}: those depend on server combat target lists
 * that are often empty/incomplete on the client, which would drop all enemy
 * danmaku (e.g. star_sapphire). Pilot only needs:
 * <ul>
 *   <li>not my own / allied bullets</li>
 *   <li>not zero-damage VFX bullets</li>
 *   <li>not stuck / zero-momentum vanilla projectiles (arrows, tridents…)</li>
 *   <li>still valid</li>
 * </ul>
 */
public final class ThreatFilters {

	/**
	 * Below this horizontal speed² a vanilla projectile is treated as inert.
	 * ~0.05 blocks/tick; stuck arrows often still report tiny residual motion.
	 */
	private static final double STATIONARY_SPEED_SQR = 0.0025;

	private ThreatFilters() {
	}

	/**
	 * @param self entity being protected (player / preview target / boss)
	 * @param projectile candidate threat entity
	 * @return true if pilot should treat it as a threat
	 */
	public static boolean isHostileTo(Entity self, Entity projectile) {
		if (self == null || projectile == null || projectile == self) return false;

		// Virtual client danmaku may not be "alive" in world terms; use isValid for SP
		if (projectile instanceof SimplifiedProjectile sp) {
			if (!sp.isValid()) return false;
		} else if (!projectile.isAlive()) {
			return false;
		}

		// Stuck / settled vanilla projectiles (in-ground arrows, tridents, etc.)
		// YH lasers may be stationary but still lethal — never apply this to lasers.
		if (isInertProjectile(projectile)) return false;

		Entity owner = resolveOwner(projectile);
		if (owner != null) {
			if (sameEntity(self, owner)) return false;
			if (isAllied(self, owner)) return false;
		}

		// YH: drop pure VFX (damage field 0). Do not use shouldHurt(owner,self).
		if (projectile instanceof IYHDanmaku yh) {
			float dmg = damageOf(yh, projectile, self);
			if (dmg <= 0f) return false;
		}

		return true;
	}

	/**
	 * True when a vanilla projectile no longer threatens the player.
	 * Not applied to YH lasers or SimplifiedProjectile danmaku.
	 */
	public static boolean isInertProjectile(Entity projectile) {
		if (projectile instanceof YHBaseLaserEntity) return false;
		if (projectile instanceof SimplifiedProjectile) return false;
		if (!(projectile instanceof Projectile)) return false;

		// Arrows / spectral arrows / tridents: official stuck flag (AT-public)
		if (projectile instanceof AbstractArrow arrow) {
			if (arrow.inGround) return true;
			// Shake/pickup residual: nearly no horizontal motion + on ground
			if (arrow.onGround() && horizontalSpeedSqr(arrow) <= STATIONARY_SPEED_SQR) {
				return true;
			}
		}

		// Other throwables (snowball, egg, etc.): freeze when almost stopped
		double speed2 = projectile.getDeltaMovement().lengthSqr();
		if (speed2 <= STATIONARY_SPEED_SQR) return true;
		if (projectile.onGround() && horizontalSpeedSqr(projectile) <= STATIONARY_SPEED_SQR) {
			return true;
		}
		return false;
	}

	private static double horizontalSpeedSqr(Entity e) {
		Vec3 v = e.getDeltaMovement();
		return v.x * v.x + v.z * v.z;
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
			// Unknown YH type without synced damage: keep as threat
			return 1f;
		}
	}
}
