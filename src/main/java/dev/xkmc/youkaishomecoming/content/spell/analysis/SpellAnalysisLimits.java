package dev.xkmc.youkaishomecoming.content.spell.analysis;

import dev.xkmc.youkaishomecoming.init.data.YHModConfig;

/**
 * Budget limits for spell analysis (design doc §10 hard limits).
 * <p>
 * MARKET keeps the historical SpellMarketValidator numbers; CERTIFICATION defaults
 * are first-pass values subject to Phase 7 balance tuning. The four headline
 * certification limits (maxSpawnPerTick / maxPeakAlive / maxProjectileTicks /
 * maxHookExecutions) are wired into {@code YHModConfig} section "certification"
 * (INV-8: config is the only tuning source); {@link #certification()} reads them
 * with a safe fallback to the spec defaults before config load.
 *
 * @param maxPhases              max phase count
 * @param maxActions             max action node count
 * @param maxDepth               max nesting depth
 * @param maxRepeat              max literal count for repeat/burst/fire (market only)
 * @param maxTotalProjectiles    total projectile budget (market) / structural (cert)
 * @param maxShooters            max spawn_shooter count
 * @param maxLifetime            max lifetime/duration/health value (ticks)
 * @param maxExpressionLength    max string/expression length (characters)
 * @param maxSpawnPerTick        certification: max projectiles spawned in a single tick
 * @param maxPeakAlive           certification: conservative concurrent-alive upper bound
 * @param maxProjectileTicks     certification: conservative total projectile-ticks
 * @param maxHookExecutions      certification: conservative total hook executions
 * @param maxHitsPerProjectile   certification: per-projectile max hits for CONTINUE hooks
	 * @param certificationWindowTicks certification projection window; callers may
	 *                                narrow it to a finite health-plan duration
 */
public record SpellAnalysisLimits(
		int maxPhases,
		int maxActions,
		int maxDepth,
		int maxRepeat,
		long maxTotalProjectiles,
		int maxShooters,
		int maxLifetime,
		int maxExpressionLength,
		int maxSpawnPerTick,
		int maxPeakAlive,
		long maxProjectileTicks,
		long maxHookExecutions,
		int maxHitsPerProjectile,
		long certificationWindowTicks
) {

	/**
	 * Returns the same limits with a narrower finite projection window.  Health-plan
	 * certification uses this to project work over the segment's real duration
	 * instead of the server-wide fallback window.
	 */
	public SpellAnalysisLimits withCertificationWindow(long windowTicks) {
		return new SpellAnalysisLimits(maxPhases, maxActions, maxDepth, maxRepeat,
				maxTotalProjectiles, maxShooters, maxLifetime, maxExpressionLength,
				maxSpawnPerTick, maxPeakAlive, maxProjectileTicks, maxHookExecutions,
				maxHitsPerProjectile, Math.max(0, windowTicks));
	}

	// --- certification spec defaults (single source for YHModConfig defineInRange) ---

	/**
	 * Certification hard-limit defaults are deliberately LOOSE: the analyzer must
	 * never reject reasonable spells out of the box (the weakest built-in spell,
	 * sunny_milk, conservatively peaks at ~9600 concurrent projectiles and ~69M
	 * projectile-ticks). Real limits are set by yhdev in the YHModConfig
	 * "certification" section — the config is the only tuning source (INV-8).
	 */
	public static final int DEFAULT_MAX_SPAWN_PER_TICK = 100_000;
	public static final int DEFAULT_MAX_PEAK_ALIVE = 1_000_000;
	public static final long DEFAULT_MAX_PROJECTILE_TICKS = 10_000_000_000L;
	public static final long DEFAULT_MAX_HOOK_EXECUTIONS = 10_000_000L;
	public static final int DEFAULT_MAX_HITS_PER_PROJECTILE = 4;
	/** 7200 ticks = 360s > the 300s max certification duration (design doc §5.2). */
	public static final long DEFAULT_CERTIFICATION_WINDOW_TICKS = 7200;

	public static SpellAnalysisLimits market() {
		return new SpellAnalysisLimits(64, 4096, 32, 256, 8192, 256, 12000, 512,
				0, 0, 0, 0, 1, 0);
	}

	/** Certification limits from YHModConfig, falling back to spec defaults before config load. */
	public static SpellAnalysisLimits certification() {
		try {
			var common = YHModConfig.COMMON;
			double multiplier = common.certificationBudgetMultiplier.get();
			return new SpellAnalysisLimits(64, 4096, 32, 256, 8192, 256, 12000, 512,
					(int) Math.min(Integer.MAX_VALUE, SpellBudgetScaling.scale(DEFAULT_MAX_SPAWN_PER_TICK, multiplier)),
					(int) Math.min(Integer.MAX_VALUE, SpellBudgetScaling.scale(DEFAULT_MAX_PEAK_ALIVE, multiplier)),
					SpellBudgetScaling.scale(DEFAULT_MAX_PROJECTILE_TICKS, multiplier),
					SpellBudgetScaling.scale(DEFAULT_MAX_HOOK_EXECUTIONS, multiplier),
					DEFAULT_MAX_HITS_PER_PROJECTILE,
					DEFAULT_CERTIFICATION_WINDOW_TICKS);
		} catch (Exception e) {
			return certificationDefaults();
		}
	}

	public static SpellAnalysisLimits certificationDefaults() {
		return new SpellAnalysisLimits(64, 4096, 32, 256, 8192, 256, 12000, 512,
				DEFAULT_MAX_SPAWN_PER_TICK,
				DEFAULT_MAX_PEAK_ALIVE,
				DEFAULT_MAX_PROJECTILE_TICKS,
				DEFAULT_MAX_HOOK_EXECUTIONS,
				DEFAULT_MAX_HITS_PER_PROJECTILE,
				DEFAULT_CERTIFICATION_WINDOW_TICKS);
	}
}
