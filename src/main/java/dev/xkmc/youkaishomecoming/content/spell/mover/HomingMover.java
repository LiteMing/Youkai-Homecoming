package dev.xkmc.youkaishomecoming.content.spell.mover;

import dev.xkmc.fastprojectileapi.entity.ProjectileMovement;
import dev.xkmc.l2serial.serialization.SerialClass;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

@SerialClass
public class HomingMover extends DanmakuMover {

	@SerialClass.SerialField
	private int targetEntityId = -1;
	@SerialClass.SerialField
	private boolean hasFallbackTarget = false;
	@SerialClass.SerialField
	private Vec3 fallbackTargetPos = Vec3.ZERO;
	@SerialClass.SerialField
	private Vec3 fallbackVelocity = Vec3.ZERO;
	@SerialClass.SerialField
	private double strength;
	@SerialClass.SerialField
	private int delay;
	@SerialClass.SerialField
	private int duration;

	@Deprecated
	public HomingMover() {
	}

	public HomingMover(int targetEntityId, @Nullable Vec3 fallbackTargetPos, Vec3 fallbackVelocity,
					   double strength, int delay, int duration) {
		this.targetEntityId = targetEntityId;
		this.hasFallbackTarget = fallbackTargetPos != null;
		this.fallbackTargetPos = fallbackTargetPos == null ? Vec3.ZERO : fallbackTargetPos;
		this.fallbackVelocity = fallbackVelocity;
		this.strength = strength;
		this.delay = delay;
		this.duration = duration;
	}

	@Override
	public ProjectileMovement move(MoverInfo info) {
		Vec3 currentVel = info.prevVel();
		if (currentVel.lengthSqr() < 1e-8) {
			currentVel = fallbackVelocity;
		}
		if (!isActive(info.tick())) {
			return movementOf(currentVel, info.self().rot());
		}

		Vec3 targetPos = resolveTarget(info);
		if (targetPos == null) {
			return movementOf(currentVel, info.self().rot());
		}

		Vec3 toTarget = targetPos.subtract(info.prevPos());
		if (toTarget.lengthSqr() < 1e-8) {
			return movementOf(currentVel, info.self().rot());
		}

		double speed = currentVel.length();
		Vec3 targetDir = toTarget.normalize();
		Vec3 currentDir = currentVel.lengthSqr() > 1e-8 ? currentVel.normalize() : targetDir;
		double turn = Mth.clamp(strength, 0.0, 1.0);
		Vec3 newDir = turn >= 1.0 ? targetDir : currentDir.scale(1.0 - turn).add(targetDir.scale(turn));
		if (newDir.lengthSqr() < 1e-8) {
			newDir = targetDir;
		}
		Vec3 newVel = speed > 1e-8 ? newDir.normalize().scale(speed) : Vec3.ZERO;
		return movementOf(newVel, info.self().rot());
	}

	private boolean isActive(int tick) {
		if (tick < delay) return false;
		return duration < 0 || tick < delay + duration;
	}

	@Nullable
	private Vec3 resolveTarget(MoverInfo info) {
		Entity self = info.self().self();
		if (targetEntityId >= 0) {
			Entity target = self.level().getEntity(targetEntityId);
			if (target != null && target.isAlive()) {
				return target.position().add(0, target.getBbHeight() * 0.5, 0);
			}
		}
		return hasFallbackTarget ? fallbackTargetPos : null;
	}

	private static ProjectileMovement movementOf(Vec3 velocity, Vec3 fallbackRot) {
		if (velocity.lengthSqr() > 1e-8) {
			return ProjectileMovement.of(velocity);
		}
		return new ProjectileMovement(Vec3.ZERO, fallbackRot);
	}
}
