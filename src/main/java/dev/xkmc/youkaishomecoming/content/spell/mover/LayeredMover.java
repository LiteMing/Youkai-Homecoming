package dev.xkmc.youkaishomecoming.content.spell.mover;

import dev.xkmc.fastprojectileapi.entity.ProjectileMovement;
import dev.xkmc.l2serial.serialization.SerialClass;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Additive mover: multiple sub-movers run simultaneously and their displacements are summed.
 * Each sub-mover computes its position at tick t independently, and the final position
 * is origin + sum of (layer_pos(t) - layer_origin) for all layers.
 *
 * This is a TargetPosMover so it works correctly when nested inside other movers.
 * All sub-layers should ideally be TargetPosMover instances for correct behavior.
 * Non-TargetPosMover layers (like RotateMover) contribute zero displacement.
 */
@SerialClass
public class LayeredMover extends TargetPosMover {

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
	public Vec3 pos(MoverInfo info) {
		Vec3 totalOffset = Vec3.ZERO;

		for (DanmakuMover layer : layers) {
			if (layer instanceof TargetPosMover tpm) {
				// Position-based mover: offset = pos(tick) - origin
				// Since all sub-movers are created with the same origin,
				// pos(tick) - origin gives the pure displacement from that layer.
				Vec3 layerPos = tpm.pos(info);
				totalOffset = totalOffset.add(layerPos.subtract(origin));
			}
			// Non-TargetPosMover layers (e.g. RotateMover) only affect rotation, not position.
			// They contribute zero displacement in the layered context.
		}

		return origin.add(totalOffset);
	}
}
