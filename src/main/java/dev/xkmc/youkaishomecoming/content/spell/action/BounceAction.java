package dev.xkmc.youkaishomecoming.content.spell.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.content.spell.definition.DanmakuBounceConfig;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellHitContext;

public record BounceAction(
		int maxBounces,
		double decay,
		boolean retarget
) implements SpellAction {

	public static final Codec<BounceAction> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("max_bounces", 1).forGetter(BounceAction::maxBounces),
			Codec.DOUBLE.optionalFieldOf("decay", 1.0).forGetter(BounceAction::decay),
			Codec.BOOL.optionalFieldOf("retarget", false).forGetter(BounceAction::retarget)
	).apply(i, BounceAction::new));

	public BounceAction() {
		this(1, 1.0, false);
	}

	public BounceAction withMaxBounces(int v) { return new BounceAction(v, decay, retarget); }
	public BounceAction withDecay(double v) { return new BounceAction(maxBounces, v, retarget); }
	public BounceAction withRetarget(boolean v) { return new BounceAction(maxBounces, decay, v); }
	public DanmakuBounceConfig sanitize() {
		return new DanmakuBounceConfig(maxBounces, decay, retarget).sanitize();
	}

	@Override
	public void execute(SpellContext ctx) {
		ctx.hitContext().ifPresent(hit -> {
			if (hit.hitType() == SpellHitContext.HitType.BLOCK) {
				hit.resolveBounce(new DanmakuBounceConfig(maxBounces, decay, retarget));
			}
		});
	}
}
