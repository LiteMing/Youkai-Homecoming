package dev.xkmc.youkaishomecoming.content.spell.payment;

/**
 * Cast cost model (Phase 7+ balance): cost is driven by the spell duration
 * only — projectile volume no longer affects bomb/XP costs.
 * <p>
 * The first 100 ticks cost the 1-bomb / 5-level minimum. Each additional
 * 20-tick bucket costs 0.2 bomb / 1 level (partial buckets count as one).
 * Abstract units: 100 units = 1 bomb (5 XP levels baseline).
 */
public final class CastCost {
	private static final int BASELINE_DURATION_TICKS = 100;

	private CastCost() {
	}

	/** Bombs needed for a spell of the given duration (ticks), minimum 1. */
	public static double bombsForDuration(int durationTicks) {
		long excessTicks = Math.max(0, durationTicks - BASELINE_DURATION_TICKS);
		long steps = (excessTicks + 19L) / 20L;
		return 1.0 + steps * 0.2;
	}

	/** Abstract units (100 = 1 bomb / 5 XP levels), minimum 100. */
	public static long unitsForDuration(int durationTicks) {
		return Math.max(100, Math.round(bombsForDuration(durationTicks) * 100));
	}
}
