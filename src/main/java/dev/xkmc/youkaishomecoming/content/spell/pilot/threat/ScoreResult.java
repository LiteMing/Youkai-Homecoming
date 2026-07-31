package dev.xkmc.youkaishomecoming.content.spell.pilot.threat;

/**
 * Node / path safety evaluation for one self pose against a threat snapshot slice.
 *
 * @param score          higher is safer; {@link #DEAD} for hard hit
 * @param minClearance   min signed clearance over evaluated threats (negative = penetrate)
 * @param grazeCount     threats inside graze band but not hard-hit
 * @param hardHit        true if any hard collision on this node
 * @param nearestThreatId entity id of closest threat, or -1
 */
public record ScoreResult(double score, double minClearance, int grazeCount, boolean hardHit, int nearestThreatId) {

	public static final double DEAD = Double.NEGATIVE_INFINITY;

	public static ScoreResult dead(int threatId) {
		return new ScoreResult(DEAD, -1, 0, true, threatId);
	}

	public static ScoreResult safe(double score, double minClearance, int graze, int nearestId) {
		return new ScoreResult(score, minClearance, graze, false, nearestId);
	}

	public boolean isAlive() {
		return !hardHit && score > DEAD;
	}
}
