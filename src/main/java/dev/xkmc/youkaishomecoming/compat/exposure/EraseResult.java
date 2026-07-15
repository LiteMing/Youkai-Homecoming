package dev.xkmc.youkaishomecoming.compat.exposure;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.HashMap;
import java.util.Map;

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

	public void record(String typeName, String colorName) {
		totalErased++;
		typeCount.merge(typeName, 1, Integer::sum);
		if (colorName != null && !colorName.isEmpty()) {
			colorCount.merge(colorName, 1, Integer::sum);
		}
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
}
