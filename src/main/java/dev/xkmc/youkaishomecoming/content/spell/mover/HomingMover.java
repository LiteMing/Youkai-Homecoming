package dev.xkmc.youkaishomecoming.content.spell.mover;

import dev.xkmc.fastprojectileapi.entity.ProjectileMovement;
import dev.xkmc.l2serial.serialization.SerialClass;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Experimental smooth homing mover.
 *
 * <p>The target is resolved on the game thread by {@link #prepare(MoverOwner)}.
 * Movement itself is then a pure calculation and is safe for the parallel
 * danmaku ticker. If the live target disappears, the projectile keeps its
 * current direction. A fixed aim fallback is approached once, then passed.</p>
 */
@SerialClass
public final class HomingMover extends DanmakuMover {

	@SerialClass.SerialField
	private Vec3 targetPos = Vec3.ZERO;
	@SerialClass.SerialField
	private Vec3 initialDirection = new Vec3(0, 0, 1);
	@SerialClass.SerialField
	private double speed = 0.45;
	@SerialClass.SerialField
	private double turnRate = Math.toRadians(6);
	@SerialClass.SerialField
	private int delay;
	@SerialClass.SerialField
	private int targetEntityId = -1;
	@SerialClass.SerialField
	private boolean hasTargetReference;
	@Nullable
	@SerialClass.SerialField
	private UUID targetUuid;
	@SerialClass.SerialField
	private boolean fallbackApproachStarted;
	@SerialClass.SerialField
	private boolean fallbackComplete;

	/** Main-thread cache. Never read or write this from mover.move(). */
	private transient LivingEntity targetCache;
	private transient boolean liveTargetAvailable;

	@Deprecated
	public HomingMover() {
	}

	public HomingMover(Vec3 velocity, double speed, double turnRateDegrees, int delay,
						   @Nullable LivingEntity target, Vec3 targetPos) {
		this.initialDirection = velocity.lengthSqr() > 1.0e-8 ? velocity.normalize() : new Vec3(0, 0, 1);
		this.speed = Math.max(0, finiteOr(speed, 0.45));
		this.turnRate = Math.toRadians(clamp(finiteOr(turnRateDegrees, 6), 0, 180));
		this.delay = Math.max(0, delay);
		this.targetPos = finiteVec(targetPos) ? targetPos : Vec3.ZERO;
		if (target != null) {
			this.hasTargetReference = true;
			this.targetCache = target;
			this.liveTargetAvailable = true;
			this.targetEntityId = target.getId();
			this.targetUuid = target.getUUID();
		}
	}

	/**
	 * Refreshes the live target point on the main thread before a parallel tick.
	 * The client uses the network entity id; the server prefers the stable UUID.
	 */
	@Override
	public void prepare(MoverOwner owner) {
		Level level = owner.self().level();
		liveTargetAvailable = false;
		if (!isUsableTarget(targetCache, level)) {
			targetCache = null;
			Entity resolved = null;
			if (level instanceof ServerLevel server && targetUuid != null) {
				resolved = server.getEntity(targetUuid);
			}
			if (resolved == null && targetEntityId >= 0) {
				resolved = level.getEntity(targetEntityId);
			}
			if (resolved instanceof LivingEntity living && isUsableTarget(living, level)
					&& (targetUuid == null || targetUuid.equals(living.getUUID()))) {
				targetCache = living;
			}
		}
		if (targetCache != null) {
			liveTargetAvailable = true;
			targetPos = targetCache.position().add(0, targetCache.getBbHeight() / 2, 0);
		}
	}

	@Override
	public ProjectileMovement move(MoverInfo info) {
		Vec3 current = info.prevVel();
		Vec3 currentDirection = current.lengthSqr() > 1.0e-8 ? current.normalize() : initialDirection;
		if (info.tick() <= delay) {
			return ProjectileMovement.of(initialDirection.scale(speed));
		}
		if (hasTargetReference && !liveTargetAvailable) {
			return ProjectileMovement.of(currentDirection.scale(speed));
		}

		Vec3 delta = targetPos.subtract(info.prevPos());
		if (delta.lengthSqr() <= 1.0e-8) {
			return ProjectileMovement.of(currentDirection.scale(speed));
		}
		if (!hasTargetReference) {
			if (fallbackComplete) {
				return ProjectileMovement.of(currentDirection.scale(speed));
			}
			double alignment = currentDirection.dot(delta);
			if (alignment > 0) {
				fallbackApproachStarted = true;
			} else if (fallbackApproachStarted) {
				fallbackComplete = true;
				return ProjectileMovement.of(currentDirection.scale(speed));
			}
		}
		Vec3 desired = delta.normalize();
		Vec3 direction = turnTowards(currentDirection, desired, turnRate);
		return ProjectileMovement.of(direction.scale(speed));
	}

	private static Vec3 turnTowards(Vec3 current, Vec3 desired, double maxAngle) {
		if (maxAngle >= Math.PI - 1.0e-8) return desired;
		double dot = clamp(current.dot(desired), -1, 1);
		double angle = Math.acos(dot);
		if (angle <= maxAngle) return desired;

		Vec3 axis = current.cross(desired);
		if (axis.lengthSqr() <= 1.0e-12) {
			if (dot >= 0) return current;
			Vec3 reference = Math.abs(current.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
			axis = current.cross(reference).normalize();
		} else {
			axis = axis.normalize();
		}

		double cos = Math.cos(maxAngle);
		double sin = Math.sin(maxAngle);
		return current.scale(cos)
				.add(axis.cross(current).scale(sin))
				.normalize();
	}

	private static boolean isUsableTarget(@Nullable LivingEntity target, Level level) {
		return target != null && target.isAlive() && !target.isRemoved() && target.level() == level;
	}

	private static double finiteOr(double value, double fallback) {
		return Double.isFinite(value) ? value : fallback;
	}

	private static boolean finiteVec(Vec3 value) {
		return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}
}
