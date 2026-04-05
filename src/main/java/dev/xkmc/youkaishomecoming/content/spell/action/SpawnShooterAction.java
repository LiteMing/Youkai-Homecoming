package dev.xkmc.youkaishomecoming.content.spell.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.content.spell.definition.*;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import dev.xkmc.youkaishomecoming.content.spell.shooter.ShooterData;
import net.minecraft.resources.ResourceLocation;
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
 *   "velocity": { "x": 0, "y": 0, "z": 0.5 },
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
		Optional<MoverConfig> mover,
		List<SpellAction> body
) implements SpellAction {

	public static final Codec<SpawnShooterAction> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("health", 40).forGetter(SpawnShooterAction::health),
			Codec.FLOAT.optionalFieldOf("damage", 4f).forGetter(SpawnShooterAction::damage),
			Codec.INT.optionalFieldOf("lifetime", 100).forGetter(SpawnShooterAction::lifetime),
			OriginConfig.CODEC.optionalFieldOf("origin", OriginConfig.caster()).forGetter(SpawnShooterAction::origin),
			NumberProvider.CODEC.optionalFieldOf("velocity_x", NumberProvider.constant(0)).forGetter(SpawnShooterAction::velocityX),
			NumberProvider.CODEC.optionalFieldOf("velocity_y", NumberProvider.constant(0)).forGetter(SpawnShooterAction::velocityY),
			NumberProvider.CODEC.optionalFieldOf("velocity_z", NumberProvider.constant(0)).forGetter(SpawnShooterAction::velocityZ),
			MoverConfig.CODEC.optionalFieldOf("mover").forGetter(SpawnShooterAction::mover),
			SpellAction.CODEC.listOf().fieldOf("body").forGetter(SpawnShooterAction::body)
	).apply(i, SpawnShooterAction::new));

	@Override
	public void execute(SpellContext ctx) {
		var holder = ctx.holder();
		Vec3 spawnPos = origin.resolve(ctx);
		Vec3 vel = new Vec3(velocityX.get(ctx), velocityY.get(ctx), velocityZ.get(ctx));

		// If velocity specifies only a speed scalar (x=0, y=0, z=speed),
		// derive direction toward target with angular spread from origin rotation.
		// Legacy pattern: dir = orient(toTarget).rotateDegrees(randX, randY), vel = dir * speed.
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

		var shooterSpell = new DataDrivenShooterSpell(body);
		var data = new ShooterData(health, damage, lifetime);
		var entity = holder.prepareShooter(data, shooterSpell);
		entity.setPos(spawnPos);

		if (vel.lengthSqr() > 1e-8) {
			entity.setDeltaMovement(vel);
		}

		if (mover.isPresent()) {
			entity.mover = mover.get().create(spawnPos, vel);
		}

		holder.shoot(entity);
	}
}
