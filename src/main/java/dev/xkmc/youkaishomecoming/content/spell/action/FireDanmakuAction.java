package dev.xkmc.youkaishomecoming.content.spell.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.content.spell.definition.*;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.CardHolder;
import dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.Vec3;

import dev.xkmc.youkaishomecoming.content.entity.danmaku.HitBehavior;
import dev.xkmc.youkaishomecoming.content.spell.definition.DanmakuDamageType;

import java.util.List;
import java.util.Optional;

/**
 * Data-driven action to fire danmaku projectiles.
 * Supports ring, line, random, aimed, and nested_ring patterns with configurable parameters.
 */
public record FireDanmakuAction(
		BulletProvider bulletType,
		ColorProvider color,
		NumberProvider count,
		NumberProvider speed,
		NumberProvider lifetime,
		NumberProvider angleOffset,
		NumberProvider spread,
		NumberProvider elevation,
		PatternType pattern,
		OriginConfig origin,
		AimMode aimMode,
		Optional<MoverConfig> mover,
		Optional<NumberProvider> outerCount,
		Optional<List<SpellAction>> onExpiry,
		Optional<List<SpellAction>> onTrail,
		int trailInterval,
		Optional<NumberProvider> tiltAngle,
		Optional<List<SpellAction>> onHitEntity,
		Optional<List<SpellAction>> onHitBlock,
		HitBehavior hitBehaviorEntity,
		HitBehavior hitBehaviorBlock,
		Optional<DanmakuDamageType> damageType,
		Optional<GroupRotation> groupRotation,
		NumberProvider size
) implements SpellAction {

	/** Backwards-compatible constructor without tiltAngle/onHit/damageType fields (16 args). */
	public FireDanmakuAction(
			YHDanmaku.Bullet bulletType, ColorProvider color,
			NumberProvider count, NumberProvider speed, NumberProvider lifetime,
			NumberProvider angleOffset, NumberProvider spread, NumberProvider elevation,
			PatternType pattern, OriginConfig origin, AimMode aimMode,
			Optional<MoverConfig> mover, Optional<NumberProvider> outerCount,
			Optional<List<SpellAction>> onExpiry, Optional<List<SpellAction>> onTrail,
			int trailInterval) {
		this(BulletProvider.constant(bulletType), color, count, speed, lifetime, angleOffset, spread, elevation,
				pattern, origin, aimMode, mover, outerCount, onExpiry, onTrail, trailInterval,
				Optional.empty(), Optional.empty(), Optional.empty(), HitBehavior.DISCARD, HitBehavior.CONTINUE,
				Optional.empty(), Optional.empty(), NumberProvider.constant(1));
	}

	/** Backwards-compatible constructor with tiltAngle but without onHit/damageType fields (17 args). */
	public FireDanmakuAction(
			YHDanmaku.Bullet bulletType, ColorProvider color,
			NumberProvider count, NumberProvider speed, NumberProvider lifetime,
			NumberProvider angleOffset, NumberProvider spread, NumberProvider elevation,
			PatternType pattern, OriginConfig origin, AimMode aimMode,
			Optional<MoverConfig> mover, Optional<NumberProvider> outerCount,
			Optional<List<SpellAction>> onExpiry, Optional<List<SpellAction>> onTrail,
			int trailInterval, Optional<NumberProvider> tiltAngle) {
		this(BulletProvider.constant(bulletType), color, count, speed, lifetime, angleOffset, spread, elevation,
				pattern, origin, aimMode, mover, outerCount, onExpiry, onTrail, trailInterval,
				tiltAngle, Optional.empty(), Optional.empty(), HitBehavior.DISCARD, HitBehavior.CONTINUE,
				Optional.empty(), Optional.empty(), NumberProvider.constant(1));
	}

	public FireDanmakuAction(
			YHDanmaku.Bullet bulletType, ColorProvider color,
			NumberProvider count, NumberProvider speed, NumberProvider lifetime,
			NumberProvider angleOffset, NumberProvider spread, NumberProvider elevation,
			PatternType pattern, OriginConfig origin, AimMode aimMode,
			Optional<MoverConfig> mover, Optional<NumberProvider> outerCount,
			Optional<List<SpellAction>> onExpiry, Optional<List<SpellAction>> onTrail,
			int trailInterval, Optional<NumberProvider> tiltAngle,
			Optional<List<SpellAction>> onHitEntity, Optional<List<SpellAction>> onHitBlock,
			HitBehavior hitBehaviorEntity, HitBehavior hitBehaviorBlock,
			Optional<DanmakuDamageType> damageType, Optional<GroupRotation> groupRotation) {
		this(BulletProvider.constant(bulletType), color, count, speed, lifetime, angleOffset, spread, elevation,
				pattern, origin, aimMode, mover, outerCount, onExpiry, onTrail, trailInterval,
				tiltAngle, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock,
				damageType, groupRotation, NumberProvider.constant(1));
	}

	public FireDanmakuAction(
			YHDanmaku.Bullet bulletType, ColorProvider color,
			NumberProvider count, NumberProvider speed, NumberProvider lifetime,
			NumberProvider angleOffset, NumberProvider spread, NumberProvider elevation,
			PatternType pattern, OriginConfig origin, AimMode aimMode,
			Optional<MoverConfig> mover, Optional<NumberProvider> outerCount,
			Optional<List<SpellAction>> onExpiry, Optional<List<SpellAction>> onTrail,
			int trailInterval, Optional<NumberProvider> tiltAngle,
			Optional<List<SpellAction>> onHitEntity, Optional<List<SpellAction>> onHitBlock,
			HitBehavior hitBehaviorEntity, HitBehavior hitBehaviorBlock,
			Optional<DanmakuDamageType> damageType, Optional<GroupRotation> groupRotation,
			NumberProvider size) {
		this(BulletProvider.constant(bulletType), color, count, speed, lifetime, angleOffset, spread, elevation,
				pattern, origin, aimMode, mover, outerCount, onExpiry, onTrail, trailInterval,
				tiltAngle, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock,
				damageType, groupRotation, size);
	}

	// 16-field group (DFU RecordCodecBuilder limit)
	private static final com.mojang.serialization.MapCodec<FireDanmakuAction> BASE_MAP = RecordCodecBuilder.mapCodec(i -> i.group(
			BulletProvider.CODEC.fieldOf("bullet").forGetter(FireDanmakuAction::bulletType),
			ColorProvider.CODEC.fieldOf("color").forGetter(FireDanmakuAction::color),
			NumberProvider.CODEC.fieldOf("count").forGetter(FireDanmakuAction::count),
			NumberProvider.CODEC.fieldOf("speed").forGetter(FireDanmakuAction::speed),
			NumberProvider.CODEC.fieldOf("lifetime").forGetter(FireDanmakuAction::lifetime),
			NumberProvider.CODEC.optionalFieldOf("angle_offset", NumberProvider.constant(0)).forGetter(FireDanmakuAction::angleOffset),
			NumberProvider.CODEC.optionalFieldOf("spread", NumberProvider.constant(360)).forGetter(FireDanmakuAction::spread),
			NumberProvider.CODEC.optionalFieldOf("elevation", NumberProvider.constant(0)).forGetter(FireDanmakuAction::elevation),
			PatternType.CODEC.optionalFieldOf("pattern", PatternType.RING).forGetter(FireDanmakuAction::pattern),
			OriginConfig.CODEC.optionalFieldOf("origin", OriginConfig.caster()).forGetter(FireDanmakuAction::origin),
			AimMode.CODEC.optionalFieldOf("aim_mode", new AimMode.AimModes.Target()).forGetter(FireDanmakuAction::aimMode),
			MoverConfig.CODEC.optionalFieldOf("mover").forGetter(FireDanmakuAction::mover),
			NumberProvider.CODEC.optionalFieldOf("outer_count").forGetter(FireDanmakuAction::outerCount),
			SpellAction.CODEC.listOf().optionalFieldOf("on_expiry").forGetter(FireDanmakuAction::onExpiry),
			SpellAction.CODEC.listOf().optionalFieldOf("on_trail").forGetter(FireDanmakuAction::onTrail),
			Codec.INT.optionalFieldOf("trail_interval", 1).forGetter(FireDanmakuAction::trailInterval)
	).apply(i, (bt, c, cnt, spd, lt, ao, sp, el, pt, o, am, m, oc, oe, ot, ti) ->
			new FireDanmakuAction(bt, c, cnt, spd, lt, ao, sp, el, pt, o, am, m, oc, oe, ot, ti,
					Optional.empty(), Optional.empty(), Optional.empty(), HitBehavior.DISCARD, HitBehavior.CONTINUE,
					Optional.empty(), Optional.empty(), NumberProvider.constant(1))));

	// Extra fields beyond 16-field limit (merged at same JSON level via codec composition)
	public static final Codec<FireDanmakuAction> CODEC = RecordCodecBuilder.create(i -> i.group(
			BASE_MAP.forGetter(fda -> fda),
			NumberProvider.CODEC.optionalFieldOf("tilt_angle").forGetter(FireDanmakuAction::tiltAngle),
			SpellAction.CODEC.listOf().optionalFieldOf("on_hit_entity").forGetter(FireDanmakuAction::onHitEntity),
			SpellAction.CODEC.listOf().optionalFieldOf("on_hit_block").forGetter(FireDanmakuAction::onHitBlock),
			HitBehavior.CODEC.optionalFieldOf("hit_behavior_entity", HitBehavior.DISCARD).forGetter(FireDanmakuAction::hitBehaviorEntity),
			HitBehavior.CODEC.optionalFieldOf("hit_behavior_block", HitBehavior.CONTINUE).forGetter(FireDanmakuAction::hitBehaviorBlock),
			DanmakuDamageType.CODEC.optionalFieldOf("damage_type").forGetter(FireDanmakuAction::damageType),
			GroupRotation.CODEC.optionalFieldOf("group_rotation").forGetter(FireDanmakuAction::groupRotation),
			NumberProvider.CODEC.optionalFieldOf("size", NumberProvider.constant(1)).forGetter(FireDanmakuAction::size)
	).apply(i, (base, tilt, hitEnt, hitBlk, hitEntBhv, hitBlkBhv, dmgType, grpRot, size) -> new FireDanmakuAction(
			base.bulletType, base.color, base.count, base.speed, base.lifetime,
			base.angleOffset, base.spread, base.elevation, base.pattern, base.origin,
			base.aimMode, base.mover, base.outerCount, base.onExpiry, base.onTrail,
			base.trailInterval, tilt, hitEnt, hitBlk, hitEntBhv, hitBlkBhv, dmgType, grpRot, size
	)));

	// withXxx helper methods for editor use (preserve all 23 fields)
	private FireDanmakuAction all(BulletProvider bt, ColorProvider c, NumberProvider cnt, NumberProvider spd, NumberProvider lt, NumberProvider ao, NumberProvider sp, NumberProvider el, PatternType pt, OriginConfig o, AimMode am, Optional<MoverConfig> m, Optional<NumberProvider> oc, Optional<List<SpellAction>> oe, Optional<List<SpellAction>> ot, int ti, Optional<NumberProvider> ta, Optional<List<SpellAction>> ohe, Optional<List<SpellAction>> ohb, HitBehavior hbe, HitBehavior hbb, Optional<DanmakuDamageType> ddt, Optional<GroupRotation> gr, NumberProvider sz) { return new FireDanmakuAction(bt, c, cnt, spd, lt, ao, sp, el, pt, o, am, m, oc, oe, ot, ti, ta, ohe, ohb, hbe, hbb, ddt, gr, sz); }
	public FireDanmakuAction withBulletType(YHDanmaku.Bullet v) { return withBulletProvider(BulletProvider.constant(v)); }
	public FireDanmakuAction withBulletProvider(BulletProvider v) { return all(v, color, count, speed, lifetime, angleOffset, spread, elevation, pattern, origin, aimMode, mover, outerCount, onExpiry, onTrail, trailInterval, tiltAngle, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock, damageType, groupRotation, size); }
	public FireDanmakuAction withColor(ColorProvider v) { return all(bulletType, v, count, speed, lifetime, angleOffset, spread, elevation, pattern, origin, aimMode, mover, outerCount, onExpiry, onTrail, trailInterval, tiltAngle, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock, damageType, groupRotation, size); }
	public FireDanmakuAction withCount(NumberProvider v) { return all(bulletType, color, v, speed, lifetime, angleOffset, spread, elevation, pattern, origin, aimMode, mover, outerCount, onExpiry, onTrail, trailInterval, tiltAngle, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock, damageType, groupRotation, size); }
	public FireDanmakuAction withSpeed(NumberProvider v) { return all(bulletType, color, count, v, lifetime, angleOffset, spread, elevation, pattern, origin, aimMode, mover, outerCount, onExpiry, onTrail, trailInterval, tiltAngle, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock, damageType, groupRotation, size); }
	public FireDanmakuAction withLifetime(NumberProvider v) { return all(bulletType, color, count, speed, v, angleOffset, spread, elevation, pattern, origin, aimMode, mover, outerCount, onExpiry, onTrail, trailInterval, tiltAngle, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock, damageType, groupRotation, size); }
	public FireDanmakuAction withAngleOffset(NumberProvider v) { return all(bulletType, color, count, speed, lifetime, v, spread, elevation, pattern, origin, aimMode, mover, outerCount, onExpiry, onTrail, trailInterval, tiltAngle, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock, damageType, groupRotation, size); }
	public FireDanmakuAction withSpread(NumberProvider v) { return all(bulletType, color, count, speed, lifetime, angleOffset, v, elevation, pattern, origin, aimMode, mover, outerCount, onExpiry, onTrail, trailInterval, tiltAngle, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock, damageType, groupRotation, size); }
	public FireDanmakuAction withElevation(NumberProvider v) { return all(bulletType, color, count, speed, lifetime, angleOffset, spread, v, pattern, origin, aimMode, mover, outerCount, onExpiry, onTrail, trailInterval, tiltAngle, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock, damageType, groupRotation, size); }
	public FireDanmakuAction withPattern(PatternType v) { return all(bulletType, color, count, speed, lifetime, angleOffset, spread, elevation, v, origin, aimMode, mover, outerCount, onExpiry, onTrail, trailInterval, tiltAngle, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock, damageType, groupRotation, size); }
	public FireDanmakuAction withOrigin(OriginConfig v) { return all(bulletType, color, count, speed, lifetime, angleOffset, spread, elevation, pattern, v, aimMode, mover, outerCount, onExpiry, onTrail, trailInterval, tiltAngle, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock, damageType, groupRotation, size); }
	public FireDanmakuAction withAimMode(AimMode v) { return all(bulletType, color, count, speed, lifetime, angleOffset, spread, elevation, pattern, origin, v, mover, outerCount, onExpiry, onTrail, trailInterval, tiltAngle, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock, damageType, groupRotation, size); }
	public FireDanmakuAction withMover(Optional<MoverConfig> v) { return all(bulletType, color, count, speed, lifetime, angleOffset, spread, elevation, pattern, origin, aimMode, v, outerCount, onExpiry, onTrail, trailInterval, tiltAngle, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock, damageType, groupRotation, size); }
	public FireDanmakuAction withOuterCount(Optional<NumberProvider> v) { return all(bulletType, color, count, speed, lifetime, angleOffset, spread, elevation, pattern, origin, aimMode, mover, v, onExpiry, onTrail, trailInterval, tiltAngle, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock, damageType, groupRotation, size); }
	public FireDanmakuAction withOnExpiry(Optional<List<SpellAction>> v) { return all(bulletType, color, count, speed, lifetime, angleOffset, spread, elevation, pattern, origin, aimMode, mover, outerCount, v, onTrail, trailInterval, tiltAngle, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock, damageType, groupRotation, size); }
	public FireDanmakuAction withOnTrail(Optional<List<SpellAction>> v) { return all(bulletType, color, count, speed, lifetime, angleOffset, spread, elevation, pattern, origin, aimMode, mover, outerCount, onExpiry, v, trailInterval, tiltAngle, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock, damageType, groupRotation, size); }
	public FireDanmakuAction withTrailInterval(int v) { return all(bulletType, color, count, speed, lifetime, angleOffset, spread, elevation, pattern, origin, aimMode, mover, outerCount, onExpiry, onTrail, v, tiltAngle, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock, damageType, groupRotation, size); }
	public FireDanmakuAction withTiltAngle(Optional<NumberProvider> v) { return all(bulletType, color, count, speed, lifetime, angleOffset, spread, elevation, pattern, origin, aimMode, mover, outerCount, onExpiry, onTrail, trailInterval, v, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock, damageType, groupRotation, size); }
	public FireDanmakuAction withOnHitEntity(Optional<List<SpellAction>> v) { return all(bulletType, color, count, speed, lifetime, angleOffset, spread, elevation, pattern, origin, aimMode, mover, outerCount, onExpiry, onTrail, trailInterval, tiltAngle, v, onHitBlock, hitBehaviorEntity, hitBehaviorBlock, damageType, groupRotation, size); }
	public FireDanmakuAction withOnHitBlock(Optional<List<SpellAction>> v) { return all(bulletType, color, count, speed, lifetime, angleOffset, spread, elevation, pattern, origin, aimMode, mover, outerCount, onExpiry, onTrail, trailInterval, tiltAngle, onHitEntity, v, hitBehaviorEntity, hitBehaviorBlock, damageType, groupRotation, size); }
	public FireDanmakuAction withHitBehaviorEntity(HitBehavior v) { return all(bulletType, color, count, speed, lifetime, angleOffset, spread, elevation, pattern, origin, aimMode, mover, outerCount, onExpiry, onTrail, trailInterval, tiltAngle, onHitEntity, onHitBlock, v, hitBehaviorBlock, damageType, groupRotation, size); }
	public FireDanmakuAction withHitBehaviorBlock(HitBehavior v) { return all(bulletType, color, count, speed, lifetime, angleOffset, spread, elevation, pattern, origin, aimMode, mover, outerCount, onExpiry, onTrail, trailInterval, tiltAngle, onHitEntity, onHitBlock, hitBehaviorEntity, v, damageType, groupRotation, size); }
	public FireDanmakuAction withDamageType(Optional<DanmakuDamageType> v) { return all(bulletType, color, count, speed, lifetime, angleOffset, spread, elevation, pattern, origin, aimMode, mover, outerCount, onExpiry, onTrail, trailInterval, tiltAngle, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock, v, groupRotation, size); }
	public FireDanmakuAction withGroupRotation(Optional<GroupRotation> v) { return all(bulletType, color, count, speed, lifetime, angleOffset, spread, elevation, pattern, origin, aimMode, mover, outerCount, onExpiry, onTrail, trailInterval, tiltAngle, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock, damageType, v, size); }
	public FireDanmakuAction withSize(NumberProvider v) { return all(bulletType, color, count, speed, lifetime, angleOffset, spread, elevation, pattern, origin, aimMode, mover, outerCount, onExpiry, onTrail, trailInterval, tiltAngle, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock, damageType, groupRotation, v); }

	@Override
	public void execute(SpellContext ctx) {
		CardHolder holder = ctx.holder();
		int life = (int) lifetime.get(ctx);
		Vec3 originPos = origin.resolve(ctx);
		var settings = new PatternEmitter.Settings(count, speed, angleOffset, spread, elevation, pattern,
				aimMode, origin.rotation(), outerCount, tiltAngle, groupRotation);
		PatternEmitter.emit(ctx, originPos, settings, (dir, baseDir) ->
				emitDanmaku(holder, ctx, life, dir, originPos, baseDir));
	}

	private void emitDanmaku(CardHolder holder, SpellContext ctx, int life, Vec3 dir, Vec3 originPos, Vec3 baseDir) {
		YHDanmaku.Bullet resolvedBullet = bulletType.get(ctx);
		DyeColor resolvedColor = color.get(ctx);
		var danmaku = holder.prepareDanmaku(life, dir, resolvedBullet, resolvedColor);
		NumberProvider scaleFunction = size instanceof NumberProviders.Constant ? null : size;
		danmaku.configureVisualScale((float) size.get(ctx), scaleFunction);
		danmaku.setPos(originPos);
		// Apply per-action damage type override
		if (damageType.isPresent()) {
			danmaku.damageTypeOverride = damageType.get();
		}
		if (mover.isPresent()) {
			// Pass baseDir, target, and caster positions so movers can use them in expressions.
			// When no target is available, targetPos = originPos (zero displacement for aim="target").
			Vec3 casterPos = holder.self() != null ? holder.self().position() : originPos;
			Vec3 targetPos = holder.target() != null ? holder.target() : originPos;
			danmaku.mover = mover.get().create(ctx, originPos, dir, baseDir, targetPos, casterPos);
		}
		if (onExpiry.isPresent()) {
			var expiryAction = new DataDrivenTrailAction(onExpiry.get(), ctx.runtime(), ctx.definition());
			expiryAction.setup(holder);
			danmaku.afterExpiry = expiryAction;
		}
		if (onTrail.isPresent()) {
			var trailAction = new DataDrivenTrailAction(onTrail.get(), ctx.runtime(), ctx.definition());
			trailAction.setup(holder);
			danmaku.onTrail = trailAction;
			danmaku.trailInterval = Math.max(1, trailInterval);
		}
		if (onHitEntity.isPresent()) {
			var hitAction = new DataDrivenTrailAction(onHitEntity.get(), ctx.runtime(), ctx.definition());
			hitAction.setup(holder);
			danmaku.onHitEntityAction = hitAction;
		}
		if (onHitBlock.isPresent()) {
			var hitAction = new DataDrivenTrailAction(onHitBlock.get(), ctx.runtime(), ctx.definition());
			hitAction.setup(holder);
			danmaku.onHitBlockAction = hitAction;
		}
		danmaku.hitBehaviorEntity = hitBehaviorEntity;
		danmaku.hitBehaviorBlock = hitBehaviorBlock;
		// Default from prepareDanmaku is bypassWall=true, bypassEntity=true (boss danmaku defaults).
		// Keep the legacy pass-through behavior unless this action explicitly uses block hit handling:
		// either a block-hit callback is configured, or block hits should stop/expire the danmaku.
		if (onHitBlock.isPresent() || hitBehaviorBlock != HitBehavior.CONTINUE) {
			danmaku.setBypassWall(false);
		}
		// Data-driven danmaku always enable entity collision detection.
		// Whether they keep flying or erase is decided in onHitEntity().
		danmaku.setBypassEntity(false);
		holder.shoot(danmaku);
	}

}
