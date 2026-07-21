package dev.xkmc.youkaishomecoming.content.spell.pilot.search;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Ground player action set: 8 horizontal dirs × high/low + jump + stay.
 * Prone (box height change) reserved; search uses horizontal + jump pulses.
 */
public final class GroundedModel implements ActionModel {

	private static final Vec3[] HORIZ = {
			new Vec3(1, 0, 0), new Vec3(-1, 0, 0),
			new Vec3(0, 0, 1), new Vec3(0, 0, -1),
			new Vec3(1, 0, 1).normalize(), new Vec3(1, 0, -1).normalize(),
			new Vec3(-1, 0, 1).normalize(), new Vec3(-1, 0, -1).normalize()
	};

	private final double jumpSpeed;

	public GroundedModel() {
		this(0.42);
	}

	public GroundedModel(double jumpSpeed) {
		this.jumpSpeed = jumpSpeed;
	}

	@Override
	public List<Action> actions(PilotSearchNode parent, double highSpeed, double lowSpeed) {
		List<Action> out = new ArrayList<>(20);
		out.add(Action.stay());
		// Jump pulse (only from low depth / not already rising hard)
		if (parent.velocity.y < 0.1) {
			out.add(new Action(new Vec3(0, jumpSpeed, 0), false, 100));
		}
		for (int i = 0; i < HORIZ.length; i++) {
			Vec3 d = HORIZ[i];
			boolean allowHigh = true;
			if (parent.depth > 0 && parent.velocity.lengthSqr() > 1e-8) {
				Vec3 hPrev = new Vec3(parent.velocity.x, 0, parent.velocity.z);
				if (hPrev.lengthSqr() > 1e-8 && d.dot(hPrev.normalize()) < 0) {
					allowHigh = false;
				}
			}
			if (parent.depth > 0 && parent.dirId >= 0 && parent.dirId < 100) {
				if (parent.highSpeed) {
					if (allowHigh) out.add(new Action(d.scale(highSpeed), true, i));
				} else {
					out.add(new Action(d.scale(lowSpeed), false, i));
				}
			} else {
				out.add(new Action(d.scale(lowSpeed), false, i));
				if (allowHigh) out.add(new Action(d.scale(highSpeed), true, i));
			}
		}
		return out;
	}
}
