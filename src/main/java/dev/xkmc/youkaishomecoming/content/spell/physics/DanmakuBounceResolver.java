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
			Vec3 hitPos,
			Vec3 currentVel,
			Vec3 hitNormal,
			@Nullable DanmakuBounceConfig config,
			int currentBounces,
			@Nullable Vec3 targetPos
	) {
		DanmakuBounceConfig cfg = config != null ? config.sanitize() : DanmakuBounceConfig.defaults();
		int nextBounces = currentBounces + 1;
		if (nextBounces > cfg.maxBounces()) {
			return new BounceResult(hitPos, Vec3.ZERO, nextBounces, true);
		}

		Vec3 n = hitNormal.lengthSqr() > 1e-8 ? hitNormal.normalize() : new Vec3(0, 1, 0);

		// 1. Decompose into normal and tangential velocity components
		double vNorm = currentVel.dot(n);
		Vec3 vNormalComp = n.scale(vNorm);
		Vec3 vTangentComp = currentVel.subtract(vNormalComp);

		// 2. Apply normal factor and tangent factor
		Vec3 transformedNormal = vNormalComp.scale(cfg.normalFactor());
		Vec3 transformedTangent = vTangentComp.scale(cfg.tangentFactor());
		Vec3 outgoing = transformedNormal.add(transformedTangent);

		// 3. Optional output speed reset
		if (cfg.outputSpeed().isPresent()) {
			double speed = cfg.outputSpeed().get();
			if (outgoing.lengthSqr() > 1e-8) {
				outgoing = outgoing.normalize().scale(speed);
			} else {
				outgoing = n.scale(speed);
			}
		}

		// 4. Retarget towards target if enabled
		if (cfg.retarget() && targetPos != null) {
			Vec3 toTarget = targetPos.subtract(hitPos);
			if (toTarget.lengthSqr() > 1e-8) {
				double curSpeed = outgoing.length();
				outgoing = toTarget.normalize().scale(curSpeed);
			}
		}

		// 5. Normal separation to stay on the incident side of the surface
		Vec3 newPos = hitPos.add(n.scale(0.08));

		return new BounceResult(newPos, outgoing, nextBounces, false);
	}
}
