package dev.xkmc.youkaishomecoming.content.spell.mover;

import dev.xkmc.fastprojectileapi.entity.ProjectileMovement;
import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.DanmakuHelper;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.CardHolder;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Attached mover that follows caster position and slowly rotates
 * its direction toward the target. Used by MasterSpark.
 * <p>
 * maxTurnRate: maximum degrees per tick the direction can change.
 * The actual turn is clamped to this rate per tick.
 */
@SerialClass
public class TrackingAttachedMover extends DanmakuMover {

	@SerialClass.SerialField
	private Vec3 direction;

	@SerialClass.SerialField
	private double maxTurnRate;

	@Deprecated
	public TrackingAttachedMover() {
	}

	public TrackingAttachedMover(Vec3 initialDirection, double maxTurnRateDegrees) {
		this.direction = DanmakuHelper.safeDirection(initialDirection, new Vec3(0, 0, 1));
		this.maxTurnRate = maxTurnRateDegrees;
	}

	@Override
	public ProjectileMovement move(MoverInfo info) {
		direction = DanmakuHelper.safeDirection(direction, new Vec3(0, 0, 1));

		// Get caster center position
		Vec3 casterPos = getCasterCenter(info);
		Vec3 delta = casterPos.subtract(info.prevPos());

		// Try to get target position and lerp direction toward it
		Vec3 targetPos = getTargetPos(info);
		if (targetPos != null) {
			Vec3 desiredDelta = targetPos.subtract(casterPos);
			if (DanmakuHelper.isFinite(desiredDelta) && desiredDelta.lengthSqr() > 1e-6) {
				Vec3 desired = DanmakuHelper.safeDirection(desiredDelta, direction);
				double angle = angleBetween(direction, desired);
				if (angle > 1e-6) {
					double maxRad = Math.toRadians(maxTurnRate);
					double perc = angle < maxRad ? 1.0 : maxRad / angle;
					direction = DanmakuHelper.safeDirection(slerp(direction, desired, perc), desired);
				}
			}
		}

		// Compute rotation from direction
		double d0 = direction.horizontalDistance();
		Vec3 rot = new Vec3(-Mth.atan2(direction.y, d0), -Mth.atan2(direction.x, direction.z), 0);
		return new ProjectileMovement(delta, rot);
	}

	private Vec3 getCasterCenter(MoverInfo info) {
		var e = info.self();
		if (e.asTraceable().getOwner() instanceof CardHolder holder) {
			return holder.center();
		} else if (e.asTraceable().getOwner() instanceof Player player) {
			return player.position().add(0, player.getBbHeight() / 2, 0);
		}
		return info.prevPos();
	}

	private Vec3 getTargetPos(MoverInfo info) {
		var e = info.self();
		if (e.asTraceable().getOwner() instanceof CardHolder holder) {
			return holder.target();
		}
		return null;
	}

	private static double angleBetween(Vec3 a, Vec3 b) {
		double dot = a.dot(b);
		dot = Math.max(-1.0, Math.min(1.0, dot));
		return Math.acos(dot);
	}

	private static Vec3 slerp(Vec3 a, Vec3 b, double t) {
		double dot = a.dot(b);
		dot = Math.max(-1.0, Math.min(1.0, dot));
		double theta = Math.acos(dot);
		if (theta < 1e-6) return a;
		double sinTheta = Math.sin(theta);
		double wa = Math.sin((1 - t) * theta) / sinTheta;
		double wb = Math.sin(t * theta) / sinTheta;
		return a.scale(wa).add(b.scale(wb));
	}

}
