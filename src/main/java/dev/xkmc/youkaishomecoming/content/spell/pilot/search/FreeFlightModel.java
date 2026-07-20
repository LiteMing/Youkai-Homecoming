package dev.xkmc.youkaishomecoming.content.spell.pilot.search;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * 6 axial + 8 body-diagonal directions × high/low speed + stay = 29 actions.
 */
public final class FreeFlightModel implements ActionModel {

	private static final Vec3[] DIRS = buildDirs();

	private static Vec3[] buildDirs() {
		List<Vec3> list = new ArrayList<>();
		// 6 axial
		list.add(new Vec3(1, 0, 0));
		list.add(new Vec3(-1, 0, 0));
		list.add(new Vec3(0, 1, 0));
		list.add(new Vec3(0, -1, 0));
		list.add(new Vec3(0, 0, 1));
		list.add(new Vec3(0, 0, -1));
		// 8 body diagonals
		for (int x = -1; x <= 1; x += 2) {
			for (int y = -1; y <= 1; y += 2) {
				for (int z = -1; z <= 1; z += 2) {
					list.add(new Vec3(x, y, z).normalize());
				}
			}
		}
		return list.toArray(new Vec3[0]);
	}

	@Override
	public List<Action> actions(PilotSearchNode parent, double highSpeed, double lowSpeed) {
		List<Action> out = new ArrayList<>(29);
		out.add(Action.stay());
		for (int i = 0; i < DIRS.length; i++) {
			Vec3 d = DIRS[i];
			// High-speed reverse prune: drop high branch if angle with prev > 90°
			boolean allowHigh = true;
			if (parent.depth > 0 && parent.velocity.lengthSqr() > 1e-8) {
				if (d.dot(parent.velocity.normalize()) < 0) {
					allowHigh = false;
				}
			}
			// Same path: no high/low switch — if parent chose high, children only high (and stay)
			if (parent.depth > 0 && parent.dirId >= 0) {
				if (parent.highSpeed) {
					if (allowHigh) {
						out.add(new Action(d.scale(highSpeed), true, i));
					}
				} else {
					out.add(new Action(d.scale(lowSpeed), false, i));
				}
			} else {
				out.add(new Action(d.scale(lowSpeed), false, i));
				if (allowHigh) {
					out.add(new Action(d.scale(highSpeed), true, i));
				}
			}
		}
		return out;
	}

	public static int directionCount() {
		return DIRS.length;
	}
}
