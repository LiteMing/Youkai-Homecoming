package dev.xkmc.youkaishomecoming.content.spell.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.DanmakuHelper;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.HitBehavior;
import dev.xkmc.youkaishomecoming.content.spell.definition.*;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.CardHolder;
import dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.Vec3;

import dev.xkmc.youkaishomecoming.content.spell.definition.DanmakuDamageType;

import java.util.List;
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
		Optional<Double> delayedV1,
		Optional<DanmakuDamageType> damageType,
		NumberProvider thickness,
		Optional<List<SpellAction>> onExpiry,
		Optional<List<SpellAction>> onTrail,
		int trailInterval,
		Optional<List<SpellAction>> onHitEntity,
		Optional<List<SpellAction>> onHitBlock,
		HitBehavior hitBehaviorEntity,
		HitBehavior hitBehaviorBlock
) implements SpellAction {

	/** Backwards-compatible constructor without elevation, delayed mover, and damage type fields. */
	public FireLaserAction(YHDanmaku.Laser laserType, DyeColor color,
						   NumberProvider lifetime, NumberProvider length, NumberProvider angleOffset,
						   AimMode aimMode, OriginConfig origin, Optional<MoverConfig> mover,
						   int setupPrepare, int setupStart, int setupEnd) {
		this(laserType, color, lifetime, length, angleOffset, NumberProvider.constant(0), aimMode, origin, mover,
				setupPrepare, setupStart, setupEnd, Optional.empty(), Optional.empty(), Optional.empty(), NumberProvider.constant(1),
				Optional.empty(), Optional.empty(), 1, Optional.empty(), Optional.empty(),
				HitBehavior.CONTINUE, HitBehavior.CONTINUE);
	}

	/** Constructor with delayed mover but no elevation or damage type. */
	public FireLaserAction(YHDanmaku.Laser laserType, DyeColor color,
						   NumberProvider lifetime, NumberProvider length, NumberProvider angleOffset,
						   AimMode aimMode, OriginConfig origin, Optional<MoverConfig> mover,
						   int setupPrepare, int setupStart, int setupEnd,
						   Optional<Double> delayedV0, Optional<Double> delayedV1) {
		this(laserType, color, lifetime, length, angleOffset, NumberProvider.constant(0), aimMode, origin, mover,
				setupPrepare, setupStart, setupEnd, delayedV0, delayedV1, Optional.empty(), NumberProvider.constant(1),
				Optional.empty(), Optional.empty(), 1, Optional.empty(), Optional.empty(),
				HitBehavior.CONTINUE, HitBehavior.CONTINUE);
	}

	/** Constructor with all fields except damage type (14 args). */
	public FireLaserAction(YHDanmaku.Laser laserType, DyeColor color,
						   NumberProvider lifetime, NumberProvider length, NumberProvider angleOffset,
						   NumberProvider elevation, AimMode aimMode, OriginConfig origin,
						   Optional<MoverConfig> mover, int setupPrepare, int setupStart, int setupEnd,
						   Optional<Double> delayedV0, Optional<Double> delayedV1) {
		this(laserType, color, lifetime, length, angleOffset, elevation, aimMode, origin, mover,
				setupPrepare, setupStart, setupEnd, delayedV0, delayedV1, Optional.empty(), NumberProvider.constant(1),
				Optional.empty(), Optional.empty(), 1, Optional.empty(), Optional.empty(),
				HitBehavior.CONTINUE, HitBehavior.CONTINUE);
	}

	public FireLaserAction(YHDanmaku.Laser laserType, DyeColor color,
						   NumberProvider lifetime, NumberProvider length, NumberProvider angleOffset,
						   NumberProvider elevation, AimMode aimMode, OriginConfig origin,
						   Optional<MoverConfig> mover, int setupPrepare, int setupStart, int setupEnd,
						   Optional<Double> delayedV0, Optional<Double> delayedV1,
						   Optional<DanmakuDamageType> damageType) {
		this(laserType, color, lifetime, length, angleOffset, elevation, aimMode, origin, mover,
				setupPrepare, setupStart, setupEnd, delayedV0, delayedV1, damageType, NumberProvider.constant(1),
				Optional.empty(), Optional.empty(), 1, Optional.empty(), Optional.empty(),
				HitBehavior.CONTINUE, HitBehavior.CONTINUE);
	}

	/** Full constructor with all fields including hooks (23 args). */
	public FireLaserAction(YHDanmaku.Laser laserType, DyeColor color,
						   NumberProvider lifetime, NumberProvider length, NumberProvider angleOffset,
						   NumberProvider elevation, AimMode aimMode, OriginConfig origin,
						   Optional<MoverConfig> mover, int setupPrepare, int setupStart, int setupEnd,
						   Optional<Double> delayedV0, Optional<Double> delayedV1,
						   Optional<DanmakuDamageType> damageType, NumberProvider thickness,
						   Optional<List<SpellAction>> onExpiry, Optional<List<SpellAction>> onTrail,
						   int trailInterval, Optional<List<SpellAction>> onHitEntity,
						   Optional<List<SpellAction>> onHitBlock,
						   HitBehavior hitBehaviorEntity, HitBehavior hitBehaviorBlock) {
		this.laserType = laserType;
		this.color = color;
		this.lifetime = lifetime;
		this.length = length;
		this.angleOffset = angleOffset;
		this.elevation = elevation;
		this.aimMode = aimMode;
		this.origin = origin;
		this.mover = mover;
		this.setupPrepare = setupPrepare;
		this.setupStart = setupStart;
		this.setupEnd = setupEnd;
		this.delayedV0 = delayedV0;
		this.delayedV1 = delayedV1;
		this.damageType = damageType;
		this.thickness = thickness;
		this.onExpiry = onExpiry;
		this.onTrail = onTrail;
		this.trailInterval = trailInterval;
		this.onHitEntity = onHitEntity;
		this.onHitBlock = onHitBlock;
		this.hitBehaviorEntity = hitBehaviorEntity;
		this.hitBehaviorBlock = hitBehaviorBlock;
	}

	public static final com.mojang.serialization.MapCodec<FireLaserAction> BASE_MAP = RecordCodecBuilder.mapCodec(i -> i.group(
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
			Codec.DOUBLE.optionalFieldOf("delayed_v1").forGetter(FireLaserAction::delayedV1),
			DanmakuDamageType.CODEC.optionalFieldOf("damage_type").forGetter(FireLaserAction::damageType),
			NumberProvider.CODEC.optionalFieldOf("thickness", NumberProvider.constant(1)).forGetter(FireLaserAction::thickness)
	).apply(i, (lt, c, lf, ln, ao, el, am, o, m, sp, ss, se, dv0, dv1, ddt, th) ->
			new FireLaserAction(lt, c, lf, ln, ao, el, am, o, m, sp, ss, se, dv0, dv1, ddt, th,
					Optional.empty(), Optional.empty(), 1, Optional.empty(), Optional.empty(),
					HitBehavior.CONTINUE, HitBehavior.CONTINUE)));

	// Extra hook fields beyond 16-field limit (merged at same JSON level via codec composition)
	public static final Codec<FireLaserAction> CODEC = RecordCodecBuilder.create(i -> i.group(
			BASE_MAP.forGetter(f -> f),
			SpellAction.CODEC.listOf().optionalFieldOf("on_expiry").forGetter(FireLaserAction::onExpiry),
			SpellAction.CODEC.listOf().optionalFieldOf("on_trail").forGetter(FireLaserAction::onTrail),
			Codec.INT.optionalFieldOf("trail_interval", 1).forGetter(FireLaserAction::trailInterval),
			SpellAction.CODEC.listOf().optionalFieldOf("on_hit_entity").forGetter(FireLaserAction::onHitEntity),
			SpellAction.CODEC.listOf().optionalFieldOf("on_hit_block").forGetter(FireLaserAction::onHitBlock),
			HitBehavior.CODEC.optionalFieldOf("hit_behavior_entity", HitBehavior.CONTINUE).forGetter(FireLaserAction::hitBehaviorEntity),
			HitBehavior.CODEC.optionalFieldOf("hit_behavior_block", HitBehavior.CONTINUE).forGetter(FireLaserAction::hitBehaviorBlock)
	).apply(i, (base, oe, ot, ti, ohe, ohb, hbe, hbb) -> new FireLaserAction(
			base.laserType, base.color, base.lifetime, base.length, base.angleOffset, base.elevation,
			base.aimMode, base.origin, base.mover, base.setupPrepare, base.setupStart, base.setupEnd,
			base.delayedV0, base.delayedV1, base.damageType, base.thickness,
			oe, ot, ti, ohe, ohb, hbe, hbb)));

	// withXxx helper methods for editor use (preserve all fields)
	private FireLaserAction all(YHDanmaku.Laser lt, DyeColor c, NumberProvider lf, NumberProvider ln, NumberProvider ao, NumberProvider el, AimMode am, OriginConfig o, Optional<MoverConfig> m, int sp, int ss, int se, Optional<Double> dv0, Optional<Double> dv1, Optional<DanmakuDamageType> ddt, NumberProvider th, Optional<List<SpellAction>> oe, Optional<List<SpellAction>> ot, int ti, Optional<List<SpellAction>> ohe, Optional<List<SpellAction>> ohb, HitBehavior hbe, HitBehavior hbb) { return new FireLaserAction(lt, c, lf, ln, ao, el, am, o, m, sp, ss, se, dv0, dv1, ddt, th, oe, ot, ti, ohe, ohb, hbe, hbb); }
	public FireLaserAction withLaserType(YHDanmaku.Laser v) { return all(v, color, lifetime, length, angleOffset, elevation, aimMode, origin, mover, setupPrepare, setupStart, setupEnd, delayedV0, delayedV1, damageType, thickness, onExpiry, onTrail, trailInterval, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock); }
	public FireLaserAction withColor(DyeColor v) { return all(laserType, v, lifetime, length, angleOffset, elevation, aimMode, origin, mover, setupPrepare, setupStart, setupEnd, delayedV0, delayedV1, damageType, thickness, onExpiry, onTrail, trailInterval, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock); }
	public FireLaserAction withLifetime(NumberProvider v) { return all(laserType, color, v, length, angleOffset, elevation, aimMode, origin, mover, setupPrepare, setupStart, setupEnd, delayedV0, delayedV1, damageType, thickness, onExpiry, onTrail, trailInterval, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock); }
	public FireLaserAction withLength(NumberProvider v) { return all(laserType, color, lifetime, v, angleOffset, elevation, aimMode, origin, mover, setupPrepare, setupStart, setupEnd, delayedV0, delayedV1, damageType, thickness, onExpiry, onTrail, trailInterval, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock); }
	public FireLaserAction withAngleOffset(NumberProvider v) { return all(laserType, color, lifetime, length, v, elevation, aimMode, origin, mover, setupPrepare, setupStart, setupEnd, delayedV0, delayedV1, damageType, thickness, onExpiry, onTrail, trailInterval, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock); }
	public FireLaserAction withElevation(NumberProvider v) { return all(laserType, color, lifetime, length, angleOffset, v, aimMode, origin, mover, setupPrepare, setupStart, setupEnd, delayedV0, delayedV1, damageType, thickness, onExpiry, onTrail, trailInterval, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock); }
	public FireLaserAction withAimMode(AimMode v) { return all(laserType, color, lifetime, length, angleOffset, elevation, v, origin, mover, setupPrepare, setupStart, setupEnd, delayedV0, delayedV1, damageType, thickness, onExpiry, onTrail, trailInterval, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock); }
	public FireLaserAction withOrigin(OriginConfig v) { return all(laserType, color, lifetime, length, angleOffset, elevation, aimMode, v, mover, setupPrepare, setupStart, setupEnd, delayedV0, delayedV1, damageType, thickness, onExpiry, onTrail, trailInterval, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock); }
	public FireLaserAction withMover(Optional<MoverConfig> v) { return all(laserType, color, lifetime, length, angleOffset, elevation, aimMode, origin, v, setupPrepare, setupStart, setupEnd, delayedV0, delayedV1, damageType, thickness, onExpiry, onTrail, trailInterval, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock); }
	public FireLaserAction withSetupPrepare(int v) { return all(laserType, color, lifetime, length, angleOffset, elevation, aimMode, origin, mover, v, setupStart, setupEnd, delayedV0, delayedV1, damageType, thickness, onExpiry, onTrail, trailInterval, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock); }
	public FireLaserAction withSetupStart(int v) { return all(laserType, color, lifetime, length, angleOffset, elevation, aimMode, origin, mover, setupPrepare, v, setupEnd, delayedV0, delayedV1, damageType, thickness, onExpiry, onTrail, trailInterval, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock); }
	public FireLaserAction withSetupEnd(int v) { return all(laserType, color, lifetime, length, angleOffset, elevation, aimMode, origin, mover, setupPrepare, setupStart, v, delayedV0, delayedV1, damageType, thickness, onExpiry, onTrail, trailInterval, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock); }
	public FireLaserAction withDelayedV0(Optional<Double> v) { return all(laserType, color, lifetime, length, angleOffset, elevation, aimMode, origin, mover, setupPrepare, setupStart, setupEnd, v, delayedV1, damageType, thickness, onExpiry, onTrail, trailInterval, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock); }
	public FireLaserAction withDelayedV1(Optional<Double> v) { return all(laserType, color, lifetime, length, angleOffset, elevation, aimMode, origin, mover, setupPrepare, setupStart, setupEnd, delayedV0, v, damageType, thickness, onExpiry, onTrail, trailInterval, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock); }
	public FireLaserAction withDamageType(Optional<DanmakuDamageType> v) { return all(laserType, color, lifetime, length, angleOffset, elevation, aimMode, origin, mover, setupPrepare, setupStart, setupEnd, delayedV0, delayedV1, v, thickness, onExpiry, onTrail, trailInterval, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock); }
	public FireLaserAction withThickness(NumberProvider v) { return all(laserType, color, lifetime, length, angleOffset, elevation, aimMode, origin, mover, setupPrepare, setupStart, setupEnd, delayedV0, delayedV1, damageType, v, onExpiry, onTrail, trailInterval, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock); }
	public FireLaserAction withOnExpiry(Optional<List<SpellAction>> v) { return all(laserType, color, lifetime, length, angleOffset, elevation, aimMode, origin, mover, setupPrepare, setupStart, setupEnd, delayedV0, delayedV1, damageType, thickness, v, onTrail, trailInterval, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock); }
	public FireLaserAction withOnTrail(Optional<List<SpellAction>> v) { return all(laserType, color, lifetime, length, angleOffset, elevation, aimMode, origin, mover, setupPrepare, setupStart, setupEnd, delayedV0, delayedV1, damageType, thickness, onExpiry, v, trailInterval, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock); }
	public FireLaserAction withTrailInterval(int v) { return all(laserType, color, lifetime, length, angleOffset, elevation, aimMode, origin, mover, setupPrepare, setupStart, setupEnd, delayedV0, delayedV1, damageType, thickness, onExpiry, onTrail, v, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock); }
	public FireLaserAction withOnHitEntity(Optional<List<SpellAction>> v) { return all(laserType, color, lifetime, length, angleOffset, elevation, aimMode, origin, mover, setupPrepare, setupStart, setupEnd, delayedV0, delayedV1, damageType, thickness, onExpiry, onTrail, trailInterval, v, onHitBlock, hitBehaviorEntity, hitBehaviorBlock); }
	public FireLaserAction withOnHitBlock(Optional<List<SpellAction>> v) { return all(laserType, color, lifetime, length, angleOffset, elevation, aimMode, origin, mover, setupPrepare, setupStart, setupEnd, delayedV0, delayedV1, damageType, thickness, onExpiry, onTrail, trailInterval, onHitEntity, v, hitBehaviorEntity, hitBehaviorBlock); }
	public FireLaserAction withHitBehaviorEntity(HitBehavior v) { return all(laserType, color, lifetime, length, angleOffset, elevation, aimMode, origin, mover, setupPrepare, setupStart, setupEnd, delayedV0, delayedV1, damageType, thickness, onExpiry, onTrail, trailInterval, onHitEntity, onHitBlock, v, hitBehaviorBlock); }
	public FireLaserAction withHitBehaviorBlock(HitBehavior v) { return all(laserType, color, lifetime, length, angleOffset, elevation, aimMode, origin, mover, setupPrepare, setupStart, setupEnd, delayedV0, delayedV1, damageType, thickness, onExpiry, onTrail, trailInterval, onHitEntity, onHitBlock, hitBehaviorEntity, v); }

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
		NumberProvider scaleFunction = thickness instanceof NumberProviders.Constant ? null : thickness;
		laser.configureVisualScale((float) thickness.get(ctx), scaleFunction);
		// Apply per-action damage type override
		if (damageType.isPresent()) {
			laser.damageTypeOverride = damageType.get();
		}
		// Per-laser action hooks (mirror FireDanmakuAction)
		if (onExpiry.isPresent()) {
			var expiryAction = new DataDrivenTrailAction(onExpiry.get(), ctx.runtime(), ctx.definition());
			expiryAction.setup(holder);
			laser.afterExpiry = expiryAction;
		}
		if (onTrail.isPresent()) {
			var trailAction = new DataDrivenTrailAction(onTrail.get(), ctx.runtime(), ctx.definition());
			trailAction.setup(holder);
			laser.onTrail = trailAction;
			laser.trailInterval = Math.max(1, trailInterval);
		}
		if (onHitEntity.isPresent()) {
			var hitAction = new DataDrivenTrailAction(onHitEntity.get(), ctx.runtime(), ctx.definition());
			hitAction.setup(holder);
			laser.onHitEntityAction = hitAction;
		}
		if (onHitBlock.isPresent()) {
			var hitAction = new DataDrivenTrailAction(onHitBlock.get(), ctx.runtime(), ctx.definition());
			hitAction.setup(holder);
			laser.onHitBlockAction = hitAction;
		}
		laser.hitBehaviorEntity = hitBehaviorEntity;
		laser.hitBehaviorBlock = hitBehaviorBlock;
		if (onHitBlock.isPresent() || hitBehaviorBlock != HitBehavior.CONTINUE) {
			laser.setBypassWall(false);
		}
		if (setupPrepare > 0 || setupStart > 0 || setupEnd > 0) {
			laser.setupTime(setupPrepare, setupStart, life, setupEnd);
		}
		// Delayed mover: prepare → slow expand → full speed
		if (delayedV0.isPresent() && delayedV1.isPresent()) {
			laser.setDelayedMover(delayedV0.get().floatValue(), delayedV1.get().floatValue(),
					setupPrepare, setupStart);
		} else if (mover.isPresent()) {
			Vec3 casterPos = holder.self() != null ? holder.self().position() : originPos;
			MoverConfig moverConfig = mover.get();
			Vec3 targetPos = moverConfig.resolveTargetPos(ctx, originPos);
			laser.mover = moverConfig.create(ctx, originPos, dir, baseDir, targetPos, casterPos);
		}
		// Must happen after mover assignment and before shoot/network serialization:
		// place the laser at the mover's pos(0) and seed rotation from pos(1) - pos(0).
		laser.initializeMoverAtSpawn();
		holder.shoot(laser);
	}

}
