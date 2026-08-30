package dev.xkmc.youkaishomecoming.content.spell.physics;

import dev.xkmc.youkaishomecoming.content.spell.definition.DanmakuBounceConfig;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class DanmakuBounceResolver {

	public record BounceResult(
			Vec3 newPos,
			Vec3 newVel,
			int updatedBounces,
			boolean isGroundGliding,
			boolean erased
	) {}

	private DanmakuBounceResolver() {}

	public static BounceResult resolve(
			Vec3 currentPos,
			Vec3 currentVel,
			Vec3 hitNormal,
			@Nullable DanmakuBounceConfig config,
			int currentBounces,
			@Nullable Vec3 targetPos
	) {
		DanmakuBounceConfig cfg = config != null ? config.sanitize() : DanmakuBounceConfig.defaults();
		int nextBounces = currentBounces + 1;
		if (nextBounces > cfg.maxBounces()) {
			return new BounceResult(currentPos, Vec3.ZERO, nextBounces, false, true);
		}

		double speed = currentVel.length() * cfg.decay();
		if (speed < 1e-6) {
			speed = 1e-4;
		}

		// Ground glide check: only when mode is ground_glide and floor/upward normal is hit (n.y > 0.5)
		if (cfg.mode() == DanmakuBounceConfig.BounceMode.GROUND_GLIDE && hitNormal.y > 0.5) {
			Vec3 newPos = currentPos.add(0, cfg.groundOffset(), 0);
			Vec3 flatDir = new Vec3(currentVel.x, 0, currentVel.z);
			if (cfg.retarget() && targetPos != null) {
				Vec3 toTarget = targetPos.subtract(currentPos);
				flatDir = new Vec3(toTarget.x, 0, toTarget.z);
			}
			if (flatDir.lengthSqr() > 1e-8) {
				flatDir = flatDir.normalize();
			} else {
				flatDir = new Vec3(0, 0, 1);
			}
			Vec3 newVel = flatDir.scale(speed);
			return new BounceResult(newPos, newVel, nextBounces, true, false);
		}

		// Specular reflection
		Vec3 n = hitNormal.lengthSqr() > 1e-8 ? hitNormal.normalize() : new Vec3(0, 1, 0);
		double dot = currentVel.dot(n);
		Vec3 bounced;
		if (dot < 0) {
			bounced = currentVel.subtract(n.scale(2 * dot)).normalize().scale(speed);
		} else {
			bounced = currentVel.normalize().scale(speed);
		}

		if (cfg.retarget() && targetPos != null) {
			Vec3 toTarget = targetPos.subtract(currentPos);
			if (toTarget.lengthSqr() > 1e-8) {
				bounced = toTarget.normalize().scale(speed);
			}
		}

		Vec3 newPos = currentPos.add(n.scale(0.08));
		return new BounceResult(newPos, bounced, nextBounces, false, false);
	}
}
