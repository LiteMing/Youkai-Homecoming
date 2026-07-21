package dev.xkmc.youkaishomecoming.events;

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

import java.util.ArrayList;
import java.util.List;

/**
 * Client-authoritative player auto-dodge (Phase 7).
 * Amp 0 = rescue pulse, 1 = soft APF assist, 2+ = full pilot takeover.
 * No MLM conflict handling (both experimental).
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = YoukaisHomecoming.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AutoDodgeClientHandlers {

	private static final double SCAN_RADIUS = 12.0;
	private static final int EMERGENCY_COOLDOWN = 6;
	private static final double RESCUE_CLEARANCE = 0.85;
	private static final double INPUT_PRIORITY = 0.35;

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

	private static void ensureProviders() {
		if (providersReady) return;
		// Client may include T1 exact for YH danmaku
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
		if (!(e instanceof Projectile) && !(e instanceof dev.xkmc.fastprojectileapi.entity.SimplifiedProjectile)) {
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
		if (threats.isEmpty() && amp == 0) return;

		Vec3 feet = player.position();
		float hitDelta = GrazeHelper.getHitBoxDelta(player);
		SelfBoxModel box = SelfBoxModel.playerDanmaku(hitDelta);
		DodgePilot pilot = pilotFor(amp);
		int horizon = pilot.profile().predictHorizon();
		int topK = pilot.profile().threatTopK();

		ThreatSnapshot snap = ThreatSnapshot.capture(threats, REGISTRY, horizon, topK, feet);
		if (snap.size() == 0 && amp == 0) return;

		PilotState state = new PilotState(feet, player.getDeltaMovement(), box);
		state.oracle = new LevelCollisionOracle(player.level(), player);
		state.anchor = feet; // stay near current pos unless forced
		state.hitBoxDelta = hitDelta;
		state.tick = player.tickCount;
		// Soft arena: large box around player so pilot doesn't wander forever
		double r = 8;
		state.arena = new AABB(feet.x - r, feet.y - 4, feet.z - r, feet.x + r, feet.y + 6, feet.z + r);

		boolean flying = player.getAbilities().flying || player.isFallFlying() || !player.onGround();

		if (amp == 0) {
			// Rescue only: act when clearance critical
			ScoreResult sc = pilot.scorer().score(snap, box, feet, player.getDeltaMovement(), 0);
			if (!sc.hardHit() && sc.minClearance() > RESCUE_CLEARANCE) return;
			if (!allowEmergency && emergencyCooldown > 0) return;
			Vec3 pulse = computeRescuePulse(player, snap, state, flying);
			if (pulse.lengthSqr() > 1e-6) {
				applyVelocity(player, pulse, true);
				emergencyCooldown = EMERGENCY_COOLDOWN;
			}
			return;
		}

		if (amp == 1) {
			// Soft assist: APF only, blend with input priority
			Vec3 desired = pilot.tick(snap, state);
			// Force APF path even if search mode latched: re-run soft only
			// (pilot.tick may search; for assist we prefer continuous micro-correction)
			Vec3 input = readInputWish(player);
			if (input.lengthSqr() > INPUT_PRIORITY * INPUT_PRIORITY) {
				// Player steering wins: only add 25% pilot
				desired = input.scale(0.75).add(desired.scale(0.25));
			} else {
				// Micro blend into current motion
				Vec3 cur = player.getDeltaMovement();
				desired = cur.scale(0.55).add(desired.scale(0.45));
			}
			// Cap assist speed
			if (desired.horizontalDistance() > pilot.profile().lowSpeed() * 1.2) {
				Vec3 h = new Vec3(desired.x, 0, desired.z);
				if (h.lengthSqr() > 1e-8) {
					h = h.normalize().scale(pilot.profile().lowSpeed() * 1.2);
					desired = new Vec3(h.x, desired.y, h.z);
				}
			}
			applyVelocity(player, desired, false);
			return;
		}

		// Amp 2+: full takeover
		Vec3 desired;
		if (flying) {
			desired = pilot.tick(snap, state);
		} else {
			// Grounded search when tight, else APF
			ScoreResult sc = pilot.scorer().score(snap, box, feet, player.getDeltaMovement(), 0);
			if (sc.hardHit() || sc.minClearance() < pilot.profile().searchEnterClearance()) {
				var sr = GROUND_SEARCH.search(snap, state, pilot.profile());
				desired = sr.firstStep().lengthSqr() > 1e-10 ? sr.firstStep() : pilot.tick(snap, state);
			} else {
				desired = pilot.tick(snap, state);
				// Flatten unwanted vertical from free-flight APF when grounded
				if (player.onGround() && desired.y < 0) {
					desired = new Vec3(desired.x, 0, desired.z);
				}
			}
		}
		applyVelocity(player, desired, true);
	}

	private static Vec3 computeRescuePulse(LocalPlayer player, ThreatSnapshot snap, PilotState state, boolean flying) {
		if (flying) {
			return PILOT_I.tick(snap, state);
		}
		var sr = GROUND_SEARCH.search(snap, state, PilotProfile.NOVICE);
		if (sr.firstStep().lengthSqr() > 1e-10) return sr.firstStep();
		// Fallback: horizontal away from nearest threat + optional jump
		Vec3 away = Vec3.ZERO;
		if (!snap.threats().isEmpty() && snap.threats().get(0).frames().length > 0) {
			Vec3 tpos = snap.threats().get(0).frames()[0].position();
			away = player.position().subtract(tpos);
			away = new Vec3(away.x, 0, away.z);
			if (away.lengthSqr() > 1e-8) away = away.normalize().scale(PilotProfile.NOVICE.highSpeed());
		}
		if (away.lengthSqr() < 1e-8) {
			// Strafe relative to look
			Vec3 look = player.getLookAngle();
			away = new Vec3(-look.z, 0, look.x).normalize().scale(PilotProfile.NOVICE.highSpeed());
		}
		return away.add(0, 0.12, 0); // small hop
	}

	private static void applyVelocity(LocalPlayer player, Vec3 desired, boolean replace) {
		if (desired.lengthSqr() < 1e-10) return;
		// Terrain: don't push into wall
		AABB next = player.getBoundingBox().move(desired.x, Math.max(0, desired.y), desired.z);
		if (!player.level().noCollision(player, next)) {
			// Try horizontal only
			Vec3 h = new Vec3(desired.x, 0, desired.z);
			next = player.getBoundingBox().move(h.x, 0, h.z);
			if (!player.level().noCollision(player, next)) return;
			desired = h;
		}
		if (replace) {
			// Preserve some vertical if jumping/falling unless desired specifies
			Vec3 cur = player.getDeltaMovement();
			double y = desired.y != 0 ? desired.y : cur.y;
			player.setDeltaMovement(desired.x, y, desired.z);
		} else {
			player.setDeltaMovement(desired);
		}
		player.hasImpulse = true;
	}

	private static Vec3 readInputWish(LocalPlayer player) {
		float fwd = player.input.forwardImpulse;
		float str = player.input.leftImpulse;
		if (Math.abs(fwd) < 1e-4 && Math.abs(str) < 1e-4) return Vec3.ZERO;
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		if (flat.lengthSqr() < 1e-8) flat = new Vec3(0, 0, 1);
		flat = flat.normalize();
		Vec3 right = new Vec3(-flat.z, 0, flat.x);
		return flat.scale(fwd).add(right.scale(str));
	}

	private static List<Entity> collectThreats(LocalPlayer player, Entity extra) {
		AABB area = player.getBoundingBox().inflate(SCAN_RADIUS);
		List<Entity> list = new ArrayList<>();
		for (Entity e : player.level().getEntities(player, area, AutoDodgeClientHandlers::isThreat)) {
			list.add(e);
		}
		if (extra != null && extra.isAlive() && isThreat(extra) && !list.contains(extra)) {
			list.add(extra);
		}
		return list;
	}

	private static boolean isThreat(Entity e) {
		if (e instanceof Projectile) return true;
		return e instanceof dev.xkmc.fastprojectileapi.entity.SimplifiedProjectile;
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
	}
}
