package dev.xkmc.youkaishomecoming.content.spell.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.DanmakuHelper;
import dev.xkmc.youkaishomecoming.content.spell.definition.*;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.CardHolder;
import dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * Data-driven action to fire danmaku projectiles.
 * Supports ring, line, random, and aimed patterns with configurable parameters.
 */
public record FireDanmakuAction(
		YHDanmaku.Bullet bulletType,
		DyeColor color,
		NumberProvider count,
		NumberProvider speed,
		NumberProvider lifetime,
		NumberProvider angleOffset,
		NumberProvider spread,
		PatternType pattern,
		OriginConfig origin,
		AimMode aimMode,
		Optional<MoverConfig> mover,
		Optional<List<SpellAction>> onExpiry
) implements SpellAction {

	public static final Codec<FireDanmakuAction> CODEC = RecordCodecBuilder.create(i -> i.group(
			SpellCodecs.BULLET_CODEC.fieldOf("bullet").forGetter(FireDanmakuAction::bulletType),
			SpellCodecs.DYE_COLOR_CODEC.fieldOf("color").forGetter(FireDanmakuAction::color),
			NumberProvider.CODEC.fieldOf("count").forGetter(FireDanmakuAction::count),
			NumberProvider.CODEC.fieldOf("speed").forGetter(FireDanmakuAction::speed),
			NumberProvider.CODEC.fieldOf("lifetime").forGetter(FireDanmakuAction::lifetime),
			NumberProvider.CODEC.optionalFieldOf("angle_offset", NumberProvider.constant(0)).forGetter(FireDanmakuAction::angleOffset),
			NumberProvider.CODEC.optionalFieldOf("spread", NumberProvider.constant(360)).forGetter(FireDanmakuAction::spread),
			PatternType.CODEC.optionalFieldOf("pattern", PatternType.RING).forGetter(FireDanmakuAction::pattern),
			OriginConfig.CODEC.optionalFieldOf("origin", OriginConfig.caster()).forGetter(FireDanmakuAction::origin),
			AimMode.CODEC.optionalFieldOf("aim_mode", new AimMode.AimModes.Target()).forGetter(FireDanmakuAction::aimMode),
			MoverConfig.CODEC.optionalFieldOf("mover").forGetter(FireDanmakuAction::mover),
			SpellAction.CODEC.listOf().optionalFieldOf("on_expiry").forGetter(FireDanmakuAction::onExpiry)
	).apply(i, FireDanmakuAction::new));

	// withXxx helper methods for editor use
	public FireDanmakuAction withBulletType(YHDanmaku.Bullet v) { return new FireDanmakuAction(v, color, count, speed, lifetime, angleOffset, spread, pattern, origin, aimMode, mover, onExpiry); }
	public FireDanmakuAction withColor(DyeColor v) { return new FireDanmakuAction(bulletType, v, count, speed, lifetime, angleOffset, spread, pattern, origin, aimMode, mover, onExpiry); }
	public FireDanmakuAction withCount(NumberProvider v) { return new FireDanmakuAction(bulletType, color, v, speed, lifetime, angleOffset, spread, pattern, origin, aimMode, mover, onExpiry); }
	public FireDanmakuAction withSpeed(NumberProvider v) { return new FireDanmakuAction(bulletType, color, count, v, lifetime, angleOffset, spread, pattern, origin, aimMode, mover, onExpiry); }
	public FireDanmakuAction withLifetime(NumberProvider v) { return new FireDanmakuAction(bulletType, color, count, speed, v, angleOffset, spread, pattern, origin, aimMode, mover, onExpiry); }
	public FireDanmakuAction withAngleOffset(NumberProvider v) { return new FireDanmakuAction(bulletType, color, count, speed, lifetime, v, spread, pattern, origin, aimMode, mover, onExpiry); }
	public FireDanmakuAction withSpread(NumberProvider v) { return new FireDanmakuAction(bulletType, color, count, speed, lifetime, angleOffset, v, pattern, origin, aimMode, mover, onExpiry); }
	public FireDanmakuAction withPattern(PatternType v) { return new FireDanmakuAction(bulletType, color, count, speed, lifetime, angleOffset, spread, v, origin, aimMode, mover, onExpiry); }
	public FireDanmakuAction withOrigin(OriginConfig v) { return new FireDanmakuAction(bulletType, color, count, speed, lifetime, angleOffset, spread, pattern, v, aimMode, mover, onExpiry); }
	public FireDanmakuAction withAimMode(AimMode v) { return new FireDanmakuAction(bulletType, color, count, speed, lifetime, angleOffset, spread, pattern, origin, v, mover, onExpiry); }
	public FireDanmakuAction withMover(Optional<MoverConfig> v) { return new FireDanmakuAction(bulletType, color, count, speed, lifetime, angleOffset, spread, pattern, origin, aimMode, v, onExpiry); }
	public FireDanmakuAction withOnExpiry(Optional<List<SpellAction>> v) { return new FireDanmakuAction(bulletType, color, count, speed, lifetime, angleOffset, spread, pattern, origin, aimMode, mover, v); }

	@Override
	public void execute(SpellContext ctx) {
		CardHolder holder = ctx.holder();
		var diff = ctx.difficulty();

		int n = diff.adjustCount((int) count.get(ctx));
		double spd = diff.adjustSpeed(speed.get(ctx));
		int life = (int) lifetime.get(ctx);
		double angle = angleOffset.get(ctx);
		double spreadDeg = spread.get(ctx);

		Vec3 originPos = origin.resolve(ctx);
		Vec3 baseDir = aimMode.getBaseDirection(ctx);
		var ori = DanmakuHelper.getOrientation(baseDir);

		for (int i = 0; i < n; i++) {
			double a = angle;
			switch (pattern) {
				case RING -> a += (360.0 / n) * i;
				case LINE -> {
					if (n > 1) {
						a += spreadDeg * (i - (n - 1) / 2.0) / (n - 1);
					}
				}
				case RANDOM -> a += holder.random().nextDouble() * spreadDeg - spreadDeg / 2;
				case AIMED -> {} // all same direction
			}
			Vec3 dir = ori.rotateDegrees(a).scale(spd);
			var danmaku = holder.prepareDanmaku(life, dir, bulletType, color);
			danmaku.setPos(originPos);
			if (mover.isPresent()) {
				danmaku.mover = mover.get().create(originPos, dir);
			}
			if (onExpiry.isPresent()) {
				var trail = new DataDrivenTrailAction(onExpiry.get(), ctx.runtime(), ctx.definition());
				trail.setup(holder);
				danmaku.afterExpiry = trail;
			}
			holder.shoot(danmaku);
		}
	}

}
