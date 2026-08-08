package dev.xkmc.youkaishomecoming.content.spell.analysis;

import java.util.List;
import java.util.Set;

/**
 * Static analysis result of a single SpellDefinition (design doc §10).
 * <p>
 * Metric semantics differ by profile:
 * <ul>
 *   <li>MARKET: legacy budget numbers (historical SpellMarketValidator behavior):
 *   {@code totalSpawnUpperBound} is the un-amplified-by-duration projection sum
 *   (repeat/burst/shooter counts only, no certification window, no hook fanout).</li>
 *   <li>CERTIFICATION: conservative bounds projected over
 *   {@link SpellAnalysisLimits#certificationWindowTicks()}:
 *   per-tick spawns are multiplied by the window, hook executions are counted
 *   conservatively (every eligible projectile triggers every hook).
 *   {@code peakAliveUpperBound} is the conservative "all spawned projectiles alive"
 *   bound; {@code maxSpawnPerTick} is the largest single-tick spawn count.</li>
 * </ul>
 * Hard-limit violations and policy rejections are reported by throwing
 * {@link SpellAnalysisException}; this record is returned only for accepted
 * (or analyzable-with-warnings) definitions.
 *
 * @param totalSpawnUpperBound conservative total projectile spawn upper bound
 * @param projectileTicks      conservative total projectile-tick upper bound
 * @param peakAliveUpperBound  conservative concurrent-alive upper bound
 * @param maxSpawnPerTick      largest single-tick spawn count
 * @param hookExecutionUpperBound conservative total hook execution upper bound
 * @param expressionOps        rough count of evaluated number/expression nodes
 * @param serverWork           rough server work metric (≈ projectileTicks)
 * @param clientRenderWork     rough client render metric (≈ peakAlive)
 * @param gameplayPower        rough gameplay power metric (spawns + hooks)
 * @param requiredCapabilities capabilities the definition requires
 * @param diagnostics          non-fatal findings (warnings/info)
 */
public record SpellAnalysis(
		long totalSpawnUpperBound,
		long projectileTicks,
		int peakAliveUpperBound,
		int maxSpawnPerTick,
		long hookExecutionUpperBound,
		long expressionOps,
		double serverWork,
		double clientRenderWork,
		double gameplayPower,
		Set<SpellCapability> requiredCapabilities,
		List<SpellDiagnostic> diagnostics
) {

	public boolean hasErrors() {
		for (SpellDiagnostic diag : diagnostics) {
			if (diag.isError()) return true;
		}
		return false;
	}
}
