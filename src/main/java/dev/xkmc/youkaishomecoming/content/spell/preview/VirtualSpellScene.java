package dev.xkmc.youkaishomecoming.content.spell.preview;

import dev.xkmc.youkaishomecoming.content.capability.GrazeHelper;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.difficulty.DifficultyModifiers;
import dev.xkmc.youkaishomecoming.content.spell.pilot.DodgePilot;
import dev.xkmc.youkaishomecoming.content.spell.pilot.PilotProfile;
import dev.xkmc.youkaishomecoming.content.spell.pilot.PilotState;
import dev.xkmc.youkaishomecoming.content.spell.pilot.debug.PilotDebugView;
import dev.xkmc.youkaishomecoming.content.spell.pilot.predict.BallisticProvider;
import dev.xkmc.youkaishomecoming.content.spell.pilot.predict.MoverExactProvider;
import dev.xkmc.youkaishomecoming.content.spell.pilot.predict.ObservedMotionProvider;
import dev.xkmc.youkaishomecoming.content.spell.pilot.predict.ThreatProviderRegistry;
import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.CollisionOracle;
import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.SelfBoxModel;
import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.ThreatFilters;
import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.ThreatSnapshot;
import net.minecraft.world.entity.Entity;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.ItemDanmakuEntity;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.Map;
import java.util.Set;

/**
 * Manages a virtual spell preview scene: drives SpellRuntime,
 * manages the entity pool, and provides playback controls.
 */
public class VirtualSpellScene {

	private SpellDefinition definition;
	private SpellRuntime runtime;
	private final PreviewCardHolder holder;

	private boolean playing = false;
	private Runnable beforeTimelineAdvance;
	private int speedIndex = 2; // index into SPEED_OPTIONS
	private float targetDistance = 10f;
	private float healthRatio = 1.0f;
	private Runnable onStateChanged;

	/** Duration of the last tick() call in nanoseconds. */
	private long lastTickNanos = 0;

	// --- AI pilot (aligned with player AUTO_DODGE amp 0/1/2) ---
	private static final Logger LOGGER = LogUtils.getLogger();
	/** 0 = rescue, 1 = assist, 2 = takeover (buff amp mapping). */
	public static final int PILOT_TIER_RESCUE = 0;
	public static final int PILOT_TIER_ASSIST = 1;
	public static final int PILOT_TIER_TAKEOVER = 2;
	private boolean pilotEnabled = false;
	private int pilotTier = PILOT_TIER_ASSIST;
	private int pilotRescueCooldown = 0;
	private final ThreatProviderRegistry pilotRegistry = new ThreatProviderRegistry();
	private final ObservedMotionProvider observedProvider = new ObservedMotionProvider();
	private DodgePilot pilot = new DodgePilot(PilotProfile.ADEPT);
	private long pilotProfileFingerprint = Long.MIN_VALUE;
	private long lastPilotNanos = 0;
	private boolean pilotDebugOverlay = true;

	public static final float[] SPEED_OPTIONS = {0.25f, 0.5f, 1.0f, 2.0f, 4.0f};
	public static final float[] DISTANCE_OPTIONS = {5f, 10f, 15f, 20f};
	public static final float[] HP_OPTIONS = {1.0f, 0.75f, 0.5f, 0.25f};

	public VirtualSpellScene(SpellDefinition definition) {
		Level level = Minecraft.getInstance().level;
		if (level == null) throw new IllegalStateException("Cannot create preview without a loaded world");
		this.definition = definition;
		this.runtime = new SpellRuntime(definition);
		bindRuntime(this.runtime);
		this.holder = new PreviewCardHolder(level);
		this.holder.setRuntimeSupplier(() -> this.runtime);
		if (Minecraft.getInstance().player != null) {
			this.holder.setCasterPower(GrazeHelper.getEffectivePowerLevel(Minecraft.getInstance().player));
		}
		this.holder.setOnSpellSwitch(this::switchSpellDefinition);
		this.holder.setOnPhaseSwitch(this::switchPreviewPhase);
		// Wire hit callback: when a danmaku hits the target AABB, notify runtime
		// Use mobAttack(fakeCaster) so source.getEntity() instanceof LivingEntity passes
		this.holder.setOnTargetHit(() -> {
			var ds = level.damageSources().mobAttack(holder.getFakeCaster());
			runtime.hurt(holder, ds, 2.0f);
		});
		// Preview providers: T1 exact → T2 ballistic → T3 observation
		pilotRegistry.register(new MoverExactProvider());
		pilotRegistry.register(new BallisticProvider());
		pilotRegistry.register(observedProvider);
		pilot.debugView().enabled = pilotDebugOverlay;
	}

