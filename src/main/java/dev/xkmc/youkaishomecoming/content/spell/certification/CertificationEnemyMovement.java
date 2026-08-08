package dev.xkmc.youkaishomecoming.content.spell.certification;

import dev.xkmc.youkaishomecoming.content.entity.youkai.SpellCertificationEntity;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import net.minecraft.world.phys.Vec3;

/**
 * Server-authoritative bounded smooth random waypoint movement for the
 * certification enemy (design doc §9.1, D5).
 * <p>
 * Uses its own RandomSource seeded by the locked movementSeed: it never consumes
 * the entity's random source, so spell-cast randomness is not perturbed. The
 * trajectory is not published before ACTIVE; the seed and waypoint log stay in
 * the certification record for reproduction.
 */
public class CertificationEnemyMovement {

	private final CertificationArena arena;
	private final net.minecraft.util.RandomSource random;
	private Vec3 waypoint;
	private Vec3 velocity = Vec3.ZERO;
	private int waypointTimer;

	public CertificationEnemyMovement(CertificationArena arena, long movementSeed) {
		this.arena = arena;
		this.random = net.minecraft.util.RandomSource.create(movementSeed);
	}

	public void tick(SpellCertificationEntity entity) {
		if (!YHModConfig.COMMON.certificationEnemyRandomMovementEnabled.get()) {
			return;
		}
		double maxSpeed = YHModConfig.COMMON.certificationEnemyMaxSpeed.get();
		double acceleration = YHModConfig.COMMON.certificationEnemyAcceleration.get();
		if (waypoint == null || waypointTimer-- <= 0
				|| entity.position().distanceToSqr(waypoint) < 1.0) {
			pickWaypoint(entity);
		}
		Vec3 to = waypoint.subtract(entity.position());
		if (to.lengthSqr() < 1.0) {
			return;
		}
		Vec3 desired = to.normalize().scale(maxSpeed);
		velocity = velocity.scale(1 - acceleration).add(desired.scale(acceleration));
		if (velocity.lengthSqr() > maxSpeed * maxSpeed) {
			velocity = velocity.normalize().scale(maxSpeed);
		}
		entity.setPos(entity.position().add(velocity));
	}

	private void pickWaypoint(SpellCertificationEntity entity) {
		double margin = YHModConfig.COMMON.certificationEnemyBoundaryMargin.get();
		double minTravel = YHModConfig.COMMON.certificationEnemyMinimumTravelDistance.get();
		int minTicks = YHModConfig.COMMON.certificationEnemyWaypointMinTicks.get();
		int maxTicks = YHModConfig.COMMON.certificationEnemyWaypointMaxTicks.get();
		for (int attempt = 0; attempt < 8; attempt++) {
			Vec3 candidate = arena.randomPoint(random, margin);
			if (entity.position().distanceToSqr(candidate) >= minTravel * minTravel) {
				waypoint = candidate;
				waypointTimer = random.nextInt(minTicks, maxTicks + 1);
				return;
			}
		}
		waypoint = arena.randomPoint(random, margin);
		waypointTimer = random.nextInt(minTicks, maxTicks + 1);
	}
}
