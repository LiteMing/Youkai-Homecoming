package dev.xkmc.youkaishomecoming.content.spell.pilot;

import dev.xkmc.fastprojectileapi.entity.SimplifiedProjectile;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.IYHDanmaku;
import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import dev.xkmc.youkaishomecoming.content.spell.pilot.predict.BallisticProvider;
import dev.xkmc.youkaishomecoming.content.spell.pilot.predict.MoverExactProvider;
import dev.xkmc.youkaishomecoming.content.spell.pilot.predict.ObservedMotionProvider;
import dev.xkmc.youkaishomecoming.content.spell.pilot.predict.ThreatProviderRegistry;
import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.LevelCollisionOracle;
import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.SelfBoxModel;
import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.ThreatFilters;
import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.ThreatSnapshot;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import dev.xkmc.youkaishomecoming.init.registrate.YHEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Server-side pilot used by live {@link YoukaiEntity} instances. */
public final class YoukaiDodgePilot {

	private final ThreatProviderRegistry registry = new ThreatProviderRegistry();
	private final ObservedMotionProvider observed = new ObservedMotionProvider();
	private DodgePilot pilot = new DodgePilot(PilotProfile.SERVER_BUDGET);
	private long profileFingerprint = Long.MIN_VALUE;
	private Vec3 heldVelocity = Vec3.ZERO;
	private int holdTicks;

	public YoukaiDodgePilot() {
		registry.register(new MoverExactProvider());
		registry.register(new BallisticProvider());
		registry.register(observed);
	}

	public void tick(YoukaiEntity entity) {
		var config = YHModConfig.COMMON;
		if (!config.youkaiAutoDodgeEnabled.get() || !entity.hasEffect(YHEffects.AUTO_DODGE.get()) ||
				entity.getTarget() == null || !entity.isAlive()) {
			reset();
			return;
		}

		if (holdTicks > 0) {
			holdTicks--;
			apply(entity, heldVelocity, config.youkaiAutoDodgeMaxSpeed.get());
			return;
		}

		refreshProfile();
		double radius = config.youkaiAutoDodgeScanRadius.get();
		List<Entity> threats = entity.level().getEntities(entity, entity.getBoundingBox().inflate(radius),
				candidate -> isProjectile(candidate) && ThreatFilters.isHostileTo(entity, candidate));
		if (threats.isEmpty()) {
			reset();
			return;
		}

		Vec3 feet = entity.position();
		ThreatSnapshot snapshot = ThreatSnapshot.capture(threats, registry,
				pilot.profile().predictHorizon(), pilot.profile().threatTopK(), feet);
		if (snapshot.size() == 0) {
			reset();
			return;
		}

		PilotState state = new PilotState(feet, entity.getDeltaMovement(),
				SelfBoxModel.youkaiDanmaku(entity.getBbWidth(), entity.getBbHeight()));
		state.oracle = new LevelCollisionOracle(entity.level(), entity);
		state.anchor = feet;
		state.tick = entity.tickCount;
		state.wallClearanceRadius = config.youkaiAutoDodgeWallClearanceRadius.get();
		state.wallClearanceGain = config.youkaiAutoDodgeWallClearanceGain.get();
		state.wallClearanceDangerDist = config.youkaiAutoDodgeWallClearanceDangerDist.get();
		state.wallClearanceSafeDist = config.youkaiAutoDodgeWallClearanceSafeDist.get();

		Vec3 desired = pilot.tick(snapshot, state);
		if (!entity.isFlying()) {
			desired = new Vec3(desired.x, entity.getDeltaMovement().y, desired.z);
		}
		heldVelocity = desired;
		holdTicks = Math.max(0, config.youkaiAutoDodgeTickInterval.get() - 1);
		apply(entity, desired, config.youkaiAutoDodgeMaxSpeed.get());
	}

	private void refreshProfile() {
		var config = YHModConfig.COMMON;
		long fingerprint = Double.doubleToLongBits(config.youkaiAutoDodgeHighSpeed.get())
				^ Double.doubleToLongBits(config.youkaiAutoDodgeLowSpeed.get()) * 31
				^ ((long) config.youkaiAutoDodgeThreatTopK.get() << 16)
				^ config.youkaiAutoDodgePredictHorizon.get();
		if (fingerprint == profileFingerprint) return;
		profileFingerprint = fingerprint;
		pilot = new DodgePilot(PilotProfile.SERVER_BUDGET.withMotion(
				config.youkaiAutoDodgeHighSpeed.get(),
				config.youkaiAutoDodgeLowSpeed.get(),
				config.youkaiAutoDodgeThreatTopK.get(),
				config.youkaiAutoDodgePredictHorizon.get()));
	}

	private static boolean isProjectile(Entity entity) {
		return entity instanceof Projectile || entity instanceof SimplifiedProjectile || entity instanceof IYHDanmaku;
	}

	private static void apply(YoukaiEntity entity, Vec3 desired, double maxSpeed) {
		if (desired.lengthSqr() < 1.0e-8) return;
		if (desired.length() > maxSpeed) {
			desired = desired.normalize().scale(maxSpeed);
		}
		entity.getNavigation().stop();
		entity.setDeltaMovement(desired);
		entity.hasImpulse = true;
	}

	private void reset() {
		pilot.reset();
		observed.clear();
		heldVelocity = Vec3.ZERO;
		holdTicks = 0;
	}
}
