package dev.xkmc.youkaishomecoming.content.spell.payment;

/**
 * Cast cost model (Phase 7+ balance): cost is driven by the spell duration
 * only — projectile volume no longer affects bomb/XP costs.
 * <p>
 * Bomb baseline: 1 bomb for the first second, +0.2 bombs per second up to 5
 * seconds, +0.4 bombs per second beyond 5. Abstract units: 100 units = 1 bomb
 * (5 XP levels baseline), so the formula is converted directly to units.
 */
public final class CastCost {

	private CastCost() {
	}

	/** Bombs needed for a spell of the given duration (ticks), minimum 1. */
	public static double bombsForDuration(int durationTicks) {
		double seconds = Math.max(0, durationTicks / 20.0);
		return 1.0 + 0.2 * Math.min(seconds, 5.0) + 0.4 * Math.max(0, seconds - 5.0);
	}

	/** Abstract units (100 = 1 bomb / 5 XP levels), minimum 100. */
	public static long unitsForDuration(int durationTicks) {
		return Math.max(100, Math.round(bombsForDuration(durationTicks) * 100));
	}
}
