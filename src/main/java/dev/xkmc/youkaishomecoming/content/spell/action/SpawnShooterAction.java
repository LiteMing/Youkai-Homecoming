package dev.xkmc.youkaishomecoming.content.spell.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.content.spell.definition.*;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import dev.xkmc.youkaishomecoming.content.spell.shooter.ShooterData;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * Spawns a ShooterEntity (mobile turret) that executes its own data-driven tick actions.
 * The shooter has health, lifetime, optional mover, and a nested list of SpellActions
 * that run every tick from the shooter's perspective (its center/forward).
 * <p>
 * JSON example:
 * <pre>
 * {
 *   "type": "spawn_shooter",
 *   "health": 40,
 *   "damage": 4,
 *   "lifetime": 100,
 *   "origin": { "mode": "caster" },
 *   "velocity_x": 0,
 *   "velocity_y": 0,
 *   "velocity_z": 0.5,
 *   "count": 32,
 *   "speed": 0.5,
 *   "pattern": "sphere",
 *   "mover": { ... },
 *   "body": [ ... ]
 * }
 * </pre>
 */
public record SpawnShooterAction(
		int health,
		float damage,
		int lifetime,
		OriginConfig origin,
		NumberProvider velocityX,
		NumberProvider velocityY,
		NumberProvider velocityZ,
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
		List<SpellAction> body
) implements SpellAction {

	private static final com.mojang.serialization.MapCodec<SpawnShooterAction> BASE_MAP = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.INT.optionalFieldOf("health", 40).forGetter(SpawnShooterAction::health),
			Codec.FLOAT.optionalFieldOf("damage", 4f).forGetter(SpawnShooterAction::damage),
			Codec.INT.optionalFieldOf("lifetime", 100).forGetter(SpawnShooterAction::lifetime),
			OriginConfig.CODEC.optionalFieldOf("origin", OriginConfig.caster()).forGetter(SpawnShooterAction::origin),
			NumberProvider.CODEC.optionalFieldOf("velocity_x", NumberProvider.constant(0)).forGetter(SpawnShooterAction::velocityX),
			NumberProvider.CODEC.optionalFieldOf("velocity_y", NumberProvider.constant(0)).forGetter(SpawnShooterAction::velocityY),
			NumberProvider.CODEC.optionalFieldOf("velocity_z", NumberProvider.constant(0)).forGetter(SpawnShooterAction::velocityZ),
			NumberProvider.CODEC.optionalFieldOf("count", NumberProvider.constant(1)).forGetter(SpawnShooterAction::count),
			NumberProvider.CODEC.optionalFieldOf("speed", NumberProvider.constant(0)).forGetter(SpawnShooterAction::speed),
			NumberProvider.CODEC.optionalFieldOf("angle_offset", NumberProvider.constant(0)).forGetter(SpawnShooterAction::angleOffset),
			NumberProvider.CODEC.optionalFieldOf("spread", NumberProvider.constant(360)).forGetter(SpawnShooterAction::spread),
			NumberProvider.CODEC.optionalFieldOf("elevation", NumberProvider.constant(0)).forGetter(SpawnShooterAction::elevation),
			PatternType.CODEC.optionalFieldOf("pattern", PatternType.AIMED).forGetter(SpawnShooterAction::pattern),
			AimMode.CODEC.optionalFieldOf("aim_mode", new AimMode.AimModes.Target()).forGetter(SpawnShooterAction::aimMode),
			NumberProvider.CODEC.optionalFieldOf("outer_count").forGetter(SpawnShooterAction::outerCount),
			NumberProvider.CODEC.optionalFieldOf("tilt_angle").forGetter(SpawnShooterAction::tiltAngle)
	).apply(i, SpawnShooterAction::new));

	public static final Codec<SpawnShooterAction> CODEC = RecordCodecBuilder.create(i -> i.group(
			BASE_MAP.forGetter(ssa -> ssa),
			GroupRotation.CODEC.optionalFieldOf("group_rotation").forGetter(SpawnShooterAction::groupRotation),
			MoverConfig.CODEC.optionalFieldOf("mover").forGetter(SpawnShooterAction::mover),
			Codec.STRING.optionalFieldOf("ysm_model", "").forGetter(SpawnShooterAction::ysmModel),
			Codec.STRING.optionalFieldOf("ysm_texture", "").forGetter(SpawnShooterAction::ysmTexture),
			Codec.STRING.optionalFieldOf("ysm_animation", "").forGetter(SpawnShooterAction::ysmAnimation),
			Codec.INT.optionalFieldOf("ysm_duration", 0).forGetter(SpawnShooterAction::ysmDuration),
			Codec.STRING.optionalFieldOf("ysm_clear_target", "changed").forGetter(SpawnShooterAction::ysmClearTarget),
			SpellAction.CODEC.listOf().fieldOf("body").forGetter(SpawnShooterAction::body)
	).apply(i, (base, groupRotation, mover, ysmModel, ysmTexture, ysmAnimation, ysmDuration, ysmClearTarget, body) -> new SpawnShooterAction(
			base.health, base.damage, base.lifetime, base.origin,
			base.velocityX, base.velocityY, base.velocityZ,
			base.count, base.speed, base.angleOffset, base.spread, base.elevation,
			base.pattern, base.aimMode, base.outerCount, base.tiltAngle, groupRotation, mover,
			ysmModel, ysmTexture, ysmAnimation, ysmDuration, ysmClearTarget, body
	)));

	public SpawnShooterAction {
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
			NumberProvider velocityX,
			NumberProvider velocityY,
			NumberProvider velocityZ,
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
		this(health, damage, lifetime, origin, velocityX, velocityY, velocityZ,
				count, speed, angleOffset, spread, elevation, pattern, aimMode,
				outerCount, tiltAngle, Optional.empty(), Optional.empty(),
				"", "", "", 0, "changed", List.of());
	}

	public SpawnShooterAction(
			int health,
			float damage,
			int lifetime,
			OriginConfig origin,
			NumberProvider velocityX,
			NumberProvider velocityY,
			NumberProvider velocityZ,
			Optional<MoverConfig> mover,
			List<SpellAction> body
	) {
		this(health, damage, lifetime, origin, velocityX, velocityY, velocityZ,
				NumberProvider.constant(1), NumberProvider.constant(0),
				NumberProvider.constant(0), NumberProvider.constant(360), NumberProvider.constant(0),
				PatternType.AIMED, new AimMode.AimModes.Target(),
				Optional.empty(), Optional.empty(), Optional.empty(), mover,
				"", "", "", 0, "changed", body);
	}

	public SpawnShooterAction(
			int health,
			float damage,
			int lifetime,
			OriginConfig origin,
			NumberProvider velocityX,
			NumberProvider velocityY,
			NumberProvider velocityZ,
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
		this(health, damage, lifetime, origin, velocityX, velocityY, velocityZ,
				count, speed, angleOffset, spread, elevation, pattern, aimMode,
				outerCount, tiltAngle, groupRotation, mover,
				"", "", "", 0, "changed", body);
	}

	public SpawnShooterAction withBody(List<SpellAction> body) {
		return new SpawnShooterAction(health, damage, lifetime, origin, velocityX, velocityY, velocityZ,
				count, speed, angleOffset, spread, elevation, pattern, aimMode,
				outerCount, tiltAngle, groupRotation, mover,
				ysmModel, ysmTexture, ysmAnimation, ysmDuration, ysmClearTarget, body);
	}

	public SpawnShooterAction withHealth(int v) {
		return new SpawnShooterAction(v, damage, lifetime, origin, velocityX, velocityY, velocityZ,
				count, speed, angleOffset, spread, elevation, pattern, aimMode,
				outerCount, tiltAngle, groupRotation, mover,
				ysmModel, ysmTexture, ysmAnimation, ysmDuration, ysmClearTarget, body);
	}

	public SpawnShooterAction withDamage(float v) {
		return new SpawnShooterAction(health, v, lifetime, origin, velocityX, velocityY, velocityZ,
				count, speed, angleOffset, spread, elevation, pattern, aimMode,
				outerCount, tiltAngle, groupRotation, mover,
				ysmModel, ysmTexture, ysmAnimation, ysmDuration, ysmClearTarget, body);
	}

	public SpawnShooterAction withLifetime(int v) {
		return new SpawnShooterAction(health, damage, v, origin, velocityX, velocityY, velocityZ,
				count, speed, angleOffset, spread, elevation, pattern, aimMode,
				outerCount, tiltAngle, groupRotation, mover,
				ysmModel, ysmTexture, ysmAnimation, ysmDuration, ysmClearTarget, body);
	}

	public SpawnShooterAction withOrigin(OriginConfig v) {
		return new SpawnShooterAction(health, damage, lifetime, v, velocityX, velocityY, velocityZ,
				count, speed, angleOffset, spread, elevation, pattern, aimMode,
				outerCount, tiltAngle, groupRotation, mover,
				ysmModel, ysmTexture, ysmAnimation, ysmDuration, ysmClearTarget, body);
	}

	public SpawnShooterAction withVelocityX(NumberProvider v) {
		return new SpawnShooterAction(health, damage, lifetime, origin, v, velocityY, velocityZ,
				count, speed, angleOffset, spread, elevation, pattern, aimMode,
				outerCount, tiltAngle, groupRotation, mover,
				ysmModel, ysmTexture, ysmAnimation, ysmDuration, ysmClearTarget, body);
	}

	public SpawnShooterAction withVelocityY(NumberProvider v) {
		return new SpawnShooterAction(health, damage, lifetime, origin, velocityX, v, velocityZ,
				count, speed, angleOffset, spread, elevation, pattern, aimMode,
				outerCount, tiltAngle, groupRotation, mover,
				ysmModel, ysmTexture, ysmAnimation, ysmDuration, ysmClearTarget, body);
	}

	public SpawnShooterAction withVelocityZ(NumberProvider v) {
		return new SpawnShooterAction(health, damage, lifetime, origin, velocityX, velocityY, v,
				count, speed, angleOffset, spread, elevation, pattern, aimMode,
				outerCount, tiltAngle, groupRotation, mover,
				ysmModel, ysmTexture, ysmAnimation, ysmDuration, ysmClearTarget, body);
	}

	public SpawnShooterAction withCount(NumberProvider v) {
		return new SpawnShooterAction(health, damage, lifetime, origin, velocityX, velocityY, velocityZ,
				v, speed, angleOffset, spread, elevation, pattern, aimMode,
				outerCount, tiltAngle, groupRotation, mover,
				ysmModel, ysmTexture, ysmAnimation, ysmDuration, ysmClearTarget, body);
	}

	public SpawnShooterAction withSpeed(NumberProvider v) {
		return new SpawnShooterAction(health, damage, lifetime, origin, velocityX, velocityY, velocityZ,
				count, v, angleOffset, spread, elevation, pattern, aimMode,
				outerCount, tiltAngle, groupRotation, mover,
				ysmModel, ysmTexture, ysmAnimation, ysmDuration, ysmClearTarget, body);
	}

	public SpawnShooterAction withAngleOffset(NumberProvider v) {
		return new SpawnShooterAction(health, damage, lifetime, origin, velocityX, velocityY, velocityZ,
				count, speed, v, spread, elevation, pattern, aimMode,
				outerCount, tiltAngle, groupRotation, mover,
				ysmModel, ysmTexture, ysmAnimation, ysmDuration, ysmClearTarget, body);
	}

	public SpawnShooterAction withSpread(NumberProvider v) {
		return new SpawnShooterAction(health, damage, lifetime, origin, velocityX, velocityY, velocityZ,
				count, speed, angleOffset, v, elevation, pattern, aimMode,
				outerCount, tiltAngle, groupRotation, mover,
				ysmModel, ysmTexture, ysmAnimation, ysmDuration, ysmClearTarget, body);
	}

	public SpawnShooterAction withElevation(NumberProvider v) {
		return new SpawnShooterAction(health, damage, lifetime, origin, velocityX, velocityY, velocityZ,
				count, speed, angleOffset, spread, v, pattern, aimMode,
				outerCount, tiltAngle, groupRotation, mover,
				ysmModel, ysmTexture, ysmAnimation, ysmDuration, ysmClearTarget, body);
	}

	public SpawnShooterAction withPattern(PatternType v) {
		return new SpawnShooterAction(health, damage, lifetime, origin, velocityX, velocityY, velocityZ,
				count, speed, angleOffset, spread, elevation, v, aimMode,
				outerCount, tiltAngle, groupRotation, mover,
				ysmModel, ysmTexture, ysmAnimation, ysmDuration, ysmClearTarget, body);
	}

	public SpawnShooterAction withAimMode(AimMode v) {
		return new SpawnShooterAction(health, damage, lifetime, origin, velocityX, velocityY, velocityZ,
				count, speed, angleOffset, spread, elevation, pattern, v,
				outerCount, tiltAngle, groupRotation, mover,
				ysmModel, ysmTexture, ysmAnimation, ysmDuration, ysmClearTarget, body);
	}

	public SpawnShooterAction withOuterCount(Optional<NumberProvider> v) {
		return new SpawnShooterAction(health, damage, lifetime, origin, velocityX, velocityY, velocityZ,
				count, speed, angleOffset, spread, elevation, pattern, aimMode,
				v, tiltAngle, groupRotation, mover,
				ysmModel, ysmTexture, ysmAnimation, ysmDuration, ysmClearTarget, body);
	}

	public SpawnShooterAction withTiltAngle(Optional<NumberProvider> v) {
		return new SpawnShooterAction(health, damage, lifetime, origin, velocityX, velocityY, velocityZ,
				count, speed, angleOffset, spread, elevation, pattern, aimMode,
				outerCount, v, groupRotation, mover,
				ysmModel, ysmTexture, ysmAnimation, ysmDuration, ysmClearTarget, body);
	}

	public SpawnShooterAction withGroupRotation(Optional<GroupRotation> v) {
		return new SpawnShooterAction(health, damage, lifetime, origin, velocityX, velocityY, velocityZ,
				count, speed, angleOffset, spread, elevation, pattern, aimMode,
				outerCount, tiltAngle, v, mover,
				ysmModel, ysmTexture, ysmAnimation, ysmDuration, ysmClearTarget, body);
	}

	public SpawnShooterAction withMover(Optional<MoverConfig> v) {
		return new SpawnShooterAction(health, damage, lifetime, origin, velocityX, velocityY, velocityZ,
				count, speed, angleOffset, spread, elevation, pattern, aimMode,
				outerCount, tiltAngle, groupRotation, v,
				ysmModel, ysmTexture, ysmAnimation, ysmDuration, ysmClearTarget, body);
	}

	public SpawnShooterAction withYsmModel(String v) {
		return new SpawnShooterAction(health, damage, lifetime, origin, velocityX, velocityY, velocityZ,
				count, speed, angleOffset, spread, elevation, pattern, aimMode,
				outerCount, tiltAngle, groupRotation, mover,
				v, ysmTexture, ysmAnimation, ysmDuration, ysmClearTarget, body);
	}

	public SpawnShooterAction withYsmTexture(String v) {
		return new SpawnShooterAction(health, damage, lifetime, origin, velocityX, velocityY, velocityZ,
				count, speed, angleOffset, spread, elevation, pattern, aimMode,
				outerCount, tiltAngle, groupRotation, mover,
				ysmModel, v, ysmAnimation, ysmDuration, ysmClearTarget, body);
	}

	public SpawnShooterAction withYsmAnimation(String v) {
		return new SpawnShooterAction(health, damage, lifetime, origin, velocityX, velocityY, velocityZ,
				count, speed, angleOffset, spread, elevation, pattern, aimMode,
				outerCount, tiltAngle, groupRotation, mover,
				ysmModel, ysmTexture, v, ysmDuration, ysmClearTarget, body);
	}

	public SpawnShooterAction withYsmDuration(int v) {
		return new SpawnShooterAction(health, damage, lifetime, origin, velocityX, velocityY, velocityZ,
				count, speed, angleOffset, spread, elevation, pattern, aimMode,
				outerCount, tiltAngle, groupRotation, mover,
				ysmModel, ysmTexture, ysmAnimation, v, ysmClearTarget, body);
	}

	public SpawnShooterAction withYsmClearTarget(String v) {
		return new SpawnShooterAction(health, damage, lifetime, origin, velocityX, velocityY, velocityZ,
				count, speed, angleOffset, spread, elevation, pattern, aimMode,
				outerCount, tiltAngle, groupRotation, mover,
				ysmModel, ysmTexture, ysmAnimation, ysmDuration, v, body);
	}

	@Override
	public void execute(SpellContext ctx) {
		var holder = ctx.holder();
		Vec3 spawnPos = origin.resolve(ctx);
		Vec3 legacyVel = new Vec3(velocityX.get(ctx), velocityY.get(ctx), velocityZ.get(ctx));
		if (usesPatternEmitter()) {
			Vec3 patternSpawnPos = spawnPos;
			var settings = new PatternEmitter.Settings(count, speed, angleOffset, spread, elevation, pattern,
					aimMode, origin.rotation(), outerCount, tiltAngle, groupRotation);
			PatternEmitter.emit(ctx, patternSpawnPos, settings, (vel, baseDir) ->
					spawnOne(ctx, patternSpawnPos, vel.add(legacyVel), baseDir));
			return;
		}

		// If velocity specifies only a speed scalar (x=0, y=0, z=speed),
		// derive direction toward target with angular spread from origin rotation.
		// Legacy pattern: dir = orient(toTarget).rotateDegrees(randX, randY), vel = dir * speed.
		Vec3 vel = legacyVel;
		if (vel.x == 0 && vel.y == 0 && vel.z != 0) {
			double speed = vel.z;
			Vec3 targetPos = holder.target();
			Vec3 casterPos = holder.center();
			Vec3 baseDir = null;
			if (targetPos != null) {
				Vec3 toTarget = targetPos.subtract(casterPos);
				if (toTarget.lengthSqr() > 1e-4) baseDir = toTarget.normalize();
			}
			if (baseDir == null) baseDir = holder.forward();
			// Apply angular spread from origin's rotation and offsets
			double yawSpread = origin.rotation().get(ctx);
			double pitchSpread = origin.offsetY().get(ctx);
			if (yawSpread != 0 || pitchSpread != 0) {
				var ori = dev.xkmc.youkaishomecoming.content.entity.danmaku.DanmakuHelper.getOrientation(baseDir);
				baseDir = ori.rotateDegrees(yawSpread, pitchSpread);
			}
			vel = baseDir.scale(speed);
			spawnPos = casterPos.add(baseDir.scale(2)); // spawn 2 blocks along direction
		}
		spawnOne(ctx, spawnPos, vel, vel.lengthSqr() > 1e-8 ? vel.normalize() : holder.forward());
	}

	private boolean usesPatternEmitter() {
		if (pattern != PatternType.AIMED) return true;
		if (outerCount.isPresent() || tiltAngle.isPresent() || groupRotation.isPresent()) return true;
		return !isConstant(count, 1) || !isConstant(speed, 0);
	}

	private static boolean isConstant(NumberProvider provider, double value) {
		return provider instanceof NumberProviders.Constant c && Math.abs(c.value() - value) < 1e-8;
	}

	private void spawnOne(SpellContext ctx, Vec3 spawnPos, Vec3 vel, Vec3 baseDir) {
		var holder = ctx.holder();
		var shooterSpell = new DataDrivenShooterSpell(body);
		var data = new ShooterData(health, damage, lifetime);
		var entity = holder.prepareShooter(data, shooterSpell);
		entity.inheritDamageFrom(holder);
		entity.setPos(spawnPos);

		if (vel.lengthSqr() > 1e-8) {
			entity.setDeltaMovement(vel);
		}

		if (mover.isPresent()) {
			Vec3 casterPos = holder.self() != null ? holder.self().position() : spawnPos;
			Vec3 targetPos = holder.target() != null ? holder.target() : spawnPos;
			entity.mover = mover.get().create(spawnPos, vel, baseDir, targetPos, casterPos);
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
