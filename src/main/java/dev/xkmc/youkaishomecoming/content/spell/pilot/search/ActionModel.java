package dev.xkmc.youkaishomecoming.content.spell.pilot.search;

import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Discrete action vocabulary for spatiotemporal search.
 * FreeFlightModel: 14 dirs × high/low + stay = 29.
 * GroundedModel supplies the equivalent terrain-aware player action set.
 */
public interface ActionModel {

	record Action(Vec3 velocity, boolean highSpeed, int dirId) {
		public static Action stay() {
			return new Action(Vec3.ZERO, false, -1);
		}
	}

	List<Action> actions(PilotSearchNode parent, double highSpeed, double lowSpeed);

	/** Unique low-cost directions used by the first search pass. */
	default List<Vec3> directionSeeds() {
		return List.of();
	}

	default boolean supportsVerticalMovement() {
		return true;
	}

	/** Pose-dependent self box height factor (prone etc.). Default 1. */
	default double boxHeightScale(PilotSearchNode node) {
		return 1.0;
	}
}
