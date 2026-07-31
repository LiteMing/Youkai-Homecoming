package dev.xkmc.youkaishomecoming.content.spell.pilot.search;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * One node in (position, tick) search space.
 */
public final class PilotSearchNode {

	public final Vec3 feet;
	public final Vec3 velocity;
	public final int depth;
	public final int dirId;
	public final boolean highSpeed;
	public final double pathScore; // MaxiMin: min of safety along path
	public final boolean dead;
	@Nullable
	public final PilotSearchNode parent;
	/** First-step velocity from root (what we return if this branch wins). */
	public final Vec3 firstStepVel;

	public PilotSearchNode(Vec3 feet, Vec3 velocity, int depth, int dirId, boolean highSpeed,
	                       double pathScore, boolean dead, @Nullable PilotSearchNode parent, Vec3 firstStepVel) {
		this.feet = feet;
		this.velocity = velocity;
		this.depth = depth;
		this.dirId = dirId;
		this.highSpeed = highSpeed;
		this.pathScore = pathScore;
		this.dead = dead;
		this.parent = parent;
		this.firstStepVel = firstStepVel;
	}

	public static PilotSearchNode root(Vec3 feet, Vec3 velocity) {
		return new PilotSearchNode(feet, velocity, 0, -1, false, Double.POSITIVE_INFINITY, false, null, Vec3.ZERO);
	}
}
