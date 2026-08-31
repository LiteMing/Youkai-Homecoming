package dev.xkmc.youkaishomecoming.content.spell.physics;

import dev.xkmc.youkaishomecoming.content.spell.definition.DanmakuBounceConfig;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class DanmakuBounceResolver {

	public record BounceResult(
			Vec3 newPos,
			Vec3 newVel,
			int updatedBounces,
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
			return new BounceResult(currentPos, Vec3.ZERO, nextBounces, true);
		}

		double speed = currentVel.length() * cfg.decay();

		// Specular reflection
		Vec3 n = hitNormal.lengthSqr() > 1e-8 ? hitNormal.normalize() : new Vec3(0, 1, 0);
		double dot = currentVel.dot(n);
		Vec3 bounced;
		if (dot < 0) {
			bounced = currentVel.subtract(n.scale(2 * dot));
		} else {
			bounced = currentVel;
		}
		if (bounced.lengthSqr() > 1e-8) {
			bounced = bounced.normalize().scale(speed);
		} else {
			bounced = n.scale(speed);
		}

		if (cfg.retarget() && targetPos != null) {
			Vec3 toTarget = targetPos.subtract(currentPos);
			if (toTarget.lengthSqr() > 1e-8) {
				bounced = toTarget.normalize().scale(speed);
			}
		}

		Vec3 newPos = currentPos.add(n.scale(0.08));
		return new BounceResult(newPos, bounced, nextBounces, false);
	}
}
