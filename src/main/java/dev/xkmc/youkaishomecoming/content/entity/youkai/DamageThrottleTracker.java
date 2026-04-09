package dev.xkmc.youkaishomecoming.content.entity.youkai;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;

/**
 * Dynamic damage throttling system for YoukaiEntity.
 * <p>
 * Replaces the old binary invincibility-frame approach with a graduated
 * reduction model:
 * <ul>
 *   <li>Per-second total damage is capped at a configurable fraction of max HP.</li>
 *   <li>Repeated hits from the same damage-source category within a short
 *       window are progressively reduced (diminishing returns).</li>
 * </ul>
 * <p>
 * "Source category" is determined by the causing entity's type ID (e.g. all
 * danmaku from one player is one category, all from another player is another,
 * melee from a specific mob is yet another).
 */
public class DamageThrottleTracker {

	/** Rolling window length in ticks (1 second). */
	private static final int WINDOW_TICKS = 20;

	/**
	 * Maximum fraction of max HP that can be taken as damage per window.
	 * E.g. 0.25 = at most 25 % of max HP per second.
	 */
	private static final float MAX_DAMAGE_FRACTION = 0.25f;

	/**
	 * Per-category: after how much cumulative damage (as fraction of max HP)
	 * the diminishing-returns curve starts applying within the window.
	 * E.g. 0.10 = first 10 % of max HP from this category is unpenalised.
	 */
	private static final float CATEGORY_FREE_FRACTION = 0.10f;

	/**
	 * How quickly repeated same-category damage diminishes.
	 * The multiplier applied to excess damage is {@code 1 / (1 + factor * excess)}.
	 */
	private static final float DIMINISH_FACTOR = 8.0f;

	// ---- runtime state ----

	/** Total damage dealt in the current rolling window. */
	private float windowDamage = 0;
	/** Tick at which the current window started. */
	private int windowStart = -1;

	/** Per-category cumulative damage in the current window. */
	private final Map<String, Float> categoryDamage = new HashMap<>();

	// ---- public API ----

	/**
	 * Compute the effective (reduced) damage that should actually be applied,
	 * given the proposed raw amount and the entity's current max HP.
	 * <p>
	 * Must be called <em>before</em> the damage is applied.  The tracker
	 * records the effective damage internally.
	 *
	 * @param source   the incoming damage source
	 * @param amount   raw damage amount
	 * @param maxHP    the entity's current max health
	 * @param tickCount the entity's current tick counter
	 * @return the damage amount after throttling (may be 0)
	 */
	public float throttle(DamageSource source, float amount, float maxHP, int tickCount) {
		if (maxHP <= 0 || amount <= 0) return 0;

		// Advance / reset the rolling window
		if (windowStart < 0 || tickCount - windowStart >= WINDOW_TICKS) {
			resetWindow(tickCount);
		}

		// ---- global cap ----
		float globalBudget = maxHP * MAX_DAMAGE_FRACTION - windowDamage;
		if (globalBudget <= 0) return 0;

		// ---- per-category diminishing ----
		String category = resolveCategory(source);
		float catAccum = categoryDamage.getOrDefault(category, 0f);
		float freeThreshold = maxHP * CATEGORY_FREE_FRACTION;

		float effective;
		if (catAccum < freeThreshold) {
			// Part of the hit is free, the rest gets diminished
			float freeRoom = freeThreshold - catAccum;
			float freePart = Math.min(amount, freeRoom);
			float excess = amount - freePart;
			float diminished = diminish(excess, catAccum + freePart - freeThreshold, maxHP);
			effective = freePart + diminished;
		} else {
			// Already past free threshold, full diminishing
			effective = diminish(amount, catAccum - freeThreshold, maxHP);
		}

		// Clamp to global budget
		effective = Math.min(effective, globalBudget);

		// Record
		windowDamage += effective;
		categoryDamage.put(category, catAccum + effective);

		return effective;
	}

	/**
	 * Reset the tracker completely (e.g. when the entity leaves combat).
	 */
	public void reset() {
		windowDamage = 0;
		windowStart = -1;
		categoryDamage.clear();
	}

	// ---- internals ----

	private void resetWindow(int tickCount) {
		windowDamage = 0;
		windowStart = tickCount;
		categoryDamage.clear();
	}

	/**
	 * Apply diminishing returns to {@code rawAmount} given that {@code excessSoFar}
	 * points of damage above the free threshold have already been accumulated
	 * from this category in the current window.
	 * <p>
	 * Formula: {@code raw / (1 + factor * (excessSoFar + raw/2) / maxHP)}
	 * <p>
	 * The raw/2 term makes the reduction ramp smoothly within a single large hit.
	 */
	private static float diminish(float rawAmount, float excessSoFar, float maxHP) {
		if (rawAmount <= 0) return 0;
		float normExcess = (excessSoFar + rawAmount * 0.5f) / maxHP;
		float multiplier = 1f / (1f + DIMINISH_FACTOR * normExcess);
		return rawAmount * multiplier;
	}

	/**
	 * Derive a string key that groups damage sources into categories.
	 * <ul>
	 *   <li>If there is a causing entity, use its type + entity ID
	 *       (so player A's danmaku is separate from player B's).</li>
	 *   <li>Otherwise fall back to the damage-type resource key.</li>
	 * </ul>
	 */
	private static String resolveCategory(DamageSource source) {
		Entity cause = source.getEntity();
		if (cause != null) {
			return cause.getType().builtInRegistryHolder().key().location() + "#" + cause.getId();
		}
		var holder = source.typeHolder();
		if (holder.unwrapKey().isPresent()) {
			return holder.unwrapKey().get().location().toString();
		}
		return "unknown";
	}

}
