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
 * <p>
 * Lasers: push away from <b>nearest point on the segment</b> (not muzzle). Static beams
 * still repel within a generous radius — otherwise pilot walks along the beam into the hit.
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
			boolean isLaser = frames[0].isLaser();

			Vec3 prevPos = frames[0].position();
			// Include t=0 so static lasers (no motion across frames) still contribute
			int tMax = Math.min(horizon, frames.length);
			for (int t = 0; t < tMax; t++) {
				ThreatFrame f = frames[t];
				// Warn/fade: still geometric hazard for dodge (beam is visible / about to hurt)
				if (!f.active() && !f.isLaser()) continue;
				if (f.isLaser() && f.length() <= 0.01f) continue;

				double d = distToThreat(selfCenter, f);
				Vec3 away = awayFromThreat(selfCenter, f, state.tick, threat.entityId());

				if (t > 0) {
					Vec3 delta = f.position().subtract(prevPos);
					Vec3 toSelf = selfCenter.subtract(f.isLaser() ? nearestOnFrame(selfCenter, f) : f.position());
					if (delta.dot(toSelf) > 0 && d < bestDist) {
						bestDist = d;
						bestAway = away;
						approaching = true;
						prevPos = f.position();
						continue;
					}
				}
				if (d < bestDist) {
					bestDist = d;
					bestAway = away;
				}
				prevPos = f.position();
			}

			if (bestDist < Double.POSITIVE_INFINITY) {
				minThreatDist = Math.min(minThreatDist, bestDist);
			}

			// Distance gate: approachHorizon is also used as tick cap above; allow farther
			// approaching VANILLA projectiles (skeleton arrows) so mid-range still repels.
			// Lasers: static beams must still push within a long radius (not only 2m).
			double maxDist = Math.max(profile.approachHorizon(), isLaser ? 24.0 : 20.0);
			if (bestAway == null || bestDist > maxDist) continue;
			// Static point hazards only when close; lasers always count within maxDist
			if (!approaching && !isLaser && bestDist > 2.0) continue;

			// Repulsion ~ 1/d^2 on surface gap.
			// Special J: laser near-field was too steep (sizeBoost×1.8 + fall floor 0.08)
			// → maxForce + direction flip when beam sweeps past = main jitter source.
			double d = Math.max(0.15, bestDist);
			float hr = frames[0].hitRadius();
			double sizeBoost = 1.0 + Math.min(2.0, hr);
			if (isLaser) sizeBoost *= 1.35; // was 1.8 — still above point bullets
			double fall = approaching ? Math.max(d * d, d * 2.0) : (d * d);
			// Floor 0.30 (was 0.08): near-field force no longer instantly saturates maxForce
			if (isLaser && d < 3.0) {
				fall = Math.max(0.30, d * 0.85);
			}
			double mag = profile.repulseGain() * sizeBoost / fall;
			force = force.add(bestAway.scale(mag));
		}

		// Attraction to anchor
		Vec3 toAnchor = state.anchor.subtract(state.feet);
		double ad = toAnchor.length();
		if (ad > 1e-4) {
			force = force.add(toAnchor.scale(profile.attractGain() / Math.max(1.0, ad)));
		}
		if (state.gapPreference.lengthSqr() > 1e-10) {
			force = force.add(state.gapPreference.normalize().scale(profile.maxForce() * 0.24));
		}
		if (state.inputPreference.lengthSqr() > 1e-10) {
			force = force.add(state.inputPreference.normalize().scale(profile.maxForce() * 0.5));
		}

		// Soft wall clearance only when relatively safe — never steal necessary dodge
		double wallScale = state.wallSafetyFactor(minThreatDist);
		if (wallScale > 1e-4) {
			Vec3 wall = wallClearanceForce(state, box).add(state.arenaClearanceForce());
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

		if (state.grounded) {
			force = new Vec3(force.x, 0, force.z);
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

	/** Surface gap to point ball or laser capsule. */
	private static double distToThreat(Vec3 selfCenter, ThreatFrame f) {
		if (f.isLaser() && f.orientation() != null && f.orientation().lengthSqr() > 1e-12) {
			return distPointToSegment(selfCenter, f.position(),
					f.position().add(f.orientation().normalize().scale(f.length()))) - f.hitRadius();
		}
		return selfCenter.distanceTo(f.position()) - f.hitRadius();
	}

	/**
	 * Unit vector pointing away from the hazard surface.
	 * Lasers: away from nearest point on segment (perpendicular to beam when beside it).
	 * Points: away from center.
	 */
	private static Vec3 awayFromThreat(Vec3 selfCenter, ThreatFrame f, int tick, int id) {
		if (f.isLaser() && f.orientation() != null && f.orientation().lengthSqr() > 1e-12) {
			Vec3 nearest = nearestOnFrame(selfCenter, f);
			Vec3 away = selfCenter.subtract(nearest);
			if (away.lengthSqr() > 1e-8) return away.normalize();
			// Inside/on beam: push orthogonal to laser direction
			Vec3 dir = f.orientation().normalize();
			Vec3 ortho = dir.cross(new Vec3(0, 1, 0));
			if (ortho.lengthSqr() < 1e-8) ortho = dir.cross(new Vec3(1, 0, 0));
			if (ortho.lengthSqr() > 1e-8) return ortho.normalize();
			return randomAway(tick, id);
		}
		Vec3 away = selfCenter.subtract(f.position());
		if (away.lengthSqr() > 1e-8) return away.normalize();
		return randomAway(tick, id);
	}

	private static Vec3 nearestOnFrame(Vec3 selfCenter, ThreatFrame f) {
		if (f.isLaser() && f.orientation() != null && f.orientation().lengthSqr() > 1e-12) {
			Vec3 a = f.position();
			Vec3 b = a.add(f.orientation().normalize().scale(f.length()));
			Vec3 ab = b.subtract(a);
			double denom = ab.lengthSqr();
			if (denom < 1e-12) return a;
			double t = Math.max(0, Math.min(1, selfCenter.subtract(a).dot(ab) / denom));
			return a.add(ab.scale(t));
		}
		return f.position();
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
