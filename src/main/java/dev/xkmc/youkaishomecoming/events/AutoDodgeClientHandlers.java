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
import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.LevelCollisionOracle;
import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.NodeScorer;
import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.ScoreResult;
import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.SelfBoxModel;
import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.ThreatSnapshot;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
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
 * Amp 0 = rescue pulse, 1 = soft APF assist, 2+ = full pilot takeover.
 * <p>
 * YH danmaku on client lives in {@link ClientDanmakuCache} (not world entities).
 * Must scan that cache; {@code level.getEntities} alone sees only vanilla projectiles.
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = YoukaisHomecoming.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AutoDodgeClientHandlers {

	private static final Logger LOGGER = LogUtils.getLogger();
	private static final double SCAN_RADIUS = 16.0;
	private static final int EMERGENCY_COOLDOWN = 4;
	private static final double RESCUE_CLEARANCE = 1.25;
	private static final double INPUT_PRIORITY = 0.25;
	/** Debug log every N ticks when effect active (0 = off). */
	private static final int DEBUG_LOG_INTERVAL = 40;

	private static final ThreatProviderRegistry REGISTRY = new ThreatProviderRegistry();
	private static final ObservedMotionProvider OBSERVED = new ObservedMotionProvider();
	private static final DodgePilot PILOT_I = new DodgePilot(PilotProfile.NOVICE);
	private static final DodgePilot PILOT_II = new DodgePilot(PilotProfile.ADEPT);
	private static final DodgePilot PILOT_III = new DodgePilot(PilotProfile.LUNATIC);
	private static final SpatioTemporalSearch GROUND_SEARCH =
			new SpatioTemporalSearch(new GroundedModel(), NodeScorer.defaults());

	private static boolean providersReady;
	private static int emergencyCooldown;
	private static int joinScanTicks;
	private static Entity joinedEntity;
	private static int lastAmp = -1;
	private static int debugTick;

	private static void ensureProviders() {
		if (providersReady) return;
		REGISTRY.register(new MoverExactProvider());
		REGISTRY.register(new BallisticProvider());
		REGISTRY.register(OBSERVED);
		providersReady = true;
	}

	@SubscribeEvent
	public static void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.isPaused() || mc.player == null || mc.level == null) return;
		LocalPlayer player = mc.player;
		if (!player.isLocalPlayer() || player.isSpectator()) return;

		MobEffectInstance eff = player.getEffect(YHEffects.AUTO_DODGE.get());
		if (eff == null) {
			if (lastAmp >= 0) {
				resetPilots();
				lastAmp = -1;
			}
			return;
		}

		ensureProviders();
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
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || player.level() != event.getLevel()) return;
		if (!player.hasEffect(YHEffects.AUTO_DODGE.get())) return;
		Entity e = event.getEntity();
		if (!(e instanceof Projectile) && !(e instanceof SimplifiedProjectile)) {
			return;
		}
		joinedEntity = e;
		joinScanTicks = 3;
		ensureProviders();
		MobEffectInstance eff = player.getEffect(YHEffects.AUTO_DODGE.get());
		if (eff != null) {
			tryDodge(player, Math.min(2, eff.getAmplifier()), e, false);
		}
	}

	private static void tryDodge(LocalPlayer player, int amp, Entity extra, boolean allowEmergency) {
		List<Entity> threats = collectThreats(player, extra);
		if (threats.isEmpty()) {
			logDebug(player, amp, 0, 0, Double.POSITIVE_INFINITY, Vec3.ZERO, "no-threats");
			// Amp 0: nothing to rescue. Amp 1/2: still idle.
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
		// Anchor slightly behind look so APF has a soft home without freezing in place
		Vec3 lookFlat = player.getLookAngle().multiply(1, 0, 1);
		if (lookFlat.lengthSqr() < 1e-8) lookFlat = new Vec3(0, 0, 1);
		state.anchor = feet.subtract(lookFlat.normalize().scale(0.5));
		state.hitBoxDelta = hitDelta;
		state.tick = player.tickCount;
		double r = 10;
		state.arena = new AABB(feet.x - r, feet.y - 4, feet.z - r, feet.x + r, feet.y + 6, feet.z + r);

		boolean flying = player.getAbilities().flying || player.isFallFlying() || !player.onGround();

		if (amp == 0) {
			ScoreResult sc = pilot.scorer().score(snap, box, feet, player.getDeltaMovement(), 0);
			if (!sc.hardHit() && sc.minClearance() > RESCUE_CLEARANCE) {
				logDebug(player, amp, threats.size(), snap.size(), sc.minClearance(), Vec3.ZERO, "safe");
				return;
			}
			if (!allowEmergency && emergencyCooldown > 0) return;
			Vec3 pulse = computeRescuePulse(player, snap, state, flying);
			if (pulse.lengthSqr() > 1e-6) {
				applyVelocity(player, pulse, true);
				emergencyCooldown = EMERGENCY_COOLDOWN;
				logDebug(player, amp, threats.size(), snap.size(), sc.minClearance(), pulse, "rescue");
			}
			return;
		}

		if (amp == 1) {
			Vec3 desired = pilot.tick(snap, state);
			Vec3 input = readInputWish(player);
			if (input.lengthSqr() > INPUT_PRIORITY * INPUT_PRIORITY) {
				desired = input.normalize().scale(player.getDeltaMovement().horizontalDistance() + 0.05)
						.add(desired.scale(0.35));
			} else {
				Vec3 cur = player.getDeltaMovement();
				desired = cur.scale(0.35).add(desired.scale(0.65));
			}
			// Keep some vertical from current motion when grounded assist
			if (player.onGround()) {
				desired = new Vec3(desired.x, Math.max(0, desired.y), desired.z);
			}
			double cap = Math.max(0.28, pilot.profile().highSpeed());
			if (desired.horizontalDistance() > cap) {
				Vec3 h = new Vec3(desired.x, 0, desired.z).normalize().scale(cap);
				desired = new Vec3(h.x, desired.y, h.z);
			}
			applyVelocity(player, desired, false);
			logDebug(player, amp, threats.size(), snap.size(), pilot.lastClearance(), desired, "assist");
			return;
		}

		// Amp 2+: full takeover — stronger speeds via profile
		Vec3 desired;
		if (flying) {
			desired = pilot.tick(snap, state);
		} else {
			ScoreResult sc = pilot.scorer().score(snap, box, feet, player.getDeltaMovement(), 0);
			if (sc.hardHit() || sc.minClearance() < pilot.profile().searchEnterClearance()) {
				var sr = GROUND_SEARCH.search(snap, state, pilot.profile());
				desired = sr.firstStep().lengthSqr() > 1e-10 ? sr.firstStep() : pilot.tick(snap, state);
			} else {
				desired = pilot.tick(snap, state);
				if (player.onGround() && desired.y < 0) {
					desired = new Vec3(desired.x, 0, desired.z);
				}
			}
		}
		// Boost takeover horizontal so it's visible vs vanilla walk
		if (desired.horizontalDistance() > 1e-6 && desired.horizontalDistance() < 0.2) {
			Vec3 h = new Vec3(desired.x, 0, desired.z).normalize().scale(0.35);
			desired = new Vec3(h.x, desired.y, h.z);
		}
		applyVelocity(player, desired, true);
		logDebug(player, amp, threats.size(), snap.size(), pilot.lastClearance(), desired, "takeover");
	}

	private static Vec3 computeRescuePulse(LocalPlayer player, ThreatSnapshot snap, PilotState state, boolean flying) {
		if (flying) {
			return PILOT_I.tick(snap, state);
		}
		var sr = GROUND_SEARCH.search(snap, state, PilotProfile.NOVICE);
		if (sr.firstStep().lengthSqr() > 1e-10) {
			Vec3 step = sr.firstStep();
			// Ensure visible horizontal kick
			if (step.horizontalDistance() < 0.2 && step.horizontalDistance() > 1e-6) {
				Vec3 h = new Vec3(step.x, 0, step.z).normalize().scale(0.35);
				step = new Vec3(h.x, Math.max(step.y, 0.12), h.z);
			}
			return step;
		}
		Vec3 away = Vec3.ZERO;
		if (!snap.threats().isEmpty() && snap.threats().get(0).frames().length > 0) {
			Vec3 tpos = snap.threats().get(0).frames()[0].position();
			away = player.position().subtract(tpos);
			away = new Vec3(away.x, 0, away.z);
			if (away.lengthSqr() > 1e-8) away = away.normalize().scale(0.4);
		}
		if (away.lengthSqr() < 1e-8) {
			Vec3 look = player.getLookAngle();
			away = new Vec3(-look.z, 0, look.x).normalize().scale(0.4);
		}
		return away.add(0, 0.2, 0);
	}

	private static void applyVelocity(LocalPlayer player, Vec3 desired, boolean replace) {
		if (desired.lengthSqr() < 1e-10) return;
		AABB next = player.getBoundingBox().move(desired.x, Math.max(0, desired.y), desired.z);
		if (!player.level().noCollision(player, next)) {
			Vec3 h = new Vec3(desired.x, 0, desired.z);
			next = player.getBoundingBox().move(h.x, 0, h.z);
			if (!player.level().noCollision(player, next)) return;
			desired = h;
		}
		if (replace) {
			Vec3 cur = player.getDeltaMovement();
			double y = desired.y != 0 ? desired.y : cur.y;
			player.setDeltaMovement(desired.x, y, desired.z);
		} else {
			player.setDeltaMovement(desired);
		}
		player.hurtMarked = true;
		player.hasImpulse = true;
	}

	private static Vec3 readInputWish(LocalPlayer player) {
		float fwd = player.input.forwardImpulse;
		// leftImpulse: +1 = A (left), -1 = D (right) — KeyboardInput
		float str = player.input.leftImpulse;
		if (Math.abs(fwd) < 1e-4 && Math.abs(str) < 1e-4) return Vec3.ZERO;
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		if (flat.lengthSqr() < 1e-8) flat = new Vec3(0, 0, 1);
		flat = flat.normalize();
		// left = forward × up (Y-up): (fz, 0, -fx); do NOT use right × (+leftImpulse)
		Vec3 left = new Vec3(flat.z, 0, -flat.x);
		return flat.scale(fwd).add(left.scale(str));
	}

	/**
	 * World projectiles + virtual client danmaku cache.
	 */
	private static List<Entity> collectThreats(LocalPlayer player, Entity extra) {
		AABB area = player.getBoundingBox().inflate(SCAN_RADIUS);
		List<Entity> list = new ArrayList<>();

		// 1) Vanilla / real world projectiles
		for (Entity e : player.level().getEntities(player, area, AutoDodgeClientHandlers::isThreat)) {
			list.add(e);
		}

		// 2) YH virtual danmaku (client-only, not in world)
		try {
			ClientDanmakuCache cache = ClientDanmakuCache.get(player.level());
			for (SimplifiedProjectile sp : cache.snapshot()) {
				if (!sp.isValid()) continue;
				if (!area.contains(sp.position()) && !area.intersects(sp.getBoundingBox())) continue;
				list.add(sp);
			}
		} catch (Throwable t) {
			LOGGER.warn("[AutoDodge] ClientDanmakuCache scan failed: {}", t.toString());
		}

		if (extra != null && extra.isAlive() && isThreat(extra) && !list.contains(extra)) {
			list.add(extra);
		}
		return list;
	}

	private static boolean isThreat(Entity e) {
		if (e instanceof Projectile) return true;
		return e instanceof SimplifiedProjectile;
	}

	private static DodgePilot pilotFor(int amp) {
		return switch (amp) {
			case 0 -> PILOT_I;
			case 1 -> PILOT_II;
			default -> PILOT_III;
		};
	}

	private static void resetPilots() {
		PILOT_I.reset();
		PILOT_II.reset();
		PILOT_III.reset();
		OBSERVED.clear();
		emergencyCooldown = 0;
		debugTick = 0;
	}

	private static void logDebug(LocalPlayer player, int amp, int rawThreats, int snapSize,
	                             double clearance, Vec3 vel, String tag) {
		if (DEBUG_LOG_INTERVAL <= 0) return;
		if ((++debugTick) % DEBUG_LOG_INTERVAL != 0 && !"rescue".equals(tag) && !"takeover".equals(tag)) {
			return;
		}
		int cacheSize = 0;
		try {
			cacheSize = ClientDanmakuCache.get(player.level()).size();
		} catch (Throwable ignored) {
		}
		LOGGER.info("[AutoDodge] amp={} tag={} world+cacheThreats={} snap={} cacheSize={} clr={} vel=({},{},{})",
				amp, tag, rawThreats, snapSize, cacheSize,
				String.format("%.2f", clearance),
				String.format("%.3f", vel.x), String.format("%.3f", vel.y), String.format("%.3f", vel.z));
	}
}
