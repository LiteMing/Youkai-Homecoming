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
 * <p>
 * When {@code delayed_v0} and {@code delayed_v1} are set, the laser uses a delayed mover:
 * <ol>
 *   <li>Stay in place for {@code setup_prepare} ticks</li>
 *   <li>Move forward at {@code delayed_v0} speed for {@code setup_start} ticks (expand phase)</li>
 *   <li>Move forward at {@code delayed_v1} speed for remaining lifetime</li>
 * </ol>
 * This replicates the legacy {@code ItemLaserEntity.setDelayedMover(v0, v1, prepare, setup)} pattern.
 */
public record FireLaserAction(
		YHDanmaku.Laser laserType,
		DyeColor color,
		NumberProvider lifetime,
		NumberProvider length,
		NumberProvider angleOffset,
		NumberProvider elevation,
		AimMode aimMode,
		OriginConfig origin,
		Optional<MoverConfig> mover,
		int setupPrepare,
		int setupStart,
		int setupEnd,
		Optional<Double> delayedV0,
		Optional<Double> delayedV1
) implements SpellAction {

	/** Backwards-compatible constructor without elevation and delayed mover fields. */
	public FireLaserAction(YHDanmaku.Laser laserType, DyeColor color,
						   NumberProvider lifetime, NumberProvider length, NumberProvider angleOffset,
						   AimMode aimMode, OriginConfig origin, Optional<MoverConfig> mover,
						   int setupPrepare, int setupStart, int setupEnd) {
		this(laserType, color, lifetime, length, angleOffset, NumberProvider.constant(0), aimMode, origin, mover,
				setupPrepare, setupStart, setupEnd, Optional.empty(), Optional.empty());
	}

	/** Constructor with delayed mover but no elevation. */
	public FireLaserAction(YHDanmaku.Laser laserType, DyeColor color,
						   NumberProvider lifetime, NumberProvider length, NumberProvider angleOffset,
						   AimMode aimMode, OriginConfig origin, Optional<MoverConfig> mover,
						   int setupPrepare, int setupStart, int setupEnd,
						   Optional<Double> delayedV0, Optional<Double> delayedV1) {
		this(laserType, color, lifetime, length, angleOffset, NumberProvider.constant(0), aimMode, origin, mover,
				setupPrepare, setupStart, setupEnd, delayedV0, delayedV1);
	}

	public static final Codec<FireLaserAction> CODEC = RecordCodecBuilder.create(i -> i.group(
			SpellCodecs.LASER_CODEC.optionalFieldOf("laser", YHDanmaku.Laser.LASER).forGetter(FireLaserAction::laserType),
			SpellCodecs.DYE_COLOR_CODEC.fieldOf("color").forGetter(FireLaserAction::color),
			NumberProvider.CODEC.fieldOf("lifetime").forGetter(FireLaserAction::lifetime),
			NumberProvider.CODEC.optionalFieldOf("length", NumberProvider.constant(80)).forGetter(FireLaserAction::length),
			NumberProvider.CODEC.optionalFieldOf("angle_offset", NumberProvider.constant(0)).forGetter(FireLaserAction::angleOffset),
			NumberProvider.CODEC.optionalFieldOf("elevation", NumberProvider.constant(0)).forGetter(FireLaserAction::elevation),
			AimMode.CODEC.optionalFieldOf("aim_mode", new AimMode.AimModes.Target()).forGetter(FireLaserAction::aimMode),
			OriginConfig.CODEC.optionalFieldOf("origin", OriginConfig.caster()).forGetter(FireLaserAction::origin),
			MoverConfig.CODEC.optionalFieldOf("mover").forGetter(FireLaserAction::mover),
			Codec.INT.optionalFieldOf("setup_prepare", 0).forGetter(FireLaserAction::setupPrepare),
			Codec.INT.optionalFieldOf("setup_start", 0).forGetter(FireLaserAction::setupStart),
			Codec.INT.optionalFieldOf("setup_end", 0).forGetter(FireLaserAction::setupEnd),
			Codec.DOUBLE.optionalFieldOf("delayed_v0").forGetter(FireLaserAction::delayedV0),
			Codec.DOUBLE.optionalFieldOf("delayed_v1").forGetter(FireLaserAction::delayedV1)
	).apply(i, FireLaserAction::new));

	// withXxx helper methods for editor use
	private FireLaserAction all(YHDanmaku.Laser lt, DyeColor c, NumberProvider lf, NumberProvider ln, NumberProvider ao, NumberProvider el, AimMode am, OriginConfig o, Optional<MoverConfig> m, int sp, int ss, int se, Optional<Double> dv0, Optional<Double> dv1) { return new FireLaserAction(lt, c, lf, ln, ao, el, am, o, m, sp, ss, se, dv0, dv1); }
	public FireLaserAction withLaserType(YHDanmaku.Laser v) { return all(v, color, lifetime, length, angleOffset, elevation, aimMode, origin, mover, setupPrepare, setupStart, setupEnd, delayedV0, delayedV1); }
	public FireLaserAction withColor(DyeColor v) { return all(laserType, v, lifetime, length, angleOffset, elevation, aimMode, origin, mover, setupPrepare, setupStart, setupEnd, delayedV0, delayedV1); }
	public FireLaserAction withLifetime(NumberProvider v) { return all(laserType, color, v, length, angleOffset, elevation, aimMode, origin, mover, setupPrepare, setupStart, setupEnd, delayedV0, delayedV1); }
	public FireLaserAction withLength(NumberProvider v) { return all(laserType, color, lifetime, v, angleOffset, elevation, aimMode, origin, mover, setupPrepare, setupStart, setupEnd, delayedV0, delayedV1); }
	public FireLaserAction withAngleOffset(NumberProvider v) { return all(laserType, color, lifetime, length, v, elevation, aimMode, origin, mover, setupPrepare, setupStart, setupEnd, delayedV0, delayedV1); }
	public FireLaserAction withElevation(NumberProvider v) { return all(laserType, color, lifetime, length, angleOffset, v, aimMode, origin, mover, setupPrepare, setupStart, setupEnd, delayedV0, delayedV1); }
	public FireLaserAction withAimMode(AimMode v) { return all(laserType, color, lifetime, length, angleOffset, elevation, v, origin, mover, setupPrepare, setupStart, setupEnd, delayedV0, delayedV1); }
	public FireLaserAction withOrigin(OriginConfig v) { return all(laserType, color, lifetime, length, angleOffset, elevation, aimMode, v, mover, setupPrepare, setupStart, setupEnd, delayedV0, delayedV1); }
	public FireLaserAction withMover(Optional<MoverConfig> v) { return all(laserType, color, lifetime, length, angleOffset, elevation, aimMode, origin, v, setupPrepare, setupStart, setupEnd, delayedV0, delayedV1); }
	public FireLaserAction withSetupPrepare(int v) { return all(laserType, color, lifetime, length, angleOffset, elevation, aimMode, origin, mover, v, setupStart, setupEnd, delayedV0, delayedV1); }
	public FireLaserAction withSetupStart(int v) { return all(laserType, color, lifetime, length, angleOffset, elevation, aimMode, origin, mover, setupPrepare, v, setupEnd, delayedV0, delayedV1); }
	public FireLaserAction withSetupEnd(int v) { return all(laserType, color, lifetime, length, angleOffset, elevation, aimMode, origin, mover, setupPrepare, setupStart, v, delayedV0, delayedV1); }
	public FireLaserAction withDelayedV0(Optional<Double> v) { return all(laserType, color, lifetime, length, angleOffset, elevation, aimMode, origin, mover, setupPrepare, setupStart, setupEnd, v, delayedV1); }
	public FireLaserAction withDelayedV1(Optional<Double> v) { return all(laserType, color, lifetime, length, angleOffset, elevation, aimMode, origin, mover, setupPrepare, setupStart, setupEnd, delayedV0, v); }

	@Override
	public void execute(SpellContext ctx) {
		CardHolder holder = ctx.holder();

		int life = (int) lifetime.get(ctx);
		float len = (float) length.get(ctx);
		double angle = angleOffset.get(ctx);
		double elev = elevation.get(ctx);

		Vec3 originPos = origin.resolve(ctx);
		Vec3 baseDir = aimMode.getBaseDirection(ctx, originPos);

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
		if (angle != 0 || elev != 0) {
			var ori = DanmakuHelper.getOrientation(baseDir);
			dir = ori.rotateDegrees(angle, elev);
		} else {
			dir = baseDir;
		}

		var laser = holder.prepareLaser(life, originPos, dir, len, laserType, color);
		if (setupPrepare > 0 || setupStart > 0 || setupEnd > 0) {
			laser.setupTime(setupPrepare, setupStart, life, setupEnd);
		}
		// Delayed mover: prepare → slow expand → full speed
		if (delayedV0.isPresent() && delayedV1.isPresent()) {
			laser.setDelayedMover(delayedV0.get().floatValue(), delayedV1.get().floatValue(),
					setupPrepare, setupStart);
		} else if (mover.isPresent()) {
			laser.mover = mover.get().create(originPos, dir);
		}
		holder.shoot(laser);
	}

}
