package dev.xkmc.youkaishomecoming.content.spell.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.content.spell.definition.AimMode;
import dev.xkmc.youkaishomecoming.content.spell.definition.GroupRotation;
import dev.xkmc.youkaishomecoming.content.spell.definition.MoverConfig;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberProvider;
import dev.xkmc.youkaishomecoming.content.spell.definition.OriginConfig;
import dev.xkmc.youkaishomecoming.content.spell.definition.PatternType;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import dev.xkmc.youkaishomecoming.content.spell.shooter.ShooterData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Spawns ShooterEntity instances with the same pattern emitter model used by danmaku actions.
 * Each shooter executes its own data-driven tick actions from its current position.
 */
public record SpawnShooterAction(
		int health,
		float damage,
		int lifetime,
		ResourceLocation circle,
		OriginConfig origin,
		NumberProvider count,
		NumberProvider speed,
		NumberProvider angleOffset,
		NumberProvider spread,
		NumberProvider elevation,
		PatternType pattern,
		AimMode aimMode,
		Optional<NumberProvider> outerCount,
		Optional<NumberProvider> tiltAngle,
		Optional<GroupRotation> groupRotation,
		Optional<MoverConfig> mover,
		String ysmModel,
		String ysmTexture,
		String ysmAnimation,
		int ysmDuration,
		String ysmClearTarget,
		boolean targetable,
		List<SpellAction> body
) implements SpellAction {

	private static final com.mojang.serialization.MapCodec<SpawnShooterAction> BASE_MAP = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.INT.optionalFieldOf("health", 40).forGetter(SpawnShooterAction::health),
			Codec.FLOAT.optionalFieldOf("damage", 4f).forGetter(SpawnShooterAction::damage),
			Codec.INT.optionalFieldOf("lifetime", 100).forGetter(SpawnShooterAction::lifetime),
			OriginConfig.CODEC.optionalFieldOf("origin", OriginConfig.caster()).forGetter(SpawnShooterAction::origin),
			NumberProvider.CODEC.fieldOf("count").forGetter(SpawnShooterAction::count),
			NumberProvider.CODEC.fieldOf("speed").forGetter(SpawnShooterAction::speed),
			NumberProvider.CODEC.optionalFieldOf("angle_offset", NumberProvider.constant(0)).forGetter(SpawnShooterAction::angleOffset),
			NumberProvider.CODEC.optionalFieldOf("spread", NumberProvider.constant(360)).forGetter(SpawnShooterAction::spread),
			NumberProvider.CODEC.optionalFieldOf("elevation", NumberProvider.constant(0)).forGetter(SpawnShooterAction::elevation),
			PatternType.CODEC.optionalFieldOf("pattern", PatternType.RING).forGetter(SpawnShooterAction::pattern),
			AimMode.CODEC.optionalFieldOf("aim_mode", new AimMode.AimModes.Target()).forGetter(SpawnShooterAction::aimMode),
			NumberProvider.CODEC.optionalFieldOf("outer_count").forGetter(SpawnShooterAction::outerCount),
			NumberProvider.CODEC.optionalFieldOf("tilt_angle").forGetter(SpawnShooterAction::tiltAngle)
	).apply(i, SpawnShooterAction::new));

	public static final Codec<SpawnShooterAction> CODEC = RecordCodecBuilder.create(i -> i.group(
			BASE_MAP.forGetter(ssa -> ssa),
			ResourceLocation.CODEC.optionalFieldOf("circle", ShooterData.DEFAULT_CIRCLE).forGetter(SpawnShooterAction::circle),
			GroupRotation.CODEC.optionalFieldOf("group_rotation").forGetter(SpawnShooterAction::groupRotation),
			MoverConfig.CODEC.optionalFieldOf("mover").forGetter(SpawnShooterAction::mover),
			Codec.STRING.optionalFieldOf("ysm_model", "").forGetter(SpawnShooterAction::ysmModel),
			Codec.STRING.optionalFieldOf("ysm_texture", "").forGetter(SpawnShooterAction::ysmTexture),
			Codec.STRING.optionalFieldOf("ysm_animation", "").forGetter(SpawnShooterAction::ysmAnimation),
			Codec.INT.optionalFieldOf("ysm_duration", 0).forGetter(SpawnShooterAction::ysmDuration),
			Codec.STRING.optionalFieldOf("ysm_clear_target", "changed").forGetter(SpawnShooterAction::ysmClearTarget),
			Codec.BOOL.optionalFieldOf("targetable", true).forGetter(SpawnShooterAction::targetable),
			SpellAction.CODEC.listOf().fieldOf("body").forGetter(SpawnShooterAction::body)
	).apply(i, (base, circle, groupRotation, mover, ysmModel, ysmTexture, ysmAnimation, ysmDuration, ysmClearTarget, targetable, body) -> new SpawnShooterAction(
			base.health, base.damage, base.lifetime, circle, base.origin,
			base.count, base.speed, base.angleOffset, base.spread, base.elevation,
			base.pattern, base.aimMode, base.outerCount, base.tiltAngle, groupRotation, mover,
			ysmModel, ysmTexture, ysmAnimation, ysmDuration, ysmClearTarget, targetable, body
	)));

	public SpawnShooterAction {
		if (circle == null) {
			circle = ShooterData.DEFAULT_CIRCLE;
		}
		ysmModel = normalize(ysmModel);
		ysmTexture = normalize(ysmTexture);
		ysmAnimation = normalize(ysmAnimation);
		ysmClearTarget = normalize(ysmClearTarget);
		body = List.copyOf(body);
	}

	public SpawnShooterAction(
			int health,
			float damage,
			int lifetime,
			OriginConfig origin,
			NumberProvider count,
			NumberProvider speed,
			NumberProvider angleOffset,
			NumberProvider spread,
			NumberProvider elevation,
			PatternType pattern,
			AimMode aimMode,
			Optional<NumberProvider> outerCount,
			Optional<NumberProvider> tiltAngle
	) {
		this(health, damage, lifetime, ShooterData.DEFAULT_CIRCLE, origin, count, speed, angleOffset, spread,
				elevation, pattern, aimMode, outerCount, tiltAngle, Optional.empty(), Optional.empty(),
				"", "", "", 0, "changed", true, List.of());
	}

	public SpawnShooterAction(
			int health,
			float damage,
			int lifetime,
			OriginConfig origin,
			NumberProvider count,
			NumberProvider speed,
			NumberProvider angleOffset,
			NumberProvider spread,
			NumberProvider elevation,
			PatternType pattern,
			AimMode aimMode,
			Optional<NumberProvider> outerCount,
			Optional<NumberProvider> tiltAngle,
			Optional<GroupRotation> groupRotation,
			Optional<MoverConfig> mover,
			List<SpellAction> body
	) {
		this(health, damage, lifetime, ShooterData.DEFAULT_CIRCLE, origin, count, speed, angleOffset, spread,
				elevation, pattern, aimMode, outerCount, tiltAngle, groupRotation, mover,
				"", "", "", 0, "changed", true, body);
	}

	public SpawnShooterAction withTargetable(boolean v) {
		return all(health, damage, lifetime, circle, origin, count, speed, angleOffset, spread, elevation,
				pattern, aimMode, outerCount, tiltAngle, groupRotation, mover,
				ysmModel, ysmTexture, ysmAnimation, ysmDuration, ysmClearTarget, v, body);
	}

	public SpawnShooterAction withBody(List<SpellAction> body) {
		return all(health, damage, lifetime, circle, origin, count, speed, angleOffset, spread, elevation,
				pattern, aimMode, outerCount, tiltAngle, groupRotation, mover,
				ysmModel, ysmTexture, ysmAnimation, ysmDuration, ysmClearTarget, targetable, body);
	}

	public SpawnShooterAction withHealth(int v) {
		return all(v, damage, lifetime, circle, origin, count, speed, angleOffset, spread, elevation,
				pattern, aimMode, outerCount, tiltAngle, groupRotation, mover,
				ysmModel, ysmTexture, ysmAnimation, ysmDuration, ysmClearTarget, targetable, body);
	}

	public SpawnShooterAction withDamage(float v) {
		return all(health, v, lifetime, circle, origin, count, speed, angleOffset, spread, elevation,
				pattern, aimMode, outerCount, tiltAngle, groupRotation, mover,
				ysmModel, ysmTexture, ysmAnimation, ysmDuration, ysmClearTarget, targetable, body);
	}

	public SpawnShooterAction withLifetime(int v) {
		return all(health, damage, v, circle, origin, count, speed, angleOffset, spread, elevation,
				pattern, aimMode, outerCount, tiltAngle, groupRotation, mover,
				ysmModel, ysmTexture, ysmAnimation, ysmDuration, ysmClearTarget, targetable, body);
	}

	public SpawnShooterAction withCircle(ResourceLocation v) {
		return all(health, damage, lifetime, v, origin, count, speed, angleOffset, spread, elevation,
				pattern, aimMode, outerCount, tiltAngle, groupRotation, mover,
				ysmModel, ysmTexture, ysmAnimation, ysmDuration, ysmClearTarget, targetable, body);
	}

	public SpawnShooterAction withOrigin(OriginConfig v) {
		return all(health, damage, lifetime, circle, v, count, speed, angleOffset, spread, elevation,
				pattern, aimMode, outerCount, tiltAngle, groupRotation, mover,
				ysmModel, ysmTexture, ysmAnimation, ysmDuration, ysmClearTarget, targetable, body);
	}

	public SpawnShooterAction withCount(NumberProvider v) {
		return all(health, damage, lifetime, circle, origin, v, speed, angleOffset, spread, elevation,
				pattern, aimMode, outerCount, tiltAngle, groupRotation, mover,
				ysmModel, ysmTexture, ysmAnimation, ysmDuration, ysmClearTarget, targetable, body);
	}

	public SpawnShooterAction withSpeed(NumberProvider v) {
		return all(health, damage, lifetime, circle, origin, count, v, angleOffset, spread, elevation,
				pattern, aimMode, outerCount, tiltAngle, groupRotation, mover,
				ysmModel, ysmTexture, ysmAnimation, ysmDuration, ysmClearTarget, targetable, body);
	}

	public SpawnShooterAction withAngleOffset(NumberProvider v) {
		return all(health, damage, lifetime, circle, origin, count, speed, v, spread, elevation,
				pattern, aimMode, outerCount, tiltAngle, groupRotation, mover,
				ysmModel, ysmTexture, ysmAnimation, ysmDuration, ysmClearTarget, targetable, body);
	}

	public SpawnShooterAction withSpread(NumberProvider v) {
		return all(health, damage, lifetime, circle, origin, count, speed, angleOffset, v, elevation,
				pattern, aimMode, outerCount, tiltAngle, groupRotation, mover,
				ysmModel, ysmTexture, ysmAnimation, ysmDuration, ysmClearTarget, targetable, body);
	}

	public SpawnShooterAction withElevation(NumberProvider v) {
		return all(health, damage, lifetime, circle, origin, count, speed, angleOffset, spread, v,
				pattern, aimMode, outerCount, tiltAngle, groupRotation, mover,
				ysmModel, ysmTexture, ysmAnimation, ysmDuration, ysmClearTarget, targetable, body);
	}

	public SpawnShooterAction withPattern(PatternType v) {
		return all(health, damage, lifetime, circle, origin, count, speed, angleOffset, spread, elevation,
				v, aimMode, outerCount, tiltAngle, groupRotation, mover,
				ysmModel, ysmTexture, ysmAnimation, ysmDuration, ysmClearTarget, targetable, body);
	}

	public SpawnShooterAction withAimMode(AimMode v) {
		return all(health, damage, lifetime, circle, origin, count, speed, angleOffset, spread, elevation,
				pattern, v, outerCount, tiltAngle, groupRotation, mover,
				ysmModel, ysmTexture, ysmAnimation, ysmDuration, ysmClearTarget, targetable, body);
	}

	public SpawnShooterAction withOuterCount(Optional<NumberProvider> v) {
		return all(health, damage, lifetime, circle, origin, count, speed, angleOffset, spread, elevation,
				pattern, aimMode, v, tiltAngle, groupRotation, mover,
				ysmModel, ysmTexture, ysmAnimation, ysmDuration, ysmClearTarget, targetable, body);
	}

	public SpawnShooterAction withTiltAngle(Optional<NumberProvider> v) {
		return all(health, damage, lifetime, circle, origin, count, speed, angleOffset, spread, elevation,
				pattern, aimMode, outerCount, v, groupRotation, mover,
				ysmModel, ysmTexture, ysmAnimation, ysmDuration, ysmClearTarget, targetable, body);
	}

	public SpawnShooterAction withGroupRotation(Optional<GroupRotation> v) {
		return all(health, damage, lifetime, circle, origin, count, speed, angleOffset, spread, elevation,
				pattern, aimMode, outerCount, tiltAngle, v, mover,
				ysmModel, ysmTexture, ysmAnimation, ysmDuration, ysmClearTarget, targetable, body);
	}

	public SpawnShooterAction withMover(Optional<MoverConfig> v) {
		return all(health, damage, lifetime, circle, origin, count, speed, angleOffset, spread, elevation,
				pattern, aimMode, outerCount, tiltAngle, groupRotation, v,
				ysmModel, ysmTexture, ysmAnimation, ysmDuration, ysmClearTarget, targetable, body);
	}

	public SpawnShooterAction withYsmModel(String v) {
		return all(health, damage, lifetime, circle, origin, count, speed, angleOffset, spread, elevation,
				pattern, aimMode, outerCount, tiltAngle, groupRotation, mover,
				v, ysmTexture, ysmAnimation, ysmDuration, ysmClearTarget, targetable, body);
	}

	public SpawnShooterAction withYsmTexture(String v) {
		return all(health, damage, lifetime, circle, origin, count, speed, angleOffset, spread, elevation,
				pattern, aimMode, outerCount, tiltAngle, groupRotation, mover,
				ysmModel, v, ysmAnimation, ysmDuration, ysmClearTarget, targetable, body);
	}

	public SpawnShooterAction withYsmAnimation(String v) {
		return all(health, damage, lifetime, circle, origin, count, speed, angleOffset, spread, elevation,
				pattern, aimMode, outerCount, tiltAngle, groupRotation, mover,
				ysmModel, ysmTexture, v, ysmDuration, ysmClearTarget, targetable, body);
	}

	public SpawnShooterAction withYsmDuration(int v) {
		return all(health, damage, lifetime, circle, origin, count, speed, angleOffset, spread, elevation,
				pattern, aimMode, outerCount, tiltAngle, groupRotation, mover,
				ysmModel, ysmTexture, ysmAnimation, v, ysmClearTarget, targetable, body);
	}

	public SpawnShooterAction withYsmClearTarget(String v) {
		return all(health, damage, lifetime, circle, origin, count, speed, angleOffset, spread, elevation,
				pattern, aimMode, outerCount, tiltAngle, groupRotation, mover,
				ysmModel, ysmTexture, ysmAnimation, ysmDuration, v, targetable, body);
	}

	private SpawnShooterAction all(
			int health,
			float damage,
			int lifetime,
			ResourceLocation circle,
			OriginConfig origin,
			NumberProvider count,
			NumberProvider speed,
			NumberProvider angleOffset,
			NumberProvider spread,
			NumberProvider elevation,
			PatternType pattern,
			AimMode aimMode,
			Optional<NumberProvider> outerCount,
			Optional<NumberProvider> tiltAngle,
			Optional<GroupRotation> groupRotation,
			Optional<MoverConfig> mover,
			String ysmModel,
			String ysmTexture,
			String ysmAnimation,
			int ysmDuration,
			String ysmClearTarget,
			boolean targetable,
			List<SpellAction> body
	) {
		return new SpawnShooterAction(health, damage, lifetime, circle, origin, count, speed, angleOffset,
				spread, elevation, pattern, aimMode, outerCount, tiltAngle, groupRotation, mover,
				ysmModel, ysmTexture, ysmAnimation, ysmDuration, ysmClearTarget, targetable, body);
	}

	@Override
	public void execute(SpellContext ctx) {
		Vec3 spawnPos = origin.resolve(ctx);
		var settings = new PatternEmitter.Settings(count, speed, angleOffset, spread, elevation, pattern,
				aimMode, origin.rotation(), outerCount, tiltAngle, groupRotation);
		PatternEmitter.emit(ctx, spawnPos, settings, (vel, baseDir, spawnIndex, resolvedSpread) ->
				spawnOne(ctx, spawnPos, vel, baseDir));
	}

	private void spawnOne(SpellContext ctx, Vec3 spawnPos, Vec3 vel, Vec3 baseDir) {
		var holder = ctx.holder();
		var shooterSpell = new DataDrivenShooterSpell(body, ctx.runtime());
		var data = new ShooterData(health, damage, Math.max(1, lifetime), circle, targetable);
		var entity = holder.prepareShooter(data, shooterSpell);
		entity.inheritDamageFrom(holder);
		entity.setPos(spawnPos);

		if (vel.lengthSqr() > 1e-8) {
			entity.setDeltaMovement(vel);
		}

		if (mover.isPresent()) {
			Vec3 casterPos = holder.self() != null ? holder.self().position() : spawnPos;
			MoverConfig moverConfig = mover.get();
			Vec3 targetPos = moverConfig.resolveTargetPos(ctx, spawnPos);
			entity.mover = moverConfig.create(ctx, spawnPos, vel, baseDir, targetPos, casterPos);
		}

		if (hasInitialYsmOverride()) {
			entity.setYsmRenderOverride(ysmModel, ysmTexture, ysmAnimation, ysmDuration, ysmClearTarget);
		}

		holder.shoot(entity);
	}

	private boolean hasInitialYsmOverride() {
		return !ysmModel.isBlank() || !ysmTexture.isBlank() || !ysmAnimation.isBlank();
	}

	private static String normalize(String value) {
		return value == null ? "" : value.trim();
	}
}
