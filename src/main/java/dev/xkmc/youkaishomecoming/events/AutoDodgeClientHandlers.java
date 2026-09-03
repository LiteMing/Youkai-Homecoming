package dev.xkmc.youkaishomecoming.events;

import com.mojang.logging.LogUtils;
import dev.xkmc.fastprojectileapi.entity.SimplifiedProjectile;
import dev.xkmc.fastprojectileapi.render.virtual.ClientDanmakuCache;
import dev.xkmc.youkaishomecoming.content.capability.GrazeHelper;
import dev.xkmc.youkaishomecoming.content.spell.pilot.DodgePilot;
import dev.xkmc.youkaishomecoming.content.spell.pilot.PilotProfile;
import dev.xkmc.youkaishomecoming.content.spell.pilot.PilotState;
import dev.xkmc.youkaishomecoming.content.spell.pilot.predict.BallisticProvider;
import dev.xkmc.youkaishomecoming.content.spell.pilot.predict.MoverExactProvider;
import dev.xkmc.youkaishomecoming.content.spell.pilot.predict.ObservedMotionProvider;
import dev.xkmc.youkaishomecoming.content.spell.pilot.predict.ThreatProviderRegistry;
import dev.xkmc.youkaishomecoming.content.spell.pilot.search.GroundedModel;
import dev.xkmc.youkaishomecoming.content.spell.pilot.search.SpatioTemporalSearch;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.YHBaseLaserEntity;
import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.LevelCollisionOracle;
import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.ScoreResult;
import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.SelfBoxModel;
import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.ThreatFilters;
import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.ThreatSnapshot;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import dev.xkmc.youkaishomecoming.init.registrate.YHEffects;
import net.minecraft.client.Minecraft;
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
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-authoritative player auto-dodge (Phase 7).
 * Amp 0 = rescue, 1 = assist, 2+ = takeover.
 * Parameters from {@link YHModConfig.Common} {@code auto_dodge} (COMMON config).
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = YoukaisHomecoming.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AutoDodgeClientHandlers {

	private static final Logger LOGGER = LogUtils.getLogger();

	private static final ThreatProviderRegistry REGISTRY = new ThreatProviderRegistry();
	private static final ObservedMotionProvider OBSERVED = new ObservedMotionProvider();
	private static DodgePilot pilotI = new DodgePilot(PilotProfile.NOVICE);
	private static DodgePilot pilotII = new DodgePilot(PilotProfile.ADEPT);
	private static DodgePilot pilotIII = new DodgePilot(PilotProfile.LUNATIC);
	private static SpatioTemporalSearch groundSearch =
			new SpatioTemporalSearch(new GroundedModel(), pilotI.scorer());

	private static boolean providersReady;
	private static int emergencyCooldown;
	private static int joinScanTicks;
	private static Entity joinedEntity;
	private static int lastAmp = -1;
	private static int debugTick;
	/** Fingerprint of speed/topK/horizon config so pilots rebuild after /reload. */
	private static long profileFingerprint = Long.MIN_VALUE;

	private static void ensureProviders() {
		if (providersReady) return;
		REGISTRY.register(new MoverExactProvider());
		REGISTRY.register(new BallisticProvider());
		REGISTRY.register(OBSERVED);
		providersReady = true;
	}

	private static void refreshProfilesIfNeeded() {
		var c = YHModConfig.COMMON;
		long fp = Double.doubleToLongBits(c.autoDodgeTierIHighSpeed.get())
				^ Double.doubleToLongBits(c.autoDodgeTierILowSpeed.get()) * 31
				^ Double.doubleToLongBits(c.autoDodgeTierIIHighSpeed.get()) * 37
				^ Double.doubleToLongBits(c.autoDodgeTierIILowSpeed.get()) * 41
				^ Double.doubleToLongBits(c.autoDodgeTierIIIHighSpeed.get()) * 43
				^ Double.doubleToLongBits(c.autoDodgeTierIIILowSpeed.get()) * 47
				^ ((long) c.autoDodgeThreatTopK.get() << 16)
				^ c.autoDodgePredictHorizon.get();
		if (fp == profileFingerprint) return;
		profileFingerprint = fp;
		int topK = c.autoDodgeThreatTopK.get();
		int horizon = c.autoDodgePredictHorizon.get();
		pilotI = new DodgePilot(PilotProfile.NOVICE.withMotion(
				c.autoDodgeTierIHighSpeed.get(), c.autoDodgeTierILowSpeed.get(), topK, horizon));
		pilotII = new DodgePilot(PilotProfile.ADEPT.withMotion(
				c.autoDodgeTierIIHighSpeed.get(), c.autoDodgeTierIILowSpeed.get(), topK, horizon));
		pilotIII = new DodgePilot(PilotProfile.LUNATIC.withMotion(
				c.autoDodgeTierIIIHighSpeed.get(), c.autoDodgeTierIIILowSpeed.get(), topK, horizon));
		groundSearch = new SpatioTemporalSearch(new GroundedModel(), pilotI.scorer());
	}

	@SubscribeEvent
	public static void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.isPaused() || mc.player == null || mc.level == null) return;
		LocalPlayer player = mc.player;
		if (!player.isLocalPlayer() || player.isSpectator()) return;

		if (!YHModConfig.COMMON.autoDodgeEnabled.get()) {
			if (lastAmp >= 0) {
				resetPilots();
				lastAmp = -1;
			}
			return;
		}

		MobEffectInstance eff = player.getEffect(YHEffects.AUTO_DODGE.get());
		if (eff == null) {
			if (lastAmp >= 0) {
				resetPilots();
				lastAmp = -1;
			}
			return;
		}

		ensureProviders();
		refreshProfilesIfNeeded();
		if (emergencyCooldown > 0) emergencyCooldown--;

		int amp = Math.min(2, eff.getAmplifier());
		if (amp != lastAmp) {
			resetPilots();
			lastAmp = amp;
			LOGGER.info("[AutoDodge] active amp={} (0=rescue,1=assist,2=takeover)", amp);
		}

		Entity extra = joinScanTicks > 0 && joinedEntity != null && joinedEntity.isAlive() ? joinedEntity : null;
		tryDodge(player, amp, extra, true);
		if (joinScanTicks > 0) joinScanTicks--;
	}

	@SubscribeEvent
	public static void onEntityJoin(EntityJoinLevelEvent event) {
		if (!YHModConfig.COMMON.autoDodgeEnabled.get()) return;
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || player.level() != event.getLevel()) return;
		if (!player.hasEffect(YHEffects.AUTO_DODGE.get())) return;
		Entity e = event.getEntity();
		if (!isThreatCandidate(e) || !ThreatFilters.isHostileTo(player, e)) {
			return;
		}
		joinedEntity = e;
		joinScanTicks = 3;
		ensureProviders();
		refreshProfilesIfNeeded();
		MobEffectInstance eff = player.getEffect(YHEffects.AUTO_DODGE.get());
		if (eff != null) {
			tryDodge(player, Math.min(2, eff.getAmplifier()), e, false);
		}
	}

	private static void tryDodge(LocalPlayer player, int amp, Entity extra, boolean allowEmergency) {
		var cfg = YHModConfig.COMMON;
		List<Entity> threats = collectThreats(player, extra, cfg.autoDodgeScanRadius.get());
		if (threats.isEmpty()) {
			logDebug(player, amp, 0, 0, Double.POSITIVE_INFINITY, Vec3.ZERO, "no-threats");
			return;
		}

		Vec3 feet = player.position();
		float hitDelta = GrazeHelper.getHitBoxDelta(player);
		SelfBoxModel box = SelfBoxModel.playerDanmaku(hitDelta);
		DodgePilot pilot = pilotFor(amp);
		int horizon = pilot.profile().predictHorizon();
		int topK = pilot.profile().threatTopK();

		ThreatSnapshot snap = ThreatSnapshot.capture(threats, REGISTRY, horizon, topK, feet);
		if (snap.size() == 0) {
			logDebug(player, amp, threats.size(), 0, Double.POSITIVE_INFINITY, Vec3.ZERO, "capture-empty");
			return;
		}

		PilotState state = new PilotState(feet, player.getDeltaMovement(), box);
		state.oracle = new LevelCollisionOracle(player.level(), player);
		Vec3 lookFlat = player.getLookAngle().multiply(1, 0, 1);
		if (lookFlat.lengthSqr() < 1e-8) lookFlat = new Vec3(0, 0, 1);
		state.anchor = feet.subtract(lookFlat.normalize().scale(0.5));
		state.hitBoxDelta = hitDelta;
		state.tick = player.tickCount;
		// Hard time budget every path — ground search without deadline freezes the render thread
		long budget = pilot.profile().timeBudgetNanos();
		if (budget > 0) {
			state.deadlineNanos = System.nanoTime() + budget;
		}
		state.wallClearanceRadius = cfg.autoDodgeWallClearanceRadius.get();
		state.wallClearanceGain = cfg.autoDodgeWallClearanceGain.get();
		state.wallClearanceDangerDist = cfg.autoDodgeWallClearanceDangerDist.get();
		state.wallClearanceSafeDist = cfg.autoDodgeWallClearanceSafeDist.get();
		double r = 10;
		state.arena = new AABB(feet.x - r, feet.y - 4, feet.z - r, feet.x + r, feet.y + 6, feet.z + r);

		boolean flying = player.getAbilities().flying || player.isFallFlying() || !player.onGround();
		double rescueClr = cfg.autoDodgeRescueClearance.get();
		int emergCd = cfg.autoDodgeEmergencyCooldown.get();
		double inputPri = cfg.autoDodgeInputPriority.get();
		double assistPilotW = cfg.autoDodgeAssistPilotWeight.get();
		double assistCurW = cfg.autoDodgeAssistCurrentWeight.get();
		double assistCap = cfg.autoDodgeAssistSpeedCap.get();
		double takeoverMin = cfg.autoDodgeTakeoverMinSpeed.get();
		double rescueSpd = cfg.autoDodgeRescuePulseSpeed.get();
		double rescueJump = cfg.autoDodgeRescueJump.get();

		if (amp == 0) {
			ScoreResult sc = pilot.scorer().score(snap, box, feet, player.getDeltaMovement(), 0, state);
			if (!sc.hardHit() && sc.minClearance() > rescueClr) {
				logDebug(player, amp, threats.size(), snap.size(), sc.minClearance(), Vec3.ZERO, "safe");
				return;
			}
			if (!allowEmergency && emergencyCooldown > 0) return;
			// Under fire: no wall probes (they dominate cost and steal dodge directions)
			state.wallClearanceGain = 0;
			Vec3 pulse = computeRescuePulse(player, snap, state, flying, rescueSpd, rescueJump);
			if (pulse.lengthSqr() > 1e-6) {
				applyVelocity(player, pulse, true);
				emergencyCooldown = emergCd;
				logDebug(player, amp, threats.size(), snap.size(), sc.minClearance(), pulse, "rescue");
			}
			return;
		}

		if (amp == 1) {
			Vec3 desired = pilot.tick(snap, state);
			Vec3 input = readInputWish(player);
			if (input.lengthSqr() > inputPri * inputPri) {
				desired = input.normalize().scale(player.getDeltaMovement().horizontalDistance() + 0.05)
						.add(desired.scale(0.35));
			} else {
				Vec3 cur = player.getDeltaMovement();
				desired = cur.scale(assistCurW).add(desired.scale(assistPilotW));
			}
			if (player.onGround()) {
				desired = new Vec3(desired.x, Math.max(0, desired.y), desired.z);
			}
			double cap = Math.max(assistCap, pilot.profile().highSpeed());
			if (desired.horizontalDistance() > cap) {
				Vec3 h = new Vec3(desired.x, 0, desired.z).normalize().scale(cap);
				desired = new Vec3(h.x, desired.y, h.z);
			}
			applyVelocity(player, desired, false);
			logDebug(player, amp, threats.size(), snap.size(), pilot.lastClearance(), desired, "assist");
			return;
		}

		// amp 2 takeover
		Vec3 desired;
		if (flying) {
			desired = pilot.tick(snap, state);
		} else {
			// Ground: always treat present threats as actionable. Old gate required
			// clr < searchEnter (~0.8), but t=0 clearance to a distant arrow is large —
			// player stood still until impact. Use predicted hit + generous enter pad.
			ScoreResult sc = pilot.scorer().score(snap, box, feet, player.getDeltaMovement(), 0, state);
			boolean danger = sc.hardHit()
					|| sc.minClearance() < Math.max(pilot.profile().searchEnterClearance(), 4.0)
					|| willHitSoon(pilot, snap, box, feet, player.getDeltaMovement(), horizon);
			if (danger) {
				// Emergency ground dodge: wall probes off (freeze source on dense search)
				state.wallClearanceGain = 0;
				Vec3 lateral = sideStepAway(player, snap, Math.max(takeoverMin, pilot.profile().highSpeed()));
				var sr = groundSearch.search(snap, state, pilot.profile());
				if (sr.firstStep().lengthSqr() > 1e-10) {
					desired = sr.firstStep();
					// Prefer pure horizontal on ground unless jump is the only escape
					if (player.onGround() && desired.y > 0 && desired.horizontalDistance() > 1e-6) {
						// keep jump component from search
					} else if (player.onGround() && desired.y < 0) {
						desired = new Vec3(desired.x, 0, desired.z);
					}
				} else if (lateral.lengthSqr() > 1e-10) {
					desired = lateral;
				} else {
					desired = pilot.tick(snap, state);
				}
			} else {
				desired = pilot.tick(snap, state);
				if (player.onGround() && desired.y < 0) {
					desired = new Vec3(desired.x, 0, desired.z);
				}
			}
		}
		if (desired.horizontalDistance() > 1e-6 && desired.horizontalDistance() < takeoverMin) {
			Vec3 h = new Vec3(desired.x, 0, desired.z).normalize().scale(takeoverMin);
			desired = new Vec3(h.x, desired.y, h.z);
		}
		// Last resort: if still zero with live threats, force MLM-style side step
		if (desired.lengthSqr() < 1e-10 && !snap.threats().isEmpty()) {
			desired = sideStepAway(player, snap, Math.max(takeoverMin, 0.35));
		}
		applyVelocity(player, desired, true);
		logDebug(player, amp, threats.size(), snap.size(),
				pilot.lastClearance() < Double.POSITIVE_INFINITY ? pilot.lastClearance()
						: pilot.scorer().score(snap, box, feet, desired, 0, state).minClearance(),
				desired, "takeover");
	}

	/**
	 * True if any threat frame will hard-hit self within the next few ticks along current motion.
	 * Used so ground takeover reacts before t=0 clearance collapses.
	 */
	private static boolean willHitSoon(DodgePilot pilot, ThreatSnapshot snap, SelfBoxModel box,
	                                   Vec3 feet, Vec3 motion, int horizon) {
		int n = Math.min(horizon, Math.min(10, snap.horizon()));
		if (n <= 0) return false;
		for (int t = 0; t < n; t++) {
			ScoreResult sc = pilot.scorer().score(snap, box, feet, motion, t, null);
			if (sc.hardHit() || sc.minClearance() < 0.35) return true;
		}
		return false;
	}

	/** MLM-style: step perpendicular to the nearest threat velocity / approach vector. */
	private static Vec3 sideStepAway(LocalPlayer player, ThreatSnapshot snap, double speed) {
		if (snap.threats().isEmpty()) return Vec3.ZERO;
		var th = snap.threats().get(0);
		var frames = th.frames();
		if (frames.length == 0) return Vec3.ZERO;
		Vec3 tpos = frames[0].position();
		Vec3 tvel = frames.length > 1 ? frames[1].position().subtract(frames[0].position()) : Vec3.ZERO;
		Vec3 forward = new Vec3(tvel.x, 0, tvel.z);
		if (forward.lengthSqr() < 1e-6) {
			forward = new Vec3(player.getLookAngle().x, 0, player.getLookAngle().z);
		}
		if (forward.lengthSqr() < 1e-6) forward = new Vec3(0, 0, 1);
		forward = forward.normalize();
		Vec3 perp = new Vec3(-forward.z, 0, forward.x);
		Vec3 toPlayer = player.position().add(0, 0.9, 0).subtract(tpos);
		if (toPlayer.dot(perp) < 0) perp = perp.scale(-1);
		Vec3 step = perp.scale(speed);
		// Prefer free side; if blocked try opposite
		var oracle = new LevelCollisionOracle(player.level(), player);
		AABB current = player.getBoundingBox();
		AABB next = current.move(step.x, 0, step.z);
		if (!oracle.isPathFree(current, step) || !oracle.isFree(next)) {
			step = perp.scale(-speed);
			next = current.move(step.x, 0, step.z);
			if (!oracle.isPathFree(current, step) || !oracle.isFree(next)) return Vec3.ZERO;
		}
		return step;
	}

	private static Vec3 computeRescuePulse(LocalPlayer player, ThreatSnapshot snap, PilotState state,
	                                       boolean flying, double rescueSpd, double rescueJump) {
		if (flying) {
			return pilotI.tick(snap, state);
		}
		var sr = groundSearch.search(snap, state, pilotI.profile());
		if (sr.firstStep().lengthSqr() > 1e-10) {
			Vec3 step = sr.firstStep();
			if (step.horizontalDistance() < rescueSpd * 0.5 && step.horizontalDistance() > 1e-6) {
				Vec3 h = new Vec3(step.x, 0, step.z).normalize().scale(rescueSpd);
				step = new Vec3(h.x, Math.max(step.y, rescueJump * 0.6), h.z);
			}
			return step;
		}
		Vec3 away = Vec3.ZERO;
		if (!snap.threats().isEmpty() && snap.threats().get(0).frames().length > 0) {
			Vec3 tpos = snap.threats().get(0).frames()[0].position();
			away = player.position().subtract(tpos);
			away = new Vec3(away.x, 0, away.z);
			if (away.lengthSqr() > 1e-8) away = away.normalize().scale(rescueSpd);
		}
		if (away.lengthSqr() < 1e-8) {
			Vec3 look = player.getLookAngle();
			away = new Vec3(-look.z, 0, look.x).normalize().scale(rescueSpd);
		}
		return away.add(0, rescueJump, 0);
	}

	private static void applyVelocity(LocalPlayer player, Vec3 desired, boolean replace) {
		if (desired.lengthSqr() < 1e-10) return;
		// No flight: never inject upward motion while airborne (amp 2 included)
		if (!canVerticalFlight(player) && !player.onGround() && desired.y > 0) {
			desired = new Vec3(desired.x, 0, desired.z);
			if (desired.lengthSqr() < 1e-10) return;
		}
		Vec3 pathDelta = desired;
		var oracle = new LevelCollisionOracle(player.level(), player);
		AABB next = player.getBoundingBox().move(pathDelta);
		if (!oracle.isPathFree(player.getBoundingBox(), pathDelta) || !oracle.isFree(next)) {
			Vec3 h = new Vec3(desired.x, 0, desired.z);
			next = player.getBoundingBox().move(h.x, 0, h.z);
			if (!oracle.isPathFree(player.getBoundingBox(), h) || !oracle.isFree(next)) return;
			desired = h;
		}
		if (replace) {
			Vec3 cur = player.getDeltaMovement();
			double y = desired.y != 0 ? desired.y : cur.y;
			// Airborne without flight: preserve gravity, never replace with upward pilot y
			if (!canVerticalFlight(player) && !player.onGround() && y > 0) {
				y = cur.y;
			}
			player.setDeltaMovement(desired.x, y, desired.z);
		} else {
			player.setDeltaMovement(desired);
		}
		player.hurtMarked = true;
		player.hasImpulse = true;
	}

	/** Creative/spectator fly, mayfly, or elytra — otherwise airborne Y is gravity-only. */
	private static boolean canVerticalFlight(LocalPlayer player) {
		return player.getAbilities().flying
				|| player.getAbilities().mayfly
				|| player.isFallFlying();
	}

	private static Vec3 readInputWish(LocalPlayer player) {
		float fwd = player.input.forwardImpulse;
		// leftImpulse: +1 = A (left), -1 = D (right)
		float str = player.input.leftImpulse;
		if (Math.abs(fwd) < 1e-4 && Math.abs(str) < 1e-4) return Vec3.ZERO;
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		if (flat.lengthSqr() < 1e-8) flat = new Vec3(0, 0, 1);
		flat = flat.normalize();
		Vec3 left = new Vec3(flat.z, 0, -flat.x);
		return flat.scale(fwd).add(left.scale(str));
	}

	private static List<Entity> collectThreats(LocalPlayer player, Entity extra, double scanRadius) {
		AABB area = player.getBoundingBox().inflate(scanRadius);
		List<Entity> list = new ArrayList<>();
		int worldRaw = 0, cacheRaw = 0, filtered = 0;
		Vec3 self = player.getBoundingBox().getCenter();
		double r2 = scanRadius * scanRadius;
		for (Entity e : player.level().getEntities(player, area, AutoDodgeClientHandlers::isThreatCandidate)) {
			worldRaw++;
			if (ThreatFilters.isHostileTo(player, e)) list.add(e);
			else filtered++;
		}
		try {
			ClientDanmakuCache cache = ClientDanmakuCache.get(player.level());
			for (SimplifiedProjectile sp : cache.snapshot()) {
				if (!sp.isValid()) continue;
				// Lasers: distance to segment (muzzle can be far while beam crosses player)
				if (threatScanDistSqr(sp, self) > r2) continue;
				cacheRaw++;
				if (ThreatFilters.isHostileTo(player, sp)) list.add(sp);
				else filtered++;
			}
		} catch (Throwable t) {
			LOGGER.warn("[AutoDodge] ClientDanmakuCache scan failed: {}", t.toString());
		}
		if (extra != null && isThreatCandidate(extra) && ThreatFilters.isHostileTo(player, extra) && !list.contains(extra)) {
			list.add(extra);
		}
		// Stash for debug log
		lastScanWorld = worldRaw;
		lastScanCache = cacheRaw;
		lastScanFiltered = filtered;
		return list;
	}

	/**
	 * Scan distance²: point bullets use center; lasers use nearest point on beam segment
	 * (same idea as Vertical_radar). Muzzle-only distance drops long beams that already
	 * cross the player.
	 */
	private static double threatScanDistSqr(Entity e, Vec3 self) {
		if (e instanceof YHBaseLaserEntity laser) {
			Vec3 start = laser.beamStart();
			Vec3 dir = laser.getLookAngle();
			if (dir.lengthSqr() < 1e-12) dir = new Vec3(0, 0, 1);
			dir = dir.normalize();
			// Prefer full length so warn-phase beams still enter the set
			float len = (float) Math.max(laser.getLength(), laser.effectiveLength(0f));
			if (len <= 0.01f) return start.distanceToSqr(self);
			Vec3 end = start.add(dir.scale(len));
			return distPointToSegmentSqr(self, start, end);
		}
		return e.position().distanceToSqr(self);
	}

	private static double distPointToSegmentSqr(Vec3 p, Vec3 a, Vec3 b) {
		Vec3 ab = b.subtract(a);
		double denom = ab.lengthSqr();
		if (denom < 1e-12) return p.distanceToSqr(a);
		double t = Math.max(0, Math.min(1, p.subtract(a).dot(ab) / denom));
		return p.distanceToSqr(a.add(ab.scale(t)));
	}

	private static int lastScanWorld, lastScanCache, lastScanFiltered;

	private static boolean isThreatCandidate(Entity e) {
		return e instanceof Projectile || e instanceof SimplifiedProjectile;
	}

	private static DodgePilot pilotFor(int amp) {
		return switch (amp) {
			case 0 -> pilotI;
			case 1 -> pilotII;
			default -> pilotIII;
		};
	}

	private static void resetPilots() {
		pilotI.reset();
		pilotII.reset();
		pilotIII.reset();
		OBSERVED.clear();
		emergencyCooldown = 0;
		debugTick = 0;
	}

	private static void logDebug(LocalPlayer player, int amp, int rawThreats, int snapSize,
	                             double clearance, Vec3 vel, String tag) {
		int interval = YHModConfig.COMMON.autoDodgeDebugLogInterval.get();
		if (interval <= 0) return;
		if ((++debugTick) % interval != 0 && !"rescue".equals(tag) && !"takeover".equals(tag)) {
			return;
		}
		int cacheSize = 0;
		try {
			cacheSize = ClientDanmakuCache.get(player.level()).size();
		} catch (Throwable ignored) {
		}
		LOGGER.info("[AutoDodge] amp={} tag={} threats={} snap={} cacheSize={} scan(w={},c={},filt={}) clr={} vel=({},{},{})",
				amp, tag, rawThreats, snapSize, cacheSize,
				lastScanWorld, lastScanCache, lastScanFiltered,
				String.format("%.2f", clearance),
				String.format("%.3f", vel.x), String.format("%.3f", vel.y), String.format("%.3f", vel.z));
	}
}
