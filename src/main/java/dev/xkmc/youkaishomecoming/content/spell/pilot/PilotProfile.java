package dev.xkmc.youkaishomecoming.content.spell.pilot;

/** All algorithm parameters for one pilot capability profile. */
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

	public static final double DEFAULT_PLAYER_BASE_SPEED = 0.30;
	public static final double DEFAULT_PLAYER_SPEED_STEP = 0.10;

	public static final PilotProfile BASIC = playerTier(0,
			DEFAULT_PLAYER_BASE_SPEED, DEFAULT_PLAYER_SPEED_STEP);
	public static final PilotProfile ENHANCED = playerTier(1,
			DEFAULT_PLAYER_BASE_SPEED, DEFAULT_PLAYER_SPEED_STEP);
	public static final PilotProfile ADVANCED = playerTier(2,
			DEFAULT_PLAYER_BASE_SPEED, DEFAULT_PLAYER_SPEED_STEP);

	/** Server boss budget: shallow search and a smaller threat set. */
	public static final PilotProfile SERVER_BUDGET = new PilotProfile(
			"SERVER_BUDGET",
			0.3, 0.14,
			0.05, 0.70,
			2.4, 0.4, 0.45,
			8.0,
			3, 500, 24,
			32, 8,
			1.5f,
			0.55, 1.6,
			500_000L
	);

	/**
	 * All player levels run the same controller. Only capability increases with
	 * the level; no algorithm layer is removed from lower levels.
	 */
	public static PilotProfile playerTier(int amplifier, double baseSpeed, double speedStep) {
		int tier = Math.max(0, Math.min(2, amplifier));
		double high = baseSpeed + speedStep * tier;
		return new PilotProfile(
				switch (tier) {
					case 0 -> "BASIC";
					case 1 -> "ENHANCED";
					default -> "ADVANCED";
				},
				high, high * 0.46,
				0.04, 0.68,
				2.8, 0.42, 0.55,
				8.0 + tier * 2.0,
				4 + tier, 800 + tier * 600, 32 + tier * 16,
				56 + tier * 32, 12 + tier * 4,
				1.5f,
				0.6, 1.8,
				1_500_000L + tier * 250_000L
		);
	}

	/** Dense rays are only used after every base direction is predicted blocked. */
	public int emergencyRefinementRays() {
		return Math.max(12, directionRays / 2);
	}

	/** Higher levels refresh broad open-space guidance more often. */
	public int gapRefreshTicks() {
		return directionRays >= 64 ? 2 : directionRays >= 48 ? 3 : 4;
	}

	public int planCommitTicks() {
		return directionRays >= 64 ? 6 : directionRays >= 48 ? 5 : 4;
	}

	public double wallClearanceRadius() {
		return 1.5;
	}

	public double wallClearanceGain() {
		return 0.75;
	}

	public double wallClearanceDangerDist() {
		return 0.85;
	}

	public double wallClearanceSafeDist() {
		return 2.5;
	}

	/** Copy with speed / top-K / horizon overrides for the server-side consumer. */
	public PilotProfile withMotion(double high, double low, int topK, int horizon) {
		return new PilotProfile(
				name, high, low, deadzone, damping, repulseGain, attractGain, maxForce,
				approachHorizon, searchDepth, nodeBudget, directionRays,
				topK, horizon, grazeBand, searchEnterClearance, searchExitClearance, timeBudgetNanos
		);
	}
}
