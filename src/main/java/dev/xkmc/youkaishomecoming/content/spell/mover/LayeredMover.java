package dev.xkmc.youkaishomecoming.content.spell.mover;

import dev.xkmc.fastprojectileapi.entity.ProjectileMovement;
import dev.xkmc.l2serial.serialization.SerialClass;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Additive mover: multiple sub-movers run simultaneously and their displacements are summed.
 * Each sub-mover computes its own position/velocity independently, and the final movement
 * is the sum of all sub-mover displacements relative to origin.
 *
 * This differs from CompositeMover which chains movers sequentially in time.
 */
@SerialClass
public class LayeredMover extends DanmakuMover {

	@SerialClass.SerialField
	private final ArrayList<DanmakuMover> layers = new ArrayList<>();

	@SerialClass.SerialField
	private Vec3 origin;

	public LayeredMover() {
		this.origin = Vec3.ZERO;
	}

	public LayeredMover(Vec3 origin, List<DanmakuMover> layers) {
		this.origin = origin;
		this.layers.addAll(layers);
	}

	public LayeredMover addLayer(DanmakuMover mover) {
		layers.add(mover);
		return this;
	}

	@Override
	public ProjectileMovement move(MoverInfo info) {
		Vec3 totalDisplacement = Vec3.ZERO;

		for (DanmakuMover layer : layers) {
			if (layer instanceof TargetPosMover tpm) {
				// Position-based mover: displacement = pos(tick) - origin
				Vec3 pos = tpm.pos(info);
				Vec3 displacement = pos.subtract(origin);
				totalDisplacement = totalDisplacement.add(displacement);
			} else {
				// Velocity-based mover: use its move() result directly as displacement
				ProjectileMovement pm = layer.move(info);
				totalDisplacement = totalDisplacement.add(pm.vec());
			}
		}

		// Final position = origin + sum of all displacements
		Vec3 finalPos = origin.add(totalDisplacement);
		Vec3 delta = finalPos.subtract(info.prevPos());

		if (delta.lengthSqr() > 1e-4) {
			return ProjectileMovement.of(delta);
		}
		return new ProjectileMovement(delta, info.self().rot());
	}
}
