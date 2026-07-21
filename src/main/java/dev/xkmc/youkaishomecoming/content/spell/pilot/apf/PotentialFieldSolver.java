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

		// Track nearest threat distance for wall-safety gate (dodge first, space second)
		double minThreatDist = Double.POSITIVE_INFINITY;

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

			// Always track nearest active frame at t=0 for safety gate
			if (frames[0].active()) {
				minThreatDist = Math.min(minThreatDist, distToThreat(selfCenter, frames[0]));
			}
			if (bestDist < Double.POSITIVE_INFINITY) {
				minThreatDist = Math.min(minThreatDist, bestDist);
			}

			if (bestAway == null || bestDist > profile.approachHorizon()) continue;
			if (!approaching && bestDist > 2.0) continue;

			// Repulsion ~ 1/d^2 on surface gap; large balls (bubble/moon) already use surface dist
			double d = Math.max(0.15, bestDist);
			// Mild size weight: bigger hitRadius → slightly stronger far-field attention
			float hr = frames[0].hitRadius();
			double sizeBoost = 1.0 + Math.min(2.0, hr); // r=0.2→1.2, r=1.6→2.6
			double mag = profile.repulseGain() * sizeBoost / (d * d);
			force = force.add(bestAway.scale(mag));
		}

		// Attraction to anchor
		Vec3 toAnchor = state.anchor.subtract(state.feet);
		double ad = toAnchor.length();
		if (ad > 1e-4) {
			force = force.add(toAnchor.scale(profile.attractGain() / Math.max(1.0, ad)));
		}

		// Soft wall clearance only when relatively safe — never steal necessary dodge
		double wallScale = state.wallSafetyFactor(minThreatDist);
		if (wallScale > 1e-4) {
			Vec3 wall = wallClearanceForce(state, box);
			// Cap wall contribution so it cannot dominate threat repulsion
			double wallCap = profile.maxForce() * 0.35 * wallScale;
			double wl = wall.length();
			if (wl > wallCap && wl > 1e-8) {
				wall = wall.scale(wallCap / wl);
			} else {
				wall = wall.scale(wallScale);
			}
			force = force.add(wall);
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

	/**
	 * Probe six axial directions; if a solid is within wallClearanceRadius, push away.
	 * Strength scales with (radius - freeDist) / radius * wallClearanceGain.
	 */
	private static Vec3 wallClearanceForce(PilotState state, SelfBoxModel box) {
		double radius = state.wallClearanceRadius;
		double gain = state.wallClearanceGain;
		if (radius <= 0 || gain <= 0 || state.oracle == null) return Vec3.ZERO;

		Vec3[] dirs = {
				new Vec3(1, 0, 0), new Vec3(-1, 0, 0),
				new Vec3(0, 1, 0), new Vec3(0, -1, 0),
				new Vec3(0, 0, 1), new Vec3(0, 0, -1)
		};
		Vec3 force = Vec3.ZERO;
		double step = 0.25;
		for (Vec3 dir : dirs) {
			double free = radius;
			for (double d = step; d <= radius + 1e-6; d += step) {
				AABB probe = box.bodyAt(state.feet.add(dir.scale(d)));
				if (!state.oracle.isFree(probe)) {
					free = d;
					break;
				}
			}
			if (free < radius) {
				double mag = gain * (radius - free) / radius;
				// Wall lies in +dir → push opposite
				force = force.add(dir.scale(-mag));
			}
		}
		return force;
	}

	private static double distToThreat(Vec3 selfCenter, ThreatFrame f) {
		if (f.isLaser() && f.orientation() != null && f.orientation().lengthSqr() > 1e-12) {
			return distPointToSegment(selfCenter, f.position(),
					f.position().add(f.orientation().normalize().scale(f.length()))) - f.hitRadius();
		}
		// Point / ball / bubble / giant: surface gap (not AABB cube corners)
		// hitRadius already includes scale (bubble r≈0.8, moon/giant-yinyang r≈1.6)
		return selfCenter.distanceTo(f.position()) - f.hitRadius();
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
