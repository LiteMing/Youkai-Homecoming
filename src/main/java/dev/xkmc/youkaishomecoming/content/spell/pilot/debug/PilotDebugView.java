package dev.xkmc.youkaishomecoming.content.spell.pilot.debug;

import dev.xkmc.youkaishomecoming.content.spell.pilot.predict.Threat;
import dev.xkmc.youkaishomecoming.content.spell.pilot.predict.ThreatFrame;
import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.ThreatSnapshot;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Latest-tick debug payload for viewport overlay (pure data, no GL).
 */
public final class PilotDebugView {

	public boolean enabled = true;
	public Vec3 feet = Vec3.ZERO;
	public Vec3 velocity = Vec3.ZERO;
	public Vec3 force = Vec3.ZERO;
	public Vec3 anchor = Vec3.ZERO;
	public boolean searchMode;
	public int searchNodes;
	public double minClearance = Double.POSITIVE_INFINITY;
	public int threatCount;
	/** Sample predicted polylines: each list is one threat's frame positions (capped). */
	public List<List<Vec3>> trajectories = List.of();
	/** Recent path of self feet (for trail). */
	public List<Vec3> selfTrail = new ArrayList<>();

	private static final int MAX_TRAJ = 24;
	private static final int MAX_TRAJ_LEN = 12;
	private static final int MAX_TRAIL = 40;

	public void updateFrom(ThreatSnapshot snap, Vec3 feet, Vec3 vel, Vec3 force, Vec3 anchor,
	                       boolean searchMode, int searchNodes, double clearance) {
		this.feet = feet;
		this.velocity = vel;
		this.force = force;
		this.anchor = anchor;
		this.searchMode = searchMode;
		this.searchNodes = searchNodes;
		this.minClearance = clearance;
		this.threatCount = snap.size();

		selfTrail.add(feet);
		while (selfTrail.size() > MAX_TRAIL) {
			selfTrail.remove(0);
		}

		List<List<Vec3>> traj = new ArrayList<>();
		int n = Math.min(MAX_TRAJ, snap.threats().size());
		for (int i = 0; i < n; i++) {
			Threat t = snap.threats().get(i);
			ThreatFrame[] frames = t.frames();
			int len = Math.min(MAX_TRAJ_LEN, frames.length);
			List<Vec3> line = new ArrayList<>(len);
			for (int f = 0; f < len; f++) {
				if (frames[f].active()) {
					line.add(frames[f].position());
				}
			}
			if (line.size() >= 2) traj.add(line);
		}
		this.trajectories = Collections.unmodifiableList(traj);
	}

	public void clear() {
		feet = velocity = force = anchor = Vec3.ZERO;
		searchMode = false;
		searchNodes = 0;
		minClearance = Double.POSITIVE_INFINITY;
		threatCount = 0;
		trajectories = List.of();
		selfTrail.clear();
	}
}
