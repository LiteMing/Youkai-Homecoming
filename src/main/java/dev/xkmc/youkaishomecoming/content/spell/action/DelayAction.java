package dev.xkmc.youkaishomecoming.content.spell.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberProvider;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;

import java.util.List;

/**
 * Schedules a list of actions to execute after a specified delay (in ticks).
 * The actions are queued into SpellRuntime's delayed action queue and executed
 * when the delay expires, regardless of phase changes.
 * <p>
 * The delay can be a NumberProvider expression (e.g. {@code $pair * 10}).
 * <p>
 * JSON: {"type": "delay", "delay_ticks": 20, "body": [...]}
 */
public record DelayAction(NumberProvider delayTicks, List<SpellAction> body) implements SpellAction {

	/** Convenience constructor for literal int delays. */
	public DelayAction(int delayTicks, List<SpellAction> body) {
		this(NumberProvider.constant(delayTicks), body);
	}

	public static final Codec<DelayAction> CODEC = RecordCodecBuilder.create(i -> i.group(
			NumberProvider.CODEC.fieldOf("delay_ticks").forGetter(DelayAction::delayTicks),
			SpellAction.CODEC.listOf().fieldOf("body").forGetter(DelayAction::body)
	).apply(i, DelayAction::new));

	@Override
	public void execute(SpellContext ctx) {
		int delay = (int) delayTicks.get(ctx);
		if (delay <= 0) {
			// Immediate execution if delay is zero or negative with terminal propagation
			ctx.executeList(body);
		} else if (ctx.hitContext().isPresent()) {
			// Inside hit callback: freeze projectile at hit contact position and defer body execution
			ctx.hitContext().get().resolveHold(delay, body);
		} else {
			// Schedule for future execution
			ctx.runtime().scheduleDelayed(ctx.totalTick() + delay, body);
		}
	}
}
