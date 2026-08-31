package dev.xkmc.youkaishomecoming.content.spell.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.content.spell.definition.DanmakuBounceConfig;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellHitContext;

import java.util.Optional;

public record BounceAction(
		int maxBounces,
		double normalFactor,
		double tangentFactor,
		double tangentOffsetX,
		double tangentOffsetY,
		double tangentOffsetZ,
		Optional<Double> outputSpeed,
		boolean retarget
) implements SpellAction {

	public static final Codec<BounceAction> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("max_bounces", 1).forGetter(BounceAction::maxBounces),
			Codec.DOUBLE.optionalFieldOf("normal_factor", -1.0).forGetter(BounceAction::normalFactor),
			Codec.DOUBLE.optionalFieldOf("tangent_factor", 1.0).forGetter(BounceAction::tangentFactor),
			Codec.DOUBLE.optionalFieldOf("tangent_offset_x", 0.0).forGetter(BounceAction::tangentOffsetX),
			Codec.DOUBLE.optionalFieldOf("tangent_offset_y", 0.0).forGetter(BounceAction::tangentOffsetY),
			Codec.DOUBLE.optionalFieldOf("tangent_offset_z", 0.0).forGetter(BounceAction::tangentOffsetZ),
			Codec.DOUBLE.optionalFieldOf("output_speed").forGetter(BounceAction::outputSpeed),
			Codec.BOOL.optionalFieldOf("retarget", false).forGetter(BounceAction::retarget)
	).apply(i, BounceAction::new));

	public BounceAction() {
		this(1, -1.0, 1.0, 0.0, 0.0, 0.0, Optional.empty(), false);
	}

	public BounceAction withMaxBounces(int v) { return new BounceAction(v, normalFactor, tangentFactor, tangentOffsetX, tangentOffsetY, tangentOffsetZ, outputSpeed, retarget); }
	public BounceAction withNormalFactor(double v) { return new BounceAction(maxBounces, v, tangentFactor, tangentOffsetX, tangentOffsetY, tangentOffsetZ, outputSpeed, retarget); }
	public BounceAction withTangentFactor(double v) { return new BounceAction(maxBounces, normalFactor, v, tangentOffsetX, tangentOffsetY, tangentOffsetZ, outputSpeed, retarget); }
	public BounceAction withTangentOffsetX(double v) { return new BounceAction(maxBounces, normalFactor, tangentFactor, v, tangentOffsetY, tangentOffsetZ, outputSpeed, retarget); }
	public BounceAction withTangentOffsetY(double v) { return new BounceAction(maxBounces, normalFactor, tangentFactor, tangentOffsetX, v, tangentOffsetZ, outputSpeed, retarget); }
	public BounceAction withTangentOffsetZ(double v) { return new BounceAction(maxBounces, normalFactor, tangentFactor, tangentOffsetX, tangentOffsetY, v, outputSpeed, retarget); }
	public BounceAction withOutputSpeed(Optional<Double> v) { return new BounceAction(maxBounces, normalFactor, tangentFactor, tangentOffsetX, tangentOffsetY, tangentOffsetZ, v, retarget); }
	public BounceAction withRetarget(boolean v) { return new BounceAction(maxBounces, normalFactor, tangentFactor, tangentOffsetX, tangentOffsetY, tangentOffsetZ, outputSpeed, v); }

	public DanmakuBounceConfig sanitize() {
		return new DanmakuBounceConfig(maxBounces, normalFactor, tangentFactor, tangentOffsetX, tangentOffsetY, tangentOffsetZ, outputSpeed, retarget).sanitize();
	}

	@Override
	public void execute(SpellContext ctx) {
		ctx.hitContext().ifPresent(hit -> {
			if (hit.hitType() == SpellHitContext.HitType.BLOCK) {
				hit.resolveBounce(new DanmakuBounceConfig(maxBounces, normalFactor, tangentFactor, tangentOffsetX, tangentOffsetY, tangentOffsetZ, outputSpeed, retarget));
			}
		});
	}
}
