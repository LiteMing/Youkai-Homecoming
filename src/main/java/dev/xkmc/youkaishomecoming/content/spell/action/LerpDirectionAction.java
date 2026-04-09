package dev.xkmc.youkaishomecoming.content.spell.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.DanmakuHelper;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import net.minecraft.world.phys.Vec3;

/**
 * Lerps three direction variables (x, y, z) toward the current caster→target direction
 * by at most {@code maxMove} per tick (in unit-vector distance space).
 * <p>
 * Replicates the legacy MasterSpark's slow aim-tracking logic:
 * <pre>
 * double dist = current.distanceTo(desired);
 * double perc = dist < maxMove ? 1 : maxMove / dist;
 * current = current.lerp(desired, perc);
 * </pre>
 * JSON: {"type": "lerp_direction", "x": "ms_dx", "y": "ms_dy", "z": "ms_dz", "max_move": 0.02}
 */
public record LerpDirectionAction(String xKey, String yKey, String zKey, double maxMove) implements SpellAction {

	public static final Codec<LerpDirectionAction> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("x").forGetter(LerpDirectionAction::xKey),
			Codec.STRING.fieldOf("y").forGetter(LerpDirectionAction::yKey),
			Codec.STRING.fieldOf("z").forGetter(LerpDirectionAction::zKey),
			Codec.DOUBLE.optionalFieldOf("max_move", 0.02).forGetter(LerpDirectionAction::maxMove)
	).apply(i, LerpDirectionAction::new));

	@Override
	public void execute(SpellContext ctx) {
		Vec3 target = ctx.holder().target();
		if (target == null) return;

		Vec3 cen = ctx.holder().center();
		Vec3 desiredDelta = target.subtract(cen);
		if (!DanmakuHelper.isFinite(desiredDelta) || desiredDelta.lengthSqr() < 1e-8) return;
		Vec3 desired = DanmakuHelper.safeDirection(desiredDelta, ctx.holder().forward());

		double cx = ctx.getVariable(xKey);
		double cy = ctx.getVariable(yKey);
		double cz = ctx.getVariable(zKey);
		Vec3 current = new Vec3(cx, cy, cz);
		if (!DanmakuHelper.isFinite(current) || current.lengthSqr() < 1e-8) {
			// Not initialized yet, snap to desired
			ctx.setVariable(xKey, desired.x);
			ctx.setVariable(yKey, desired.y);
			ctx.setVariable(zKey, desired.z);
			return;
		}
		current = DanmakuHelper.safeDirection(current, desired);

		double dist = current.distanceTo(desired);
		if (!Double.isFinite(dist) || dist < 1e-8) return; // already aligned

		double perc = dist < maxMove ? 1.0 : maxMove / dist;
		Vec3 lerped = current.lerp(desired, perc);
		lerped = DanmakuHelper.safeDirection(lerped, desired);

		ctx.setVariable(xKey, lerped.x);
		ctx.setVariable(yKey, lerped.y);
		ctx.setVariable(zKey, lerped.z);
	}
}
