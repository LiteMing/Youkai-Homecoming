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
	}

	public boolean timedOut() {
		return deadlineNanos > 0 && System.nanoTime() >= deadlineNanos;
	}
}
