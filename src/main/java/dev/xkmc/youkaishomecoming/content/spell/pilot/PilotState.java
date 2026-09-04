package dev.xkmc.youkaishomecoming.content.spell.pilot;

import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.CollisionOracle;
import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.SelfBoxModel;
import net.minecraft.world.phys.Vec3;

/**
 * Mutable-ish pilot ego state for one tick (rebuilt by consumer each tick).
 */
public final class PilotState {

	public Vec3 feet;
	public Vec3 velocity;
	public SelfBoxModel selfBox;
	public CollisionOracle oracle;
	public Vec3 anchor;
	/** Arena clamp for preview target (nullable = unbounded). */
	public net.minecraft.world.phys.AABB arena;
	/** Live HITBOX delta for player models (informational). */
	public float hitBoxDelta;
	public int tick;
	/** Deadline in System.nanoTime(); 0 = no deadline. */
	public long deadlineNanos;
	/** Soft wall clearance: probe radius (0 = off). */
	public double wallClearanceRadius;
	/** Soft wall clearance: max repulsion gain when fully safe. */
	public double wallClearanceGain;
	/**
	 * Threat clearance below this → wall force fully suppressed (necessary dodge first).
	 * Between danger and safe: linear ramp.
	 */
	public double wallClearanceDangerDist;
	/** Threat clearance at/above this → full wall force (claim free space). */
	public double wallClearanceSafeDist;

	public PilotState(Vec3 feet, Vec3 velocity, SelfBoxModel selfBox) {
		this.feet = feet;
		this.velocity = velocity;
		this.selfBox = selfBox;
		this.oracle = CollisionOracle.ALWAYS_FREE;
		this.anchor = feet;
		this.arena = null;
		this.hitBoxDelta = 0;
		this.tick = 0;
		this.deadlineNanos = 0;
		this.wallClearanceRadius = 0;
		this.wallClearanceGain = 0;
		this.wallClearanceDangerDist = 0.8;
		this.wallClearanceSafeDist = 2.5;
	}

	/** 0 = under fire (no wall bias), 1 = safe enough to claim space from walls. */
	public double wallSafetyFactor(double threatClearance) {
		if (wallClearanceGain <= 0 || wallClearanceRadius <= 0) return 0;
		double danger = wallClearanceDangerDist;
		double safe = Math.max(danger + 1e-3, wallClearanceSafeDist);
		if (threatClearance <= danger) return 0;
		if (threatClearance >= safe) return 1;
		return (threatClearance - danger) / (safe - danger);
	}

	/**
	 * Smooth inward force for the optional arena boundary. The arena is stored
	 * as a range of valid feet positions, so this deliberately does not inspect
	 * the body box again (the range was already derived with {@code safeFeetBounds}).
	 * A quadratic falloff gives the pilot time to turn while retaining a hard
	 * collision clamp for numerical overshoot.
	 */
	public Vec3 arenaClearanceForce() {
		if (arena == null || wallClearanceRadius <= 0 || wallClearanceGain <= 0) {
			return Vec3.ZERO;
		}
		Vec3 force = Vec3.ZERO;
		force = force.add(arenaFaceForce(feet.x - arena.minX, new Vec3(1, 0, 0)));
		force = force.add(arenaFaceForce(arena.maxX - feet.x, new Vec3(-1, 0, 0)));
		force = force.add(arenaFaceForce(feet.y - arena.minY, new Vec3(0, 1, 0)));
		force = force.add(arenaFaceForce(arena.maxY - feet.y, new Vec3(0, -1, 0)));
		force = force.add(arenaFaceForce(feet.z - arena.minZ, new Vec3(0, 0, 1)));
		force = force.add(arenaFaceForce(arena.maxZ - feet.z, new Vec3(0, 0, -1)));
		return force;
	}

	/** Negative candidate score near the arena faces, for search-mode ranking. */
	public double arenaClearancePenalty() {
		return arenaClearancePenalty(feet);
	}

	/** Negative candidate score near the arena faces for an arbitrary feet pose. */
	public double arenaClearancePenalty(Vec3 position) {
		if (arena == null || wallClearanceRadius <= 0 || wallClearanceGain <= 0) {
			return 0;
		}
		double penalty = 0;
		penalty += arenaFacePenalty(position.x - arena.minX);
		penalty += arenaFacePenalty(arena.maxX - position.x);
		penalty += arenaFacePenalty(position.y - arena.minY);
		penalty += arenaFacePenalty(arena.maxY - position.y);
		penalty += arenaFacePenalty(position.z - arena.minZ);
		penalty += arenaFacePenalty(arena.maxZ - position.z);
		return penalty;
	}

	private Vec3 arenaFaceForce(double gap, Vec3 inward) {
		if (gap >= wallClearanceRadius) return Vec3.ZERO;
		if (gap <= 0) return inward.scale(wallClearanceGain);
		double fraction = (wallClearanceRadius - gap) / wallClearanceRadius;
		return inward.scale(wallClearanceGain * fraction * fraction);
	}

	private double arenaFacePenalty(double gap) {
		if (gap >= wallClearanceRadius) return 0;
		double fraction = gap <= 0 ? 1 : (wallClearanceRadius - gap) / wallClearanceRadius;
		return -wallClearanceGain * fraction * fraction * 0.35;
	}

	public boolean timedOut() {
		return deadlineNanos > 0 && System.nanoTime() >= deadlineNanos;
	}
}
