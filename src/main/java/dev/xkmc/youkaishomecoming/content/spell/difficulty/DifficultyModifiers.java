package dev.xkmc.youkaishomecoming.content.spell.difficulty;

public record DifficultyModifiers(
		float speedMultiplier,
		float frequencyMultiplier,
		float countMultiplier
) {

	public static final DifficultyModifiers DEFAULT = new DifficultyModifiers(1.0f, 1.0f, 1.0f);

	public int adjustCount(int base) {
		return Math.max(1, Math.round(base * countMultiplier));
	}

	public double adjustSpeed(double base) {
		return base * speedMultiplier;
	}

	public int adjustInterval(int base) {
		return Math.max(1, Math.round(base / frequencyMultiplier));
	}
}