	public void tick() {
		if (!playing) {
			lastTickNanos = 0;
			return;
		}

		long t0 = System.nanoTime();
		float speed = SPEED_OPTIONS[speedIndex];
		if (speed >= 1.0f) {
			int ticks = (int) speed;
			for (int i = 0; i < ticks; i++) {
				doTick();
			}
		} else {
			// Slow motion: skip frames
			// At 0.5x, tick every 2nd call; at 0.25x, every 4th
			int interval = Math.round(1.0f / speed);
			if (runtime.getTotalTick() % interval == 0) {
				doTick();
			}
		}
		lastTickNanos = System.nanoTime() - t0;
	}

	/** Get the duration of the last tick() call in nanoseconds. */
	public long getLastTickNanos() {
		return lastTickNanos;
	}

	private void doTick() {
		holder.setCasterHealth(healthRatio);
		if (pilotEnabled) {
			runPilotStep();
		}
		runtime.tick(holder);
		holder.tick();
		// Safety: auto-pause if entity count exceeds limit
		if (holder.isSafetyTripped()) {
			playing = false;
		}
	}

	private void runPilotStep() {
		long t0 = System.nanoTime();
		refreshPilotProfileIfNeeded();
		var config = YHModConfig.COMMON;
		if (pilotRescueCooldown > 0) pilotRescueCooldown--;

		Vec3 feet = holder.getTargetPos();
		Vec3 curVel = holder.targetVelocity() == null ? Vec3.ZERO : holder.targetVelocity();
		int horizon = pilot.profile().predictHorizon();
		int topK = pilot.profile().threatTopK();
		// Self = preview target; exclude target-owned / zero-damage (caster bullets remain hostile)
		Entity self = holder.getFakeTarget();
		java.util.List<Entity> hostile = new java.util.ArrayList<>();
		for (Entity e : holder.getLocalEntities()) {
			if (ThreatFilters.isHostileTo(self, e)) hostile.add(e);
		}
		ThreatSnapshot snap = ThreatSnapshot.capture(hostile, pilotRegistry, horizon, topK, feet);
		PilotState state = new PilotState(feet, curVel, SelfBoxModel.previewTarget());
		state.oracle = CollisionOracle.ALWAYS_FREE;
		state.anchor = new Vec3(0, feet.y, -targetDistance);
		double h = config.previewPilotArenaHalf.get();
		state.arena = new AABB(-h, feet.y - h, -h - targetDistance, h, feet.y + h, h - targetDistance);
		state.tick = runtime.getTotalTick();

		Vec3 vel = Vec3.ZERO;
		int tier = Math.max(0, Math.min(2, pilotTier));
		if (tier == PILOT_TIER_RESCUE) {
			// I: only move when clearance is critical (buff amp 0)
			var sc = pilot.scorer().score(snap, state.selfBox, feet, curVel, 0);
			boolean danger = sc.hardHit() || sc.minClearance() <= config.autoDodgeRescueClearance.get();
			if (danger && pilotRescueCooldown <= 0) {
				vel = pilot.tick(snap, state);
				if (vel.lengthSqr() < 1e-6 && snap.size() > 0) {
					// Fallback lateral kick
					var th = snap.threats().get(0);
					if (th.frames().length > 0) {
						Vec3 away = feet.subtract(th.frames()[0].position());
						away = new Vec3(away.x, away.y, away.z);
						if (away.lengthSqr() > 1e-8) {
							vel = away.normalize().scale(pilot.profile().highSpeed());
						}
					}
				}
				pilotRescueCooldown = config.autoDodgeEmergencyCooldown.get();
			}
		} else if (tier == PILOT_TIER_ASSIST) {
			// II: soft APF blend — full pilot output scaled down (buff amp 1)
			Vec3 full = pilot.tick(snap, state);
			vel = curVel.scale(config.autoDodgeAssistCurrentWeight.get())
					.add(full.scale(config.autoDodgeAssistPilotWeight.get()));
			double cap = config.autoDodgeAssistSpeedCap.get();
			if (vel.length() > cap) {
				vel = vel.normalize().scale(cap);
			}
		} else {
			// III: full takeover (buff amp 2)
			vel = pilot.tick(snap, state);
		}

		Vec3 next = feet.add(vel);
		if (state.arena != null) {
			next = new Vec3(
					Math.max(state.arena.minX, Math.min(state.arena.maxX, next.x)),
					Math.max(state.arena.minY, Math.min(state.arena.maxY, next.y)),
					Math.max(state.arena.minZ, Math.min(state.arena.maxZ, next.z))
			);
			vel = next.subtract(feet);
		}
		holder.setTargetPosAndVelocity(next, vel);
		lastPilotNanos = System.nanoTime() - t0;
	}

