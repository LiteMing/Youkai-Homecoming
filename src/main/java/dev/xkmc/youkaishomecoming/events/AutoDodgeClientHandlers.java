package dev.xkmc.youkaishomecoming.events;

import dev.xkmc.fastprojectileapi.entity.SimplifiedProjectile;
import dev.xkmc.fastprojectileapi.render.virtual.ClientDanmakuCache;
import dev.xkmc.youkaishomecoming.content.capability.GrazeHelper;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.YHBaseLaserEntity;
import dev.xkmc.youkaishomecoming.content.spell.pilot.DodgePilot;
import dev.xkmc.youkaishomecoming.content.spell.pilot.PilotProfile;
import dev.xkmc.youkaishomecoming.content.spell.pilot.PilotState;
import dev.xkmc.youkaishomecoming.content.spell.pilot.predict.BallisticProvider;
import dev.xkmc.youkaishomecoming.content.spell.pilot.predict.MoverExactProvider;
import dev.xkmc.youkaishomecoming.content.spell.pilot.predict.ObservedMotionProvider;
import dev.xkmc.youkaishomecoming.content.spell.pilot.predict.ThreatProviderRegistry;
import dev.xkmc.youkaishomecoming.content.spell.pilot.search.GroundedModel;
import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.LevelCollisionOracle;
import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.SelfBoxModel;
import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.ThreatFilters;
import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.ThreatSnapshot;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import dev.xkmc.youkaishomecoming.init.registrate.YHEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/** Client-authoritative player auto-dodge. All three levels share one controller. */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = YoukaisHomecoming.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AutoDodgeClientHandlers {

	private static final double DEFAULT_ANCHOR_RADIUS = 10.0;

	private static final ThreatProviderRegistry REGISTRY = new ThreatProviderRegistry();
	private static final ObservedMotionProvider OBSERVED = new ObservedMotionProvider();
	private static DodgePilot[] flightPilots = createPilots(false,
			PilotProfile.DEFAULT_PLAYER_BASE_SPEED, PilotProfile.DEFAULT_PLAYER_SPEED_STEP);
	private static DodgePilot[] groundPilots = createPilots(true,
			PilotProfile.DEFAULT_PLAYER_BASE_SPEED, PilotProfile.DEFAULT_PLAYER_SPEED_STEP);

	private static boolean providersReady;
	private static int joinScanTicks;
	private static Entity joinedEntity;
	private static int lastAmp = -1;
	private static long profileFingerprint = Long.MIN_VALUE;
	private static Vec3 playerAnchor;
	private static boolean manualOverride;
	private static LocalPlayer trackedPlayer;
	private static boolean pilotAppliedVelocity;

	private static DodgePilot[] createPilots(boolean grounded, double baseSpeed, double speedStep) {
		DodgePilot[] result = new DodgePilot[3];
		for (int tier = 0; tier < result.length; tier++) {
			PilotProfile profile = PilotProfile.playerTier(tier, baseSpeed, speedStep);
			result[tier] = grounded
					? new DodgePilot(profile, new GroundedModel())
					: new DodgePilot(profile);
		}
		return result;
	}

	private static void ensureProviders() {
		if (providersReady) return;
		REGISTRY.register(new MoverExactProvider());
		REGISTRY.register(new BallisticProvider());
		REGISTRY.register(OBSERVED);
		providersReady = true;
	}

	private static void refreshProfilesIfNeeded() {
		var config = YHModConfig.COMMON;
		double baseSpeed = config.autoDodgeBaseSpeed.get();
		double speedStep = config.autoDodgeSpeedPerTier.get();
		long fingerprint = Double.doubleToLongBits(baseSpeed)
				^ Double.doubleToLongBits(speedStep) * 31;
		if (fingerprint == profileFingerprint) return;
		profileFingerprint = fingerprint;
		flightPilots = createPilots(false, baseSpeed, speedStep);
		groundPilots = createPilots(true, baseSpeed, speedStep);
	}

	@SubscribeEvent
	public static void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END) return;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.isPaused() || minecraft.player == null || minecraft.level == null) return;
		LocalPlayer player = minecraft.player;
		if (!player.isLocalPlayer()) return;
		trackPlayer(player);
		if (player.isSpectator()) {
			deactivate(player);
			return;
		}

		if (!YHModConfig.COMMON.autoDodgeEnabled.get()) {
			deactivate(player);
			return;
		}
		MobEffectInstance effect = player.getEffect(YHEffects.AUTO_DODGE.get());
		if (effect == null) {
			deactivate(player);
			return;
		}

		ensureProviders();
		refreshProfilesIfNeeded();
		int amplifier = Math.min(2, effect.getAmplifier());
		if (amplifier != lastAmp) {
			resetControllers();
			playerAnchor = player.position();
			lastAmp = amplifier;
		}

		Vec3 input = readInputWish(player);
		boolean controlBias = Screen.hasControlDown();
		if (!controlBias && input.lengthSqr() > 1e-8) {
			if (!manualOverride) resetControllers();
			manualOverride = true;
			pilotAppliedVelocity = false;
			playerAnchor = player.position();
			return;
		}
		if (manualOverride) {
			manualOverride = false;
			playerAnchor = player.position();
			resetControllers();
		}
		if (controlBias) player.setSprinting(false);

		Entity extra = joinScanTicks > 0 && joinedEntity != null && joinedEntity.isAlive()
				? joinedEntity : null;
		tryDodge(player, amplifier, extra, controlBias ? input : Vec3.ZERO);
		if (joinScanTicks > 0) joinScanTicks--;
	}

	@SubscribeEvent
	public static void onEntityJoin(EntityJoinLevelEvent event) {
		if (!YHModConfig.COMMON.autoDodgeEnabled.get()) return;
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || player.level() != event.getLevel()
				|| !player.hasEffect(YHEffects.AUTO_DODGE.get())) return;
		trackPlayer(player);
		Entity entity = event.getEntity();
		if (!isThreatCandidate(entity) || !ThreatFilters.isHostileTo(player, entity)) return;

		joinedEntity = entity;
		joinScanTicks = 3;
		Vec3 input = readInputWish(player);
		boolean controlBias = Screen.hasControlDown();
		if (!controlBias && input.lengthSqr() > 1e-8) return;
		ensureProviders();
		refreshProfilesIfNeeded();
		MobEffectInstance effect = player.getEffect(YHEffects.AUTO_DODGE.get());
		if (effect != null) {
			tryDodge(player, Math.min(2, effect.getAmplifier()), entity,
					controlBias ? input : Vec3.ZERO);
		}
	}

	private static void tryDodge(LocalPlayer player, int amplifier, Entity extra, Vec3 inputPreference) {
		var config = YHModConfig.COMMON;
		double scanRadius = config.autoDodgeBaseScanRadius.get()
				+ amplifier * config.autoDodgeScanRadiusPerTier.get();
		List<Entity> threats = collectThreats(player, extra, scanRadius);
		if (threats.isEmpty()) {
			releasePilotMotion(player);
			resetControllers();
			return;
		}

		boolean freeFlight = canVerticalFlight(player);
		DodgePilot pilot = pilotFor(amplifier, freeFlight);
		Vec3 feet = player.position();
		float hitScale = GrazeHelper.getHitBoxScale(player);
		Vec3 eye = new Vec3(player.getX(), player.getEyeY(), player.getZ());
		SelfBoxModel box = SelfBoxModel.playerDanmaku(player.getBoundingBox(), feet, eye, hitScale);
		ThreatSnapshot snapshot = ThreatSnapshot.capture(threats, REGISTRY,
				pilot.profile().predictHorizon(), pilot.profile().threatTopK(), feet);
		if (snapshot.size() == 0) {
			releasePilotMotion(player);
			resetControllers();
			return;
		}

		if (playerAnchor == null) playerAnchor = feet;
		PilotState state = new PilotState(feet, player.getDeltaMovement(), box);
		state.oracle = new LevelCollisionOracle(player.level(), player);
		state.anchor = playerAnchor;
		state.inputPreference = inputPreference;
		state.grounded = !freeFlight;
		state.hitBoxScale = hitScale;
		state.tick = player.tickCount;
		state.deadlineNanos = System.nanoTime() + pilot.profile().timeBudgetNanos();
		applyNavigationDefaults(state, pilot.profile());
		state.arena = new AABB(
				playerAnchor.x - DEFAULT_ANCHOR_RADIUS, playerAnchor.y - 4,
				playerAnchor.z - DEFAULT_ANCHOR_RADIUS,
				playerAnchor.x + DEFAULT_ANCHOR_RADIUS, playerAnchor.y + 6,
				playerAnchor.z + DEFAULT_ANCHOR_RADIUS);

		Vec3 desired = pilot.tick(snapshot, state);
		if (!freeFlight && desired.y < 0) desired = new Vec3(desired.x, 0, desired.z);
		applyVelocity(player, desired, true);
	}

	private static void applyNavigationDefaults(PilotState state, PilotProfile profile) {
		state.wallClearanceRadius = profile.wallClearanceRadius();
		state.wallClearanceGain = profile.wallClearanceGain();
		state.wallClearanceDangerDist = profile.wallClearanceDangerDist();
		state.wallClearanceSafeDist = profile.wallClearanceSafeDist();
	}

	private static void applyVelocity(LocalPlayer player, Vec3 desired, boolean replace) {
		if (!canVerticalFlight(player) && !player.onGround() && desired.y > 0) {
			desired = new Vec3(desired.x, 0, desired.z);
		}
		var oracle = new LevelCollisionOracle(player.level(), player);
		AABB next = player.getBoundingBox().move(desired);
		if (desired.lengthSqr() > 1e-10
				&& (!oracle.isPathFree(player.getBoundingBox(), desired) || !oracle.isFree(next))) {
			Vec3 horizontal = new Vec3(desired.x, 0, desired.z);
			next = player.getBoundingBox().move(horizontal);
			if (!oracle.isPathFree(player.getBoundingBox(), horizontal) || !oracle.isFree(next)) {
				desired = Vec3.ZERO;
			} else {
				desired = horizontal;
			}
		}

		Vec3 current = player.getDeltaMovement();
		if (replace) {
			double y = desired.y != 0 ? desired.y : current.y;
			if (!canVerticalFlight(player) && !player.onGround()) y = current.y;
			player.setDeltaMovement(desired.x, y, desired.z);
		} else {
			player.setDeltaMovement(desired);
		}
		player.hurtMarked = true;
		player.hasImpulse = true;
		pilotAppliedVelocity = true;
	}

	private static boolean canVerticalFlight(LocalPlayer player) {
		return player.getAbilities().flying || player.isFallFlying();
	}

	private static Vec3 readInputWish(LocalPlayer player) {
		float forward = player.input.forwardImpulse;
		float strafe = player.input.leftImpulse;
		double vertical = player.input.jumping ? 1 : player.input.shiftKeyDown ? -1 : 0;
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		if (flat.lengthSqr() < 1e-8) flat = new Vec3(0, 0, 1);
		flat = flat.normalize();
		Vec3 left = new Vec3(flat.z, 0, -flat.x);
		return flat.scale(forward).add(left.scale(strafe)).add(0, vertical, 0);
	}

	private static List<Entity> collectThreats(LocalPlayer player, Entity extra, double scanRadius) {
		AABB area = player.getBoundingBox().inflate(scanRadius);
		List<Entity> result = new ArrayList<>();
		Vec3 self = player.getBoundingBox().getCenter();
		double radiusSqr = scanRadius * scanRadius;
		for (Entity entity : player.level().getEntities(player, area,
				AutoDodgeClientHandlers::isThreatCandidate)) {
			if (ThreatFilters.isHostileTo(player, entity)) result.add(entity);
		}
		try {
			for (SimplifiedProjectile projectile : ClientDanmakuCache.get(player.level()).snapshot()) {
				if (!projectile.isValid() || threatScanDistSqr(projectile, self) > radiusSqr) continue;
				if (ThreatFilters.isHostileTo(player, projectile)) result.add(projectile);
			}
		} catch (Throwable ignored) {
		}
		if (extra != null && isThreatCandidate(extra) && ThreatFilters.isHostileTo(player, extra)
				&& !result.contains(extra)) result.add(extra);
		return result;
	}

	private static double threatScanDistSqr(Entity entity, Vec3 self) {
		if (entity instanceof YHBaseLaserEntity laser) {
			Vec3 start = laser.beamStart();
			Vec3 direction = laser.getLookAngle();
			if (direction.lengthSqr() < 1e-12) direction = new Vec3(0, 0, 1);
			float length = (float) Math.max(laser.getLength(), laser.effectiveLength(0));
			if (length <= 0.01f) return start.distanceToSqr(self);
			return distPointToSegmentSqr(self, start, start.add(direction.normalize().scale(length)));
		}
		return entity.position().distanceToSqr(self);
	}

	private static double distPointToSegmentSqr(Vec3 point, Vec3 start, Vec3 end) {
		Vec3 segment = end.subtract(start);
		double lengthSqr = segment.lengthSqr();
		if (lengthSqr < 1e-12) return point.distanceToSqr(start);
		double t = Math.max(0, Math.min(1, point.subtract(start).dot(segment) / lengthSqr));
		return point.distanceToSqr(start.add(segment.scale(t)));
	}

	private static boolean isThreatCandidate(Entity entity) {
		return entity instanceof Projectile || entity instanceof SimplifiedProjectile;
	}

	private static DodgePilot pilotFor(int amplifier, boolean freeFlight) {
		return (freeFlight ? flightPilots : groundPilots)[Math.max(0, Math.min(2, amplifier))];
	}

	private static void resetControllers() {
		for (DodgePilot pilot : flightPilots) pilot.reset();
		for (DodgePilot pilot : groundPilots) pilot.reset();
	}

	private static void trackPlayer(LocalPlayer player) {
		if (trackedPlayer == player) return;
		if (trackedPlayer != null) releasePilotMotion(trackedPlayer);
		resetControllers();
		OBSERVED.clear();
		lastAmp = -1;
		playerAnchor = null;
		manualOverride = false;
		joinScanTicks = 0;
		joinedEntity = null;
		trackedPlayer = player;
	}

	private static void releasePilotMotion(LocalPlayer player) {
		if (!pilotAppliedVelocity) return;
		Vec3 current = player.getDeltaMovement();
		double y = canVerticalFlight(player) ? 0 : current.y;
		player.setDeltaMovement(0, y, 0);
		player.hurtMarked = true;
		player.hasImpulse = true;
		pilotAppliedVelocity = false;
	}

	private static void deactivate(LocalPlayer player) {
		releasePilotMotion(player);
		if (lastAmp < 0) return;
		resetControllers();
		OBSERVED.clear();
		lastAmp = -1;
		playerAnchor = null;
		manualOverride = false;
		joinScanTicks = 0;
		joinedEntity = null;
	}
}
