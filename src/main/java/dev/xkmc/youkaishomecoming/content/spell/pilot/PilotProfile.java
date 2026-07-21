package dev.xkmc.youkaishomecoming.content.spell.pilot;

/**
 * All tunable pilot parameters. Presets map to buff tiers / boss budget.
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

	public static final PilotProfile NOVICE = new PilotProfile(
			"NOVICE",
			0.25, 0.12,
			0.04, 0.55,
			1.8, 0.35, 0.28,
			8.0,
			3, 600, 32,
			48, 12,
			1.5f,
			0.6, 1.2,
			1_500_000L
	);

	public static final PilotProfile ADEPT = new PilotProfile(
			"ADEPT",
			0.35, 0.16,
			0.03, 0.5,
			2.2, 0.4, 0.4,
			10.0,
			5, 1200, 64,
			80, 16,
			1.5f,
			0.7, 1.4,
			2_000_000L
	);

	public static final PilotProfile LUNATIC = new PilotProfile(
			"LUNATIC",
			0.45, 0.2,
			0.02, 0.45,
			2.8, 0.45, 0.55,
			12.0,
			6, 2000, 128,
			120, 20,
			1.5f,
			0.8, 1.6,
			2_000_000L
	);

	/** Server boss budget: shallow search, fewer threats. */
	public static final PilotProfile SERVER_BUDGET = new PilotProfile(
			"SERVER_BUDGET",
			0.3, 0.14,
			0.05, 0.6,
			1.6, 0.3, 0.25,
			8.0,
			2, 400, 14,
			32, 8,
			1.5f,
			0.5, 1.0,
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
