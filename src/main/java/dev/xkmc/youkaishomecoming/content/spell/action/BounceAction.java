package dev.xkmc.youkaishomecoming.content.spell.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.content.spell.definition.DanmakuBounceConfig;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;

public record BounceAction(
		int maxBounces,
		double decay,
		boolean retarget,
		int delay
) implements SpellAction {

	public static final Codec<BounceAction> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("max_bounces", 1).forGetter(BounceAction::maxBounces),
			Codec.DOUBLE.optionalFieldOf("decay", 1.0).forGetter(BounceAction::decay),
			Codec.BOOL.optionalFieldOf("retarget", false).forGetter(BounceAction::retarget),
			Codec.INT.optionalFieldOf("delay", 0).forGetter(BounceAction::delay)
	).apply(i, BounceAction::new));

	public BounceAction() {
		this(1, 1.0, false, 0);
	}

	public BounceAction withMaxBounces(int v) { return new BounceAction(v, decay, retarget, delay); }
	public BounceAction withDecay(double v) { return new BounceAction(maxBounces, v, retarget, delay); }
	public BounceAction withRetarget(boolean v) { return new BounceAction(maxBounces, decay, v, delay); }
	public BounceAction withDelay(int v) { return new BounceAction(maxBounces, decay, retarget, v); }

	@Override
	public void execute(SpellContext ctx) {
		ctx.hitContext().ifPresent(hit -> {
			hit.resolveBounce(new DanmakuBounceConfig(maxBounces, decay, retarget, delay));
		});
	}
}
