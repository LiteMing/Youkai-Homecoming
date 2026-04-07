package dev.xkmc.youkaishomecoming.content.spell.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.DanmakuHelper;
import dev.xkmc.youkaishomecoming.content.spell.definition.AimMode;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberProvider;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import net.minecraft.world.phys.Vec3;

/**
 * Sets the caster's velocity based on an aim direction and speed.
 * Useful for dash-style boss movement in data-driven spells.
 */
public record SetVelocityAction(
		NumberProvider speed,
		NumberProvider angleOffset,
		NumberProvider elevation,
		AimMode aimMode,
		boolean additive
) implements SpellAction {

	public static final Codec<SetVelocityAction> CODEC = RecordCodecBuilder.create(i -> i.group(
			NumberProvider.CODEC.fieldOf("speed").forGetter(SetVelocityAction::speed),
			NumberProvider.CODEC.optionalFieldOf("angle_offset", NumberProvider.constant(0)).forGetter(SetVelocityAction::angleOffset),
			NumberProvider.CODEC.optionalFieldOf("elevation", NumberProvider.constant(0)).forGetter(SetVelocityAction::elevation),
			AimMode.CODEC.optionalFieldOf("aim_mode", new AimMode.AimModes.Target()).forGetter(SetVelocityAction::aimMode),
			Codec.BOOL.optionalFieldOf("additive", false).forGetter(SetVelocityAction::additive)
	).apply(i, SetVelocityAction::new));

	public SetVelocityAction(NumberProvider speed, AimMode aimMode) {
		this(speed, NumberProvider.constant(0), NumberProvider.constant(0), aimMode, false);
	}

	@Override
	public void execute(SpellContext ctx) {
		Vec3 baseDir = aimMode.getBaseDirection(ctx, ctx.holder().center());
		if (baseDir.lengthSqr() < 1e-8) {
			baseDir = new Vec3(0, 0, 1);
		}
		double angle = angleOffset.get(ctx);
		double elev = elevation.get(ctx);
		Vec3 dir = (angle != 0 || elev != 0)
				? DanmakuHelper.getOrientation(baseDir.normalize()).rotateDegrees(angle, elev)
				: baseDir.normalize();
		Vec3 velocity = dir.scale(speed.get(ctx));
		if (additive) {
			velocity = ctx.self().getDeltaMovement().add(velocity);
		}
		ctx.self().setDeltaMovement(velocity);
		ctx.self().hasImpulse = true;
	}
}