	public boolean isPilotEnabled() {
		return pilotEnabled;
	}

	public void setPilotEnabled(boolean enabled) {
		this.pilotEnabled = enabled;
		if (!enabled) {
			pilot.reset();
			pilotRescueCooldown = 0;
			holder.setTargetVelocity(Vec3.ZERO);
		}
	}

	public void togglePilot() {
		setPilotEnabled(!pilotEnabled);
	}

	/** 0 = I rescue, 1 = II assist, 2 = III takeover. Enables pilot. */
	public void setPilotTier(int tier) {
		this.pilotTier = Math.max(0, Math.min(2, tier));
		this.pilotEnabled = true;
		this.pilotRescueCooldown = 0;
		this.pilotProfileFingerprint = Long.MIN_VALUE;
		refreshPilotProfileIfNeeded();
	}

	public int getPilotTier() {
		return pilotTier;
	}

	public String getPilotTierLabel() {
		if (!pilotEnabled) return "AI:OFF";
		return switch (pilotTier) {
			case PILOT_TIER_RESCUE -> "AI:I";
			case PILOT_TIER_ASSIST -> "AI:II";
			default -> "AI:III";
		};
	}

	public String getPilotTierName() {
		return switch (pilotTier) {
			case PILOT_TIER_RESCUE -> "I Rescue";
			case PILOT_TIER_ASSIST -> "II Assist";
			default -> "III Takeover";
		};
	}

	private void refreshPilotProfileIfNeeded() {
		var config = YHModConfig.COMMON;
		double high = switch (pilotTier) {
			case PILOT_TIER_RESCUE -> config.autoDodgeTierIHighSpeed.get();
			case PILOT_TIER_ASSIST -> config.autoDodgeTierIIHighSpeed.get();
			default -> config.autoDodgeTierIIIHighSpeed.get();
		};
		double low = switch (pilotTier) {
			case PILOT_TIER_RESCUE -> config.autoDodgeTierILowSpeed.get();
			case PILOT_TIER_ASSIST -> config.autoDodgeTierIILowSpeed.get();
			default -> config.autoDodgeTierIIILowSpeed.get();
		};
		long fingerprint = pilotTier
				^ Double.doubleToLongBits(high) * 31
				^ Double.doubleToLongBits(low) * 37
				^ ((long) config.autoDodgeThreatTopK.get() << 16)
				^ config.autoDodgePredictHorizon.get();
		if (fingerprint == pilotProfileFingerprint) return;
		pilotProfileFingerprint = fingerprint;
		PilotProfile base = switch (pilotTier) {
			case PILOT_TIER_RESCUE -> PilotProfile.NOVICE;
			case PILOT_TIER_ASSIST -> PilotProfile.ADEPT;
			default -> PilotProfile.LUNATIC;
		};
		setPilotProfile(base.withMotion(high, low,
				config.autoDodgeThreatTopK.get(), config.autoDodgePredictHorizon.get()));
	}

