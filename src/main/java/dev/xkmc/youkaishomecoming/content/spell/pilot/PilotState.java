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

	public boolean timedOut() {
		return deadlineNanos > 0 && System.nanoTime() >= deadlineNanos;
	}
}
