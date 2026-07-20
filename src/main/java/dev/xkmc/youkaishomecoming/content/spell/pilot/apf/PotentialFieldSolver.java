package dev.xkmc.youkaishomecoming.content.spell.pilot.apf;

import dev.xkmc.youkaishomecoming.content.spell.pilot.PilotProfile;
import dev.xkmc.youkaishomecoming.content.spell.pilot.PilotState;
import dev.xkmc.youkaishomecoming.content.spell.pilot.predict.Threat;
import dev.xkmc.youkaishomecoming.content.spell.pilot.predict.ThreatFrame;
import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.SelfBoxModel;
import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.SweptCollision;
import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.ThreatSnapshot;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Artificial potential field: repulsion from predicted closest approach of approaching threats,
 * attraction to anchor. Output clamped + deadzone + low-pass damping via previous velocity.
 */
public final class PotentialFieldSolver {

	private Vec3 filtered = Vec3.ZERO;
	/** Last raw force before velocity mapping (for debug arrow). */
	private Vec3 lastForce = Vec3.ZERO;

	public Vec3 lastForce() {
		return lastForce;
	}

	public Vec3 solve(ThreatSnapshot snapshot, PilotState state, PilotProfile profile) {
		Vec3 force = Vec3.ZERO;
		SelfBoxModel box = state.selfBox;
		AABB self = box.hardAt(state.feet);
		Vec3 selfCenter = SweptCollision.center(self);

		int horizon = Math.min(snapshot.horizon(), (int) Math.ceil(profile.approachHorizon()));
		for (Threat threat : snapshot.threats()) {
			ThreatFrame[] frames = threat.frames();
			if (frames.length == 0) continue;

			// Predicted closest approach within horizon
			double bestDist = Double.POSITIVE_INFINITY;
			Vec3 bestAway = null;
			boolean approaching = false;

			Vec3 prevPos = frames[0].position();
			for (int t = 1; t < Math.min(horizon, frames.length); t++) {
				ThreatFrame f = frames[t];
				if (!f.active()) continue;
				double d = distToThreat(selfCenter, f);
				Vec3 delta = f.position().subtract(prevPos);
				// Approaching if threat moves toward self
				Vec3 toSelf = selfCenter.subtract(f.position());
				if (delta.dot(toSelf) > 0 && d < bestDist) {
					bestDist = d;
					bestAway = toSelf.lengthSqr() > 1e-8 ? toSelf.normalize() : randomAway(state.tick, threat.entityId());
					approaching = true;
				} else if (d < bestDist) {
					bestDist = d;
					bestAway = toSelf.lengthSqr() > 1e-8 ? toSelf.normalize() : null;
				}
				prevPos = f.position();
			}

			if (bestAway == null || bestDist > profile.approachHorizon()) continue;
			if (!approaching && bestDist > 2.0) continue;

			// Repulsion ~ 1/d^2 near field
			double d = Math.max(0.15, bestDist);
			double mag = profile.repulseGain() / (d * d);
			force = force.add(bestAway.scale(mag));
		}

		// Attraction to anchor
		Vec3 toAnchor = state.anchor.subtract(state.feet);
		double ad = toAnchor.length();
		if (ad > 1e-4) {
			force = force.add(toAnchor.scale(profile.attractGain() / Math.max(1.0, ad)));
		}

		// Clamp
		double len = force.length();
		if (len > profile.maxForce()) {
			force = force.scale(profile.maxForce() / len);
		}

		// Deadzone
		if (force.lengthSqr() < profile.deadzone() * profile.deadzone()) {
			force = Vec3.ZERO;
		}

		// Low-pass damping vs previous filtered output
		filtered = filtered.scale(profile.damping()).add(force.scale(1.0 - profile.damping()));
		if (filtered.lengthSqr() < profile.deadzone() * profile.deadzone()) {
			filtered = Vec3.ZERO;
		}
		lastForce = filtered;

		// Map force to desired velocity (speed-capped)
		double speed = Math.min(profile.highSpeed(), filtered.length());
		if (speed < 1e-6) return Vec3.ZERO;
		return filtered.normalize().scale(speed);
	}

	public void reset() {
		filtered = Vec3.ZERO;
		lastForce = Vec3.ZERO;
	}

	private static double distToThreat(Vec3 selfCenter, ThreatFrame f) {
		if (f.isLaser() && f.orientation() != null) {
			return distPointToSegment(selfCenter, f.position(),
					f.position().add(f.orientation().normalize().scale(f.length()))) - f.hitRadius();
		}
		AABB b = f.bounds();
		// Distance from point to AABB
		double dx = Math.max(0, Math.max(b.minX - selfCenter.x, selfCenter.x - b.maxX));
		double dy = Math.max(0, Math.max(b.minY - selfCenter.y, selfCenter.y - b.maxY));
		double dz = Math.max(0, Math.max(b.minZ - selfCenter.z, selfCenter.z - b.maxZ));
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	private static double distPointToSegment(Vec3 p, Vec3 a, Vec3 b) {
		Vec3 ab = b.subtract(a);
		double denom = ab.lengthSqr();
		if (denom < 1e-12) return p.distanceTo(a);
		double t = Math.max(0, Math.min(1, p.subtract(a).dot(ab) / denom));
		return p.distanceTo(a.add(ab.scale(t)));
	}

	/** Deterministic pseudo-random unit vector (golden angle) to break symmetry. */
	private static Vec3 randomAway(int tick, int id) {
		double g = 2.399963229728653; // golden angle
		double a = (tick * 0.618 + id) * g;
		double y = ((id * 0.37 + tick * 0.11) % 2.0) - 1.0;
		double r = Math.sqrt(Math.max(0, 1 - y * y));
		return new Vec3(Math.cos(a) * r, y, Math.sin(a) * r);
	}
}
