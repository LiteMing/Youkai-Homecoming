package dev.xkmc.youkaishomecoming.content.spell.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;

import java.util.List;

/**
 * Fires multiple waves of actions with a fixed tick interval between each wave.
 * The first wave executes immediately; subsequent waves are scheduled via DelayAction's mechanism.
 * <p>
 * If {@code waveVariable} is set, each wave sets a runtime variable (default "wave") to the
 * current wave index (0-based) before executing the body, so child actions can reference it
 * via NumberProvider variable or $wave expression.
 * <p>
 * JSON: {"type": "burst", "waves": 5, "interval": 2, "wave_variable": "wave", "body": [...]}
 * <p>
 * Equivalent to:
 * <pre>
 *   set $wave = 0; body (immediate)
 *   delay(interval) { set $wave = 1; body }
 *   delay(interval*2) { set $wave = 2; body }
 *   ...
 *   delay(interval*(waves-1)) { set $wave = N-1; body }
 * </pre>
 */
public record BurstAction(int waves, int interval, String waveVariable, List<SpellAction> body) implements SpellAction {

	public static final Codec<BurstAction> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.intRange(1, Integer.MAX_VALUE).fieldOf("waves").forGetter(BurstAction::waves),
			Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("interval", 1).forGetter(BurstAction::interval),
			Codec.STRING.optionalFieldOf("wave_variable", "").forGetter(BurstAction::waveVariable),
			SpellAction.CODEC.listOf().fieldOf("body").forGetter(BurstAction::body)
	).apply(i, BurstAction::new));

	/** Backwards-compatible constructor without waveVariable. */
	public BurstAction(int waves, int interval, List<SpellAction> body) {
		this(waves, interval, "", body);
	}

	@Override
	public void execute(SpellContext ctx) {
		boolean hasVar = waveVariable != null && !waveVariable.isEmpty();
		if (interval == 0) {
			for (int w = 0; w < waves; w++) {
				if (ctx.shouldAbortActionList()) break;
				if (hasVar) ctx.setVariable(waveVariable, w);
				ctx.executeList(body);
			}
			return;
		}
		for (int w = 0; w < waves; w++) {
			if (ctx.shouldAbortActionList()) break;
			if (w == 0) {
				// First wave: execute immediately
				if (hasVar) ctx.setVariable(waveVariable, w);
				ctx.executeList(body);
			} else {
				// Subsequent waves: wrap body with variable-set prefix
				final int waveIdx = w;
				List<SpellAction> scheduled;
				if (hasVar) {
					var prefix = new SpellActions.SetVariable(waveVariable, waveIdx);
					scheduled = new java.util.ArrayList<>();
					scheduled.add(prefix);
					scheduled.addAll(body);
				} else {
					scheduled = body;
				}
				ctx.runtime().scheduleDelayed(ctx.totalTick() + interval * waveIdx, scheduled,
						ctx.holder(), ctx.callbackContext().orElse(null));
			}
		}
	}
}
