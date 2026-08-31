package dev.xkmc.youkaishomecoming.compat.exposure;

import dev.xkmc.fastprojectileapi.entity.SimplifiedProjectile;
import dev.xkmc.fastprojectileapi.render.virtual.DanmakuManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Collects statistics about danmaku erased during a photo capture.
 * Tracks total count, type breakdown, and color breakdown.
 */
public class EraseResult {

	private int totalErased = 0;
	/** Map of bullet type name → count erased. e.g. "CIRCLE" → 5, "BALL" → 3 */
	private final Map<String, Integer> typeCount = new HashMap<>();
	/** Map of color name → count erased. e.g. "red" → 10, "blue" → 5 */
	private final Map<String, Integer> colorCount = new HashMap<>();
	private final Map<ResourceLocation, Integer> sourceCount = new HashMap<>();
	private final Map<ResourceLocation, Integer> committedSourceCount = new HashMap<>();
	private final List<Candidate> candidates = new ArrayList<>();
	private final IdentityHashMap<SimplifiedProjectile, Boolean> seen = new IdentityHashMap<>();
	private boolean committed;

	public void record(String typeName, String colorName) {
		record(typeName, colorName, null, null, null);
	}

	public void record(String typeName, String colorName, @Nullable ResourceLocation source,
			@Nullable LivingEntity trackingHost, @Nullable SimplifiedProjectile projectile) {
		if (projectile != null && seen.put(projectile, Boolean.TRUE) != null) return;
		totalErased++;
		typeCount.merge(typeName, 1, Integer::sum);
		if (colorName != null && !colorName.isEmpty()) {
			colorCount.merge(colorName, 1, Integer::sum);
		}
		if (source != null) sourceCount.merge(source, 1, Integer::sum);
		if (projectile != null) candidates.add(new Candidate(projectile, trackingHost, source));
	}

	public int getTotal() {
		return totalErased;
	}

	public boolean isEmpty() {
		return totalErased == 0;
	}

	public int remaining(int limit) {
		return limit - totalErased;
	}

	/** Select an already-bound source, or the unique most common source for a blank film. */
	@Nullable
	public ResourceLocation selectReplicaSource(@Nullable ResourceLocation boundSource) {
		return selectReplicaSource(boundSource, ignored -> true);
	}

	@Nullable
	public ResourceLocation selectReplicaSource(@Nullable ResourceLocation boundSource,
			Predicate<ResourceLocation> eligible) {
		Map<ResourceLocation, Integer> counts = committed ? committedSourceCount : sourceCount;
		if (boundSource != null) return counts.containsKey(boundSource) && eligible.test(boundSource)
				? boundSource : null;
		ResourceLocation best = null;
		int bestCount = 0;
		boolean tied = false;
		for (var entry : counts.entrySet()) {
			if (!eligible.test(entry.getKey())) continue;
			if (entry.getValue() > bestCount) {
				best = entry.getKey();
				bestCount = entry.getValue();
				tied = false;
			} else if (entry.getValue() == bestCount) {
				tied = true;
			}
		}
		return tied ? null : best;
	}

	public boolean hasLiveCandidates() {
		return candidates.stream().anyMatch(candidate -> !candidate.projectile().isRemoved());
	}

	public int countForSource(ResourceLocation source) {
		return (committed ? committedSourceCount : sourceCount).getOrDefault(source, 0);
	}

	/** Erase exactly the projectiles counted before Exposure serialized the frame. */
	public int eraseCandidates(Player player) {
		int erased = 0;
		committedSourceCount.clear();
		try {
			for (Candidate candidate : candidates) {
				SimplifiedProjectile projectile = candidate.projectile();
				if (projectile.isRemoved()) continue;
				DanmakuManager.setTrackingOverride(candidate.trackingHost());
				projectile.erase(player);
				erased++;
				if (candidate.source() != null) {
					committedSourceCount.merge(candidate.source(), 1, Integer::sum);
				}
			}
			DanmakuManager.flushErases();
		} finally {
			committed = true;
			DanmakuManager.setTrackingOverride(null);
		}
		return erased;
	}

	/**
	 * Calculate a score inspired by 東方文花帖 (Shoot the Bullet) scoring:
	 * - Base points per danmaku erased
	 * - Bonus for variety of types (more types = higher multiplier)
	 * - Bonus for variety of colors
	 * - Bonus for large quantities
	 */
	public int calculateScore() {
		if (totalErased == 0) return 0;

		int basePoints = totalErased * 100;

		// Type variety bonus: 1.0x for 1 type, +0.25x per additional type
		double typeMultiplier = 1.0 + (typeCount.size() - 1) * 0.25;

		// Color variety bonus: 1.0x for 1 color, +0.2x per additional color
		double colorMultiplier = 1.0 + Math.max(0, colorCount.size() - 1) * 0.2;

		// Quantity bonus: extra multiplier for large captures
		double quantityMultiplier = 1.0;
		if (totalErased >= 50) quantityMultiplier = 2.0;
		else if (totalErased >= 20) quantityMultiplier = 1.5;
		else if (totalErased >= 10) quantityMultiplier = 1.2;

		return (int) (basePoints * typeMultiplier * colorMultiplier * quantityMultiplier);
	}

	/**
	 * Write erase statistics into the frame's CompoundTag.
	 * Always writes the tag (even with total=0) so we can verify the event fired.
	 * Format:
	 * <pre>
	 * "youkaishomecoming:danmaku_erased": {
	 *     "total": 42,
	 *     "score": 12600,
	 *     "types": [
	 *         { "type": "CIRCLE", "count": 20 },
	 *         { "type": "BALL", "count": 15 }
	 *     ],
	 *     "colors": [
	 *         { "color": "red", "count": 18 },
	 *         { "color": "blue", "count": 24 }
	 *     ]
	 * }
	 * </pre>
	 */
	public void writeToFrame(CompoundTag frame) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("total", totalErased);
		tag.putInt("score", calculateScore());

		if (!typeCount.isEmpty()) {
			ListTag types = new ListTag();
			for (var entry : typeCount.entrySet()) {
				CompoundTag typeTag = new CompoundTag();
				typeTag.putString("type", entry.getKey());
				typeTag.putInt("count", entry.getValue());
				types.add(typeTag);
			}
			tag.put("types", types);
		}

		if (!colorCount.isEmpty()) {
			ListTag colors = new ListTag();
			for (var entry : colorCount.entrySet()) {
				CompoundTag colorTag = new CompoundTag();
				colorTag.putString("color", entry.getKey());
				colorTag.putInt("count", entry.getValue());
				colors.add(colorTag);
			}
			tag.put("colors", colors);
		}

		frame.put("youkaishomecoming:danmaku_erased", tag);
	}

	/** Get type count map for packet serialization. */
	public Map<String, Integer> getTypeCount() {
		return typeCount;
	}

	/** Get color count map for packet serialization. */
	public Map<String, Integer> getColorCount() {
		return colorCount;
	}

	private record Candidate(SimplifiedProjectile projectile, @Nullable LivingEntity trackingHost,
			@Nullable ResourceLocation source) {
	}
}
