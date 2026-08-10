package dev.xkmc.youkaishomecoming.content.spell.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.DanmakuHelper;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberProvider;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellMovementDirective;
import net.minecraft.world.phys.Vec3;

/**
 * Selects caster movement for the current tick.
 * <p>
 * {@code relative} uses local x/y/z (side/normal/forward), while
 * {@code absolute} uses world-axis x/y/z. Coordinates are displacements in
 * blocks per tick, not teleport destinations.
 */
public record CasterMovesAction(
		SpellMovementDirective.Mode mode,
		NumberProvider x,
		NumberProvider y,
		NumberProvider z
) implements SpellAction {

	public static final Codec<CasterMovesAction> CODEC = RecordCodecBuilder.create(i -> i.group(
			SpellMovementDirective.Mode.CODEC.optionalFieldOf("mode", SpellMovementDirective.Mode.RANDOM)
					.forGetter(CasterMovesAction::mode),
			NumberProvider.CODEC.optionalFieldOf("x", NumberProvider.constant(0)).forGetter(CasterMovesAction::x),
			NumberProvider.CODEC.optionalFieldOf("y", NumberProvider.constant(0)).forGetter(CasterMovesAction::y),
			NumberProvider.CODEC.optionalFieldOf("z", NumberProvider.constant(0)).forGetter(CasterMovesAction::z)
	).apply(i, CasterMovesAction::new));

	public CasterMovesAction(SpellMovementDirective.Mode mode) {
		this(mode, NumberProvider.constant(0), NumberProvider.constant(0), NumberProvider.constant(0));
	}

	@Override
	public void execute(SpellContext ctx) {
		Vec3 displacement = switch (mode) {
			case RANDOM, NONE -> Vec3.ZERO;
			case ABSOLUTE -> vector(ctx);
			case RELATIVE -> resolveRelative(ctx, vector(ctx));
		};
		if (!Double.isFinite(displacement.x) || !Double.isFinite(displacement.y)
				|| !Double.isFinite(displacement.z)) {
			displacement = Vec3.ZERO;
		}
		ctx.setMovementDirective(new SpellMovementDirective(mode, displacement));
	}

	private Vec3 vector(SpellContext ctx) {
		return new Vec3(x.get(ctx), y.get(ctx), z.get(ctx));
	}

	private static Vec3 resolveRelative(SpellContext ctx, Vec3 local) {
		Vec3 forward = ctx.holder().forward();
		if (forward.lengthSqr() < 1.0e-8) {
			return local;
		}
		var orientation = DanmakuHelper.getOrientation(forward);
		return orientation.side().scale(local.x)
				.add(orientation.normal().scale(local.y))
				.add(orientation.forward().scale(local.z));
	}
}
