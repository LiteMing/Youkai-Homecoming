package dev.xkmc.youkaishomecoming.content.spell.analysis;

/**
 * Converts the large, fixed analyzer budgets into user-facing multiplier
 * settings while keeping the analyzer's internal long limits deterministic.
 */
public final class SpellBudgetScaling {

	private SpellBudgetScaling() {
	}

	public static long scale(long base, double multiplier) {
		if (base <= 0) {
			return 1L;
		}
		if (!Double.isFinite(multiplier)) {
			return multiplier > 0 ? Long.MAX_VALUE : 1L;
		}
		if (multiplier <= 0) {
			return 1L;
		}
		double scaled = base * multiplier;
		if (scaled >= Long.MAX_VALUE) {
			return Long.MAX_VALUE;
		}
		return Math.max(1L, Math.round(scaled));
	}
}
