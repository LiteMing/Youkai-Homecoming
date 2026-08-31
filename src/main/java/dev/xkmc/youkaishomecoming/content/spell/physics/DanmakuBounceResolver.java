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

		// 1. Decompose incoming velocity into normal and tangential components
		double vNorm = currentVel.dot(n);
		Vec3 vNormalComp = n.scale(vNorm);
		Vec3 vTangentComp = currentVel.subtract(vNormalComp);

		// 2. Project world XYZ tangentOffset onto the surface tangent plane (eliminating any normal component)
		Vec3 worldTangentOffset = cfg.getTangentOffset();
		Vec3 projectedOffset = worldTangentOffset.subtract(n.scale(worldTangentOffset.dot(n)));

		// 3. Transform normal and tangent velocity components
		Vec3 transformedNormal = vNormalComp.scale(cfg.normalFactor());
		Vec3 transformedTangent = vTangentComp.scale(cfg.tangentFactor());
		Vec3 outgoing = transformedNormal.add(transformedTangent).add(projectedOffset);

		// 4. Optional output speed reset: preserves zero vector, only resets non-zero outgoing velocity
		if (cfg.outputSpeed().isPresent()) {
			double speed = cfg.outputSpeed().get();
			if (outgoing.lengthSqr() > 1e-8) {
				outgoing = outgoing.normalize().scale(speed);
			}
		}

		// 5. Retarget towards target if enabled
		if (cfg.retarget() && targetPos != null) {
			Vec3 toTarget = targetPos.subtract(hitPos);
			if (toTarget.lengthSqr() > 1e-8) {
				double curSpeed = outgoing.length();
				Vec3 candidateDir = toTarget.normalize();
				// Ensure retarget does not point into the surface
				if (candidateDir.dot(n) < 0) {
					// Project out the inward normal component
					Vec3 tangentTarget = candidateDir.subtract(n.scale(candidateDir.dot(n)));
					if (tangentTarget.lengthSqr() > 1e-8) {
						outgoing = tangentTarget.normalize().scale(curSpeed);
					}
				} else {
					outgoing = candidateDir.scale(curSpeed);
				}
			}
		}

		// 6. Normal safety guard: ensure outgoing velocity does not point into the surface (outgoing.dot(n) >= -1e-6)
		double outNormalDot = outgoing.dot(n);
		if (outNormalDot < -1e-6) {
			outgoing = outgoing.subtract(n.scale(outNormalDot));
		}

		// 7. Separation offset along outward normal vector
		Vec3 newPos = hitPos.add(n.scale(0.08));

		return new BounceResult(newPos, outgoing, nextBounces, false);
	}
}
