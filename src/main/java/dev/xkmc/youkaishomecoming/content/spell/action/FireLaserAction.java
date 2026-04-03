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

import java.util.Optional;

/**
 * Data-driven action to fire laser projectiles.
 */
public record FireLaserAction(
		YHDanmaku.Laser laserType,
		DyeColor color,
		NumberProvider lifetime,
		NumberProvider length,
		NumberProvider angleOffset,
		AimMode aimMode,
		OriginConfig origin,
		Optional<MoverConfig> mover,
		int setupPrepare,
		int setupStart,
		int setupEnd
) implements SpellAction {

	public static final Codec<FireLaserAction> CODEC = RecordCodecBuilder.create(i -> i.group(
			SpellCodecs.LASER_CODEC.optionalFieldOf("laser", YHDanmaku.Laser.LASER).forGetter(FireLaserAction::laserType),
			SpellCodecs.DYE_COLOR_CODEC.fieldOf("color").forGetter(FireLaserAction::color),
			NumberProvider.CODEC.fieldOf("lifetime").forGetter(FireLaserAction::lifetime),
			NumberProvider.CODEC.optionalFieldOf("length", NumberProvider.constant(80)).forGetter(FireLaserAction::length),
			NumberProvider.CODEC.optionalFieldOf("angle_offset", NumberProvider.constant(0)).forGetter(FireLaserAction::angleOffset),
			AimMode.CODEC.optionalFieldOf("aim_mode", new AimMode.AimModes.Target()).forGetter(FireLaserAction::aimMode),
			OriginConfig.CODEC.optionalFieldOf("origin", OriginConfig.caster()).forGetter(FireLaserAction::origin),
			MoverConfig.CODEC.optionalFieldOf("mover").forGetter(FireLaserAction::mover),
			Codec.INT.optionalFieldOf("setup_prepare", 0).forGetter(FireLaserAction::setupPrepare),
			Codec.INT.optionalFieldOf("setup_start", 0).forGetter(FireLaserAction::setupStart),
			Codec.INT.optionalFieldOf("setup_end", 0).forGetter(FireLaserAction::setupEnd)
	).apply(i, FireLaserAction::new));

	// withXxx helper methods for editor use
	public FireLaserAction withLaserType(YHDanmaku.Laser v) { return new FireLaserAction(v, color, lifetime, length, angleOffset, aimMode, origin, mover, setupPrepare, setupStart, setupEnd); }
	public FireLaserAction withColor(DyeColor v) { return new FireLaserAction(laserType, v, lifetime, length, angleOffset, aimMode, origin, mover, setupPrepare, setupStart, setupEnd); }
	public FireLaserAction withLifetime(NumberProvider v) { return new FireLaserAction(laserType, color, v, length, angleOffset, aimMode, origin, mover, setupPrepare, setupStart, setupEnd); }
	public FireLaserAction withLength(NumberProvider v) { return new FireLaserAction(laserType, color, lifetime, v, angleOffset, aimMode, origin, mover, setupPrepare, setupStart, setupEnd); }
	public FireLaserAction withAngleOffset(NumberProvider v) { return new FireLaserAction(laserType, color, lifetime, length, v, aimMode, origin, mover, setupPrepare, setupStart, setupEnd); }
	public FireLaserAction withAimMode(AimMode v) { return new FireLaserAction(laserType, color, lifetime, length, angleOffset, v, origin, mover, setupPrepare, setupStart, setupEnd); }
	public FireLaserAction withOrigin(OriginConfig v) { return new FireLaserAction(laserType, color, lifetime, length, angleOffset, aimMode, v, mover, setupPrepare, setupStart, setupEnd); }
	public FireLaserAction withMover(Optional<MoverConfig> v) { return new FireLaserAction(laserType, color, lifetime, length, angleOffset, aimMode, origin, v, setupPrepare, setupStart, setupEnd); }
	public FireLaserAction withSetupPrepare(int v) { return new FireLaserAction(laserType, color, lifetime, length, angleOffset, aimMode, origin, mover, v, setupStart, setupEnd); }
	public FireLaserAction withSetupStart(int v) { return new FireLaserAction(laserType, color, lifetime, length, angleOffset, aimMode, origin, mover, setupPrepare, v, setupEnd); }
	public FireLaserAction withSetupEnd(int v) { return new FireLaserAction(laserType, color, lifetime, length, angleOffset, aimMode, origin, mover, setupPrepare, setupStart, v); }

	@Override
	public void execute(SpellContext ctx) {
		CardHolder holder = ctx.holder();

		int life = (int) lifetime.get(ctx);
		float len = (float) length.get(ctx);
		double angle = angleOffset.get(ctx);

		Vec3 originPos = origin.resolve(ctx);
		Vec3 baseDir = aimMode.getBaseDirection(ctx);

		// Apply origin.rotation to base direction so the entire pattern rotates together
		double originRot = origin.rotation().get(ctx);
		if (originRot != 0) {
			double rad = Math.toRadians(originRot);
			double cos = Math.cos(rad), sin = Math.sin(rad);
			baseDir = new Vec3(
					baseDir.x * cos - baseDir.z * sin,
					baseDir.y,
					baseDir.x * sin + baseDir.z * cos
			);
		}

		Vec3 dir;
		if (angle != 0) {
			var ori = DanmakuHelper.getOrientation(baseDir);
			dir = ori.rotateDegrees(angle);
		} else {
			dir = baseDir;
		}

		var laser = holder.prepareLaser(life, originPos, dir, len, laserType, color);
		if (setupPrepare > 0 || setupStart > 0 || setupEnd > 0) {
			laser.setupTime(setupPrepare, setupStart, life, setupEnd);
		}
		if (mover.isPresent()) {
			laser.mover = mover.get().create(originPos, dir);
		}
		holder.shoot(laser);
	}

}
