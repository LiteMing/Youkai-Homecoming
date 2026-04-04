package dev.xkmc.youkaishomecoming.content.spell.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;

import java.util.List;

/**
 * Fires multiple waves of actions with a fixed tick interval between each wave.
 * The first wave executes immediately; subsequent waves are scheduled via DelayAction's mechanism.
 * <p>
 * JSON: {"type": "burst", "waves": 5, "interval": 2, "body": [...]}
 * <p>
 * Equivalent to:
 * <pre>
 *   body (immediate)
 *   delay(interval) { body }
 *   delay(interval*2) { body }
 *   ...
 *   delay(interval*(waves-1)) { body }
 * </pre>
 */
public record BurstAction(int waves, int interval, List<SpellAction> body) implements SpellAction {

	public static final Codec<BurstAction> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("waves").forGetter(BurstAction::waves),
			Codec.INT.optionalFieldOf("interval", 1).forGetter(BurstAction::interval),
			SpellAction.CODEC.listOf().fieldOf("body").forGetter(BurstAction::body)
	).apply(i, BurstAction::new));

	@Override
	public void execute(SpellContext ctx) {
		int actualInterval = Math.max(1, interval);
		for (int w = 0; w < waves; w++) {
			if (w == 0) {
				// First wave: execute immediately
				for (var action : body) {
					action.execute(ctx);
				}
			} else {
				// Subsequent waves: schedule via runtime delayed queue
				ctx.runtime().scheduleDelayed(ctx.totalTick() + actualInterval * w, body);
			}
		}
	}
}
