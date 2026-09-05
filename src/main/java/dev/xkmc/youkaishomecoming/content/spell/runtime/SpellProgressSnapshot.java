package dev.xkmc.youkaishomecoming.content.spell.runtime;

import java.util.Arrays;

/** Immutable server projection shared by spell-health bars and world-space rings. */
public record SpellProgressSnapshot(int health, int segmentMaxHealth,
		int elapsedTicks, int durationTicks, int completedHealth, int[] healthSegments) {

	public static final SpellProgressSnapshot NONE = new SpellProgressSnapshot(0, 0, 0, 0, 0, new int[0]);

	public SpellProgressSnapshot {
		health = Math.max(0, health);
		segmentMaxHealth = Math.max(0, segmentMaxHealth);
		elapsedTicks = Math.max(0, elapsedTicks);
		durationTicks = Math.max(0, durationTicks);
		completedHealth = Math.max(0, completedHealth);
		healthSegments = healthSegments == null ? new int[0] : Arrays.stream(healthSegments)
				.filter(value -> value > 0).toArray();
	}

	@Override
	public int[] healthSegments() {
		return healthSegments.clone();
	}

	public int totalHealth() {
		long total = 0;
		for (int segment : healthSegments) total += segment;
		return (int) Math.min(Integer.MAX_VALUE, total);
	}

	public boolean active() {
		return totalHealth() > 0 || durationTicks > 0;
	}

	public int totalRemainingHealth() {
		int total = totalHealth();
		if (total <= 0) return Math.min(health, segmentMaxHealth);
		long remaining = (long) total - completedHealth - segmentMaxHealth + Math.min(health, segmentMaxHealth);
		return (int) Math.max(0, Math.min(total, remaining));
	}

	public static SpellProgressSnapshot fromRuntime(SpellRuntime runtime, int currentHealth) {
		if (runtime == null || runtime.getSpellHealthTotal() <= 0) return NONE;
		return new SpellProgressSnapshot(currentHealth, runtime.getSpellMaxHealth(),
				runtime.getSpellElapsedTicks(), runtime.getSpellDurationTicks(),
				runtime.getSpellHealthCompleted(), runtime.getSpellHealthSegments());
	}

	/** Projects a total shield-style HP value onto the ordered health segments. */
	public static SpellProgressSnapshot fromTotalRemaining(SpellRuntime runtime, int totalRemaining) {
		if (runtime == null || runtime.getSpellHealthTotal() <= 0) return NONE;
		int[] segments = runtime.getSpellHealthSegments();
		int total = runtime.getSpellHealthTotal();
		int damage = total - Math.max(0, Math.min(total, totalRemaining));
		int completed = 0;
		int currentMax = 0;
		int current = 0;
		for (int segment : segments) {
			if (damage >= segment) {
				damage -= segment;
				completed += segment;
				continue;
			}
			currentMax = segment;
			current = segment - damage;
			break;
		}
		return new SpellProgressSnapshot(current, currentMax,
				runtime.getSpellElapsedTicks(), runtime.getSpellDurationTicks(), completed, segments);
	}
}