	public long getLastPilotNanos() {
		return lastPilotNanos;
	}

	public DodgePilot getPilot() {
		return pilot;
	}

	public void setPilotProfile(PilotProfile profile) {
		boolean dbg = pilotDebugOverlay;
		this.pilot = new DodgePilot(profile);
		this.pilot.debugView().enabled = dbg;
	}

	public boolean isPilotDebugOverlay() {
		return pilotDebugOverlay;
	}

	public void setPilotDebugOverlay(boolean enabled) {
		this.pilotDebugOverlay = enabled;
		pilot.debugView().enabled = enabled;
		if (!enabled) {
			pilot.debugView().clear();
		}
	}

	public void togglePilotDebugOverlay() {
		setPilotDebugOverlay(!pilotDebugOverlay);
	}

	public PilotDebugView getPilotDebugView() {
		return pilot.debugView();
	}

	public void play() {
		beforeTimelineAdvance();
		playing = true;
	}

	public void pause() {
		playing = false;
	}

	public void togglePlayPause() {
		if (playing) {
			playing = false;
		} else {
			beforeTimelineAdvance();
			playing = true;
		}
	}

	public void step() {
		beforeTimelineAdvance();
		playing = false;
		doTick();
	}

	public void setBeforeTimelineAdvance(Runnable callback) {
		this.beforeTimelineAdvance = callback;
	}

	private void beforeTimelineAdvance() {
		if (beforeTimelineAdvance != null) beforeTimelineAdvance.run();
	}

	public void reset() {
		playing = false;
		runtime.setPhasePreviewLock(null);
		runtime.reset();
		holder.clear();
		holder.clearYsmRenderOverride();
		pilot.reset();
		observedProvider.clear();
		lastPilotNanos = 0;
		notifyStateChanged();
	}

	public void resetToPhase(ResourceLocation phaseId) {
		playing = false;
		holder.clear();
		holder.clearYsmRenderOverride();
		holder.setCasterHealth(healthRatio);
		runtime.setPhasePreviewLock(phaseId);
		DifficultyModifiers diff = definition.difficulty.resolve(healthRatio);
		SpellContext ctx = new SpellContext(holder, definition, runtime, diff);
		runtime.restartAtPhase(ctx, phaseId);
	}

	public boolean isPlaying() {
		return playing;
	}

	// Speed control

	public int getSpeedIndex() {
		return speedIndex;
	}

	public void setSpeedIndex(int index) {
		this.speedIndex = Math.max(0, Math.min(index, SPEED_OPTIONS.length - 1));
	}

	public float getCurrentSpeed() {
		return SPEED_OPTIONS[speedIndex];
	}

	// Target distance

	public float getTargetDistance() {
		return targetDistance;
	}

	public void setTargetDistance(float distance) {
		this.targetDistance = distance;
		holder.setTargetDistance(distance);
	}

	public Vec3 getCasterPos() {
		return holder.getFakeCaster().position();
	}

	public void setCasterPos(Vec3 pos) {
		holder.getFakeCaster().setPos(pos);
	}

	public void moveCaster(Vec3 delta) {
		Vec3 current = holder.getFakeCaster().position();
		holder.getFakeCaster().setPos(current.add(delta));
	}

	public void resetCasterPos() {
		holder.getFakeCaster().setPos(0, 0, 0);
	}

	public void resetTargetPos() {
		holder.setTargetPos(new Vec3(0, 0, -10));
	}

	public void moveTarget(Vec3 delta) {
		Vec3 current = holder.getTargetPos();
		holder.setTargetPos(current.add(delta));
	}

	public Vec3 getTargetPos() {
		return holder.getTargetPos();
	}

