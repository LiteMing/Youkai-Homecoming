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

import java.lang.reflect.Field;

/**
 * Shared hostility filters for pilot threat collection.
 * <p>
 * Stuck vanilla projectiles (arrows/tridents on ground) must be dropped before
 * any provider chain runs. {@link #isInertProjectile} uses:
 * <ol>
 *   <li>{@code AbstractArrow.inGround} via reflection (package-private; no AT rebuild)</li>
 *   <li>MLM-style {@code lengthSqr() < MIN_PROJECTILE_SPEED_SQR}</li>
 *   <li>near-zero horizontal speed while {@code onGround()}</li>
 * </ol>
 * YH lasers and static danmaku are exempt (may be stationary but still lethal).
 */
public final class ThreatFilters {

	/**
	 * Same threshold as MovesLikeMafuyu AutoDodgeEvent.MIN_PROJECTILE_SPEED_SQR.
	 * Flying arrows are typically well above; pure-zero stuck arrows sit near zero.
	 * Client residual motion after block-hit may still exceed this — use {@code inGround}.
	 */
	public static final double MIN_PROJECTILE_SPEED_SQR = 0.0025;

	/** Cached {@code AbstractArrow.inGround}; null if lookup failed. */
	@Nullable
	private static final Field ARROW_IN_GROUND = resolveInGroundField();

	private ThreatFilters() {
	}

	@Nullable
	private static Field resolveInGroundField() {
		try {
			// Official mappings: inGround; SRG: f_36703_
			for (String name : new String[]{"inGround", "f_36703_"}) {
				try {
					Field f = AbstractArrow.class.getDeclaredField(name);
					f.setAccessible(true);
					return f;
				} catch (NoSuchFieldException ignored) {
				}
			}
		} catch (Throwable ignored) {
		}
		return null;
	}

	/** True when arrow/trident is stuck in a block (vanilla inGround). */
	public static boolean isArrowInGround(AbstractArrow arrow) {
		if (ARROW_IN_GROUND == null) return false;
		try {
			return ARROW_IN_GROUND.getBoolean(arrow);
		} catch (Throwable ignored) {
			return false;
		}
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

		// Stuck / settled vanilla projectiles must not enter threat set
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
	 * Vanilla projectiles only: stuck arrows/tridents and near-zero motion throwables.
	 * YH lasers / SimplifiedProjectile danmaku may be still and still hurt.
	 */
	public static boolean isInertProjectile(Entity projectile) {
		if (projectile instanceof YHBaseLaserEntity) return false;
		if (projectile instanceof SimplifiedProjectile) return false;
		if (!(projectile instanceof Projectile)) return false;

		// Authoritative stuck flag (arrow / spectral / trident)
		if (projectile instanceof AbstractArrow arrow) {
			if (isArrowInGround(arrow)) return true;
			// Shake / residual after impact: almost no horizontal motion while grounded
			if (arrow.onGround() && horizontalSpeedSqr(arrow) < MIN_PROJECTILE_SPEED_SQR) {
				return true;
			}
		}

		// MLM-style: no meaningful motion
		if (projectile.getDeltaMovement().lengthSqr() < MIN_PROJECTILE_SPEED_SQR) return true;
		// Settled throwables that still report a tiny residual vector
		if (projectile.onGround() && horizontalSpeedSqr(projectile) < MIN_PROJECTILE_SPEED_SQR) {
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
			return 1f;
		}
	}
}
