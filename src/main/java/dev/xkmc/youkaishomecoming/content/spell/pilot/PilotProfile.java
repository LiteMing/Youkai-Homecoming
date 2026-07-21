package dev.xkmc.youkaishomecoming.content.spell.pilot;

/**
 * All tunable pilot parameters. Presets map to buff tiers / boss budget.
 * <p>
 * Special J (jitter governance) — retune only, no new structure:
 * <ul>
 *   <li>damping ↑ (0.55/0.5/0.45 → 0.72/0.70/0.68): only lever that tames large force flips</li>
 *   <li>deadzone ↑ slightly: drop far laser sweep micro-forces</li>
 *   <li>search enter/exit band wider: reduce APF↔SEARCH ping-pong</li>
 * </ul>
 * Laser near-field falloff floor is a constant in {@code PotentialFieldSolver} (0.08→0.30).
 */
public record PilotProfile(
		String name,
		double highSpeed,
		double lowSpeed,
		double deadzone,
		double damping,
		double repulseGain,
		double attractGain,
		double maxForce,
		double approachHorizon,
		int searchDepth,
		int nodeBudget,
		int directionRays,
		int threatTopK,
		int predictHorizon,
		float grazeBand,
		double searchEnterClearance,
		double searchExitClearance,
		long timeBudgetNanos
) {

	// deadzone, damping — special J: higher damping + slightly larger deadzone
	// searchEnter/Exit — special J: wider hysteresis band

	public static final PilotProfile NOVICE = new PilotProfile(
			"NOVICE",
			0.25, 0.12,
			0.06, 0.72, // was 0.04, 0.55
			1.8, 0.35, 0.28,
			8.0,
			3, 600, 32,
			48, 12,
			1.5f,
			0.5, 1.4, // was 0.6, 1.2 — wider band
			1_500_000L
	);

	public static final PilotProfile ADEPT = new PilotProfile(
			"ADEPT",
			0.35, 0.16,
			0.05, 0.70, // was 0.03, 0.5
			2.2, 0.4, 0.4,
			10.0,
			5, 1200, 64,
			80, 16,
			1.5f,
			0.55, 1.6, // was 0.7, 1.4
			2_000_000L
	);

	public static final PilotProfile LUNATIC = new PilotProfile(
			"LUNATIC",
			0.45, 0.2,
			0.04, 0.68, // was 0.02, 0.45
			2.8, 0.45, 0.55,
			12.0,
			6, 2000, 128,
			120, 20,
			1.5f,
			0.6, 1.8, // was 0.8, 1.6
			2_000_000L
	);

	/** Server boss budget: shallow search, fewer threats. */
	public static final PilotProfile SERVER_BUDGET = new PilotProfile(
			"SERVER_BUDGET",
			0.3, 0.14,
			0.06, 0.72, // special J aligned
			1.6, 0.3, 0.25,
			8.0,
			2, 400, 14,
			32, 8,
			1.5f,
			0.4, 1.2, // was 0.5, 1.0
			500_000L
	);

	/** Copy with speed / topK / horizon overrides (config-driven player dodge). */
	public PilotProfile withMotion(double high, double low, int topK, int horizon) {
		return new PilotProfile(
				name, high, low, deadzone, damping, repulseGain, attractGain, maxForce,
				approachHorizon, searchDepth, nodeBudget, directionRays,
				topK, horizon, grazeBand, searchEnterClearance, searchExitClearance, timeBudgetNanos
		);
	}
}