	public void setTargetPos(Vec3 pos) {
		holder.setTargetPos(pos);
	}

	public void setTargetFacing(Vec3 facing) {
		holder.setTargetFacing(facing);
	}

	public Vec3 getTargetFacing() {
		return holder.getTargetFacing();
	}

	/** Set only the Y coordinate of the target position (target_height). */
	public void setTargetHeight(double y) {
		Vec3 current = holder.getTargetPos();
		holder.setTargetPos(new Vec3(current.x, y, current.z));
	}

	/** Get the target's Y coordinate (target_height). */
	public double getTargetHeight() {
		return holder.getTargetPos().y;
	}

	// Target properties

	public void setTargetOnGround(boolean v) { holder.setTargetOnGround(v); }
	public boolean isTargetOnGround() { return holder.isTargetOnGround(); }

	public void setTargetHealthRatio(float v) { holder.setTargetHealthRatio(v); }
	public float getTargetHealthRatio() { return holder.getTargetHealthRatio(); }

	public void setTargetFlying(boolean v) { holder.setTargetFlying(v); }
	public boolean isTargetFlying() { return holder.isTargetFlying(); }

	public void setTargetFallFlying(boolean v) { holder.setTargetFallFlying(v); }
	public boolean isTargetFallFlying() { return holder.isTargetFallFlying(); }
	public void setCasterPower(double v) { holder.setCasterPower(v); }
	public double getCasterPower() { return holder.casterPower(); }

	public void setTargetBoxSize(Vec3 size) { holder.setTargetBoxSize(size); }
	public Vec3 getTargetBoxSize() { return holder.getTargetBoxSize(); }
	public void setBlockTargetPos(Vec3 pos) { holder.setBlockTargetPos(pos); }
	public Vec3 getBlockTargetPos() { return holder.getBlockTargetPos(); }
	public Vec3 getBlockTargetHandlePos() {
		AABB box = holder.getBlockTargetCollisionBox();
		return new Vec3((box.minX + box.maxX) * 0.5, box.maxY, (box.minZ + box.maxZ) * 0.5);
	}

	public void moveBlockTarget(Vec3 delta) {
		holder.setBlockTargetPos(holder.getBlockTargetPos().add(delta));
	}

	public void resetBlockTargetPos() { holder.setBlockTargetPos(new Vec3(0, -32, 0)); }

	public AABB getEntityTargetCollisionBox() { return holder.getEntityTargetCollisionBox(); }
	public AABB getBlockTargetCollisionBox() { return holder.getBlockTargetCollisionBox(); }

	// Health ratio

	public float getHealthRatio() {
		return healthRatio;
	}

	public void setHealthRatio(float ratio) {
		this.healthRatio = Math.max(0.01f, Math.min(1.0f, ratio));
		holder.setCasterHealth(healthRatio);
	}

	// Phase control

	public void forcePhase(ResourceLocation phaseId) {
		resetToPhase(phaseId);
	}

	public void setOnStateChanged(Runnable callback) {
		this.onStateChanged = callback;
	}

	private void bindRuntime(SpellRuntime runtime) {
		runtime.setOnPhaseChange(ignored -> notifyStateChanged());
	}

	private void notifyStateChanged() {
		if (onStateChanged != null) {
			onStateChanged.run();
		}
	}

	public void switchPreviewPhase(ResourceLocation phaseId, boolean clearScreen) {
		if (definition.getPhase(phaseId) == null) {
			return;
		}
		DifficultyModifiers diff = definition.difficulty.resolve(healthRatio);
		SpellContext ctx = new SpellContext(holder, definition, runtime, diff);
		runtime.setPhasePreviewLock(null);
		runtime.forceTransition(ctx, phaseId, clearScreen);
		runtime.setPhasePreviewLock(phaseId);
	}

	public void switchSpellDefinition(SpellDefinition definition, boolean clearScreen) {
		if (clearScreen) {
			holder.clear();
			holder.clearYsmRenderOverride();
		}
		this.definition = definition;
		this.runtime = new SpellRuntime(definition);
		bindRuntime(this.runtime);
		this.runtime.setPhasePreviewLock(definition.entryPhase);
		notifyStateChanged();
	}

