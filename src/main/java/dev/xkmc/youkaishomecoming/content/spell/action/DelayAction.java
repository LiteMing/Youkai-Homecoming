package dev.xkmc.youkaishomecoming.content.spell.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;

import java.util.List;

/**
 * Schedules a list of actions to execute after a specified delay (in ticks).
 * The actions are queued into SpellRuntime's delayed action queue and executed
 * when the delay expires, regardless of phase changes.
 * <p>
 * JSON: {"type": "delay", "delay_ticks": 20, "body": [...]}
 */
public record DelayAction(int delayTicks, List<SpellAction> body) implements SpellAction {

	public static final Codec<DelayAction> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("delay_ticks").forGetter(DelayAction::delayTicks),
			SpellAction.CODEC.listOf().fieldOf("body").forGetter(DelayAction::body)
	).apply(i, DelayAction::new));

	@Override
	public void execute(SpellContext ctx) {
		if (delayTicks <= 0) {
			// Immediate execution if delay is zero or negative
			for (var action : body) {
				action.execute(ctx);
			}
		} else {
			// Schedule for future execution
			ctx.runtime().scheduleDelayed(ctx.totalTick() + delayTicks, body);
		}
	}
}
