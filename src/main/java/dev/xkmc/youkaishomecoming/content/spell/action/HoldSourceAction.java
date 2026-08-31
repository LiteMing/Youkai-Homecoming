package dev.xkmc.youkaishomecoming.content.spell.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberProvider;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;

import java.util.List;

/**
 * Freezes/pins the source projectile at the collision contact point for a specified duration,
 * then executes on_release actions with the resumed hit context (e.g. bounce_source, continue_source, discard_source).
 *
 * <p>Only valid inside on_hit_block or on_hit_entity callbacks.
 * <p>JSON: {"type": "hold_source", "duration": 20, "on_release": [...]}
 */
public record HoldSourceAction(NumberProvider duration, List<SpellAction> onRelease) implements SpellAction {

	public HoldSourceAction(int duration, List<SpellAction> onRelease) {
		this(NumberProvider.constant(duration), onRelease);
	}

	public static final Codec<HoldSourceAction> CODEC = RecordCodecBuilder.create(i -> i.group(
			NumberProvider.CODEC.fieldOf("duration").forGetter(HoldSourceAction::duration),
			SpellAction.CODEC.listOf().fieldOf("on_release").forGetter(HoldSourceAction::onRelease)
	).apply(i, HoldSourceAction::new));

	public HoldSourceAction withDuration(NumberProvider v) { return new HoldSourceAction(v, onRelease); }
	public HoldSourceAction withOnRelease(List<SpellAction> v) { return new HoldSourceAction(duration, v); }

	@Override
	public void execute(SpellContext ctx) {
		int ticks = (int) duration.get(ctx);
		if (ticks <= 0) {
			// Zero duration: execute on_release immediately
			ctx.executeList(onRelease);
			return;
		}

		if (ctx.hitContext().isPresent()) {
			ctx.hitContext().get().resolveHold(ticks, onRelease);
		} else {
			// Diagnostic warning when placed outside hit callbacks
			org.slf4j.LoggerFactory.getLogger(HoldSourceAction.class).warn(
					"[Spell] hold_source action executed without hit context (must be inside on_hit_block or on_hit_entity)");
		}
	}
}