	// Accessors

	public SpellDefinition getDefinition() {
		return definition;
	}

	public SpellRuntime getRuntime() {
		return runtime;
	}

	public PreviewCardHolder getHolder() {
		return holder;
	}

	/** Move only the already-rendered projectiles emitted by one action. This is
	 * used by paused origin dragging so the viewport updates without replaying
	 * the whole spell on every mouse event. */
	public void translateActionProjectiles(int actionIndex, Vec3 delta) {
		if (delta == null || delta.lengthSqr() < 1.0e-12) return;
		for (Entity entity : holder.getLocalEntities()) {
			int source = entity instanceof ItemDanmakuEntity ide ? ide.sourceActionIndex
					: entity instanceof dev.xkmc.youkaishomecoming.content.entity.danmaku.YHBaseLaserEntity laser
					? laser.sourceActionIndex : -1;
			if (source != actionIndex) continue;
			entity.setPos(entity.position().add(delta));
			entity.setOldPosAndRot();
		}
	}

	public void rotateActionProjectiles(int actionIndex, Vec3 pivot, Vec3 axis, double degrees) {
		if (actionIndex < 0 || pivot == null || axis == null || axis.lengthSqr() < 1.0e-12) return;
		double radians = Math.toRadians(degrees);
		for (Entity entity : holder.getLocalEntities()) {
			int source = entity instanceof ItemDanmakuEntity ide ? ide.sourceActionIndex
					: entity instanceof dev.xkmc.youkaishomecoming.content.entity.danmaku.YHBaseLaserEntity laser
					? laser.sourceActionIndex : -1;
			if (source != actionIndex) continue;
			Vec3 position = pivot.add(rotateAroundAxis(entity.position().subtract(pivot), axis, radians));
			Vec3 velocity = rotateAroundAxis(entity.getDeltaMovement(), axis, radians);
			entity.setPos(position);
			entity.setDeltaMovement(velocity);
			Vec3 facing = entity instanceof dev.xkmc.youkaishomecoming.content.entity.danmaku.YHBaseLaserEntity laser
					? rotateAroundAxis(laser.getForward(), axis, radians) : velocity;
			if (facing.lengthSqr() > 1.0e-12) {
				var rotation = dev.xkmc.fastprojectileapi.entity.ProjectileMovement.of(facing).rot();
				float pitch = (float) Math.toDegrees(rotation.x);
				float yaw = (float) Math.toDegrees(rotation.y);
				entity.setXRot(pitch);
				entity.setYRot(yaw);
				entity.xRotO = pitch;
				entity.yRotO = yaw;
			}
			entity.setOldPosAndRot();
		}
	}

	private static Vec3 rotateAroundAxis(Vec3 value, Vec3 axis, double angle) {
		Vec3 normalized = axis.normalize();
		double cos = Math.cos(angle);
		double sin = Math.sin(angle);
		return value.scale(cos)
				.add(normalized.cross(value).scale(sin))
				.add(normalized.scale(normalized.dot(value) * (1.0 - cos)));
	}

	/** Transient context for paused editor transform evaluation. */
	public SpellContext previewContext() {
		return new SpellContext(holder, definition, runtime,
				definition.difficulty.resolve(1.0f));
	}

	public ResourceLocation getCurrentPhaseId() {
		return runtime.getCurrentPhaseId();
	}

	public int getTotalTick() {
		return runtime.getTotalTick();
	}

	public int getPhaseTick() {
		return runtime.getPhaseTick();
	}

	public int getEntityCount() {
		return holder.getEntityCount();
	}

	public boolean isSafetyTripped() {
		return holder.isSafetyTripped();
	}

	public int getHitCount() {
		return runtime.getHitCount();
	}

	public Map<String, Double> getVariables() {
		return runtime.getVariables();
	}

	public Set<ResourceLocation> getPhaseIds() {
		return definition.phases.keySet();
	}
}
