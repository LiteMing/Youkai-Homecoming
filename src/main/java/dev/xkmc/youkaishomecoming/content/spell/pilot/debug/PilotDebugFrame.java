package dev.xkmc.youkaishomecoming.content.spell.pilot.debug;

import net.minecraft.world.phys.Vec3;

/**
 * One tick of pilot ego state for death-replay ring buffer / debug overlay.
 * No client imports (C1).
 */
public record PilotDebugFrame(
		int tick,
		Vec3 feet,
		Vec3 velocity,
		Vec3 force,
		double minClearance,
		boolean searchMode,
		int searchNodes,
		int threatCount,
		boolean hardHit,
		long pilotNanos
) {
}
