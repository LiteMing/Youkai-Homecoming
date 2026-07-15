package dev.xkmc.youkaishomecoming.content.spell.preview;

import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.difficulty.DifficultyModifiers;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import net.minecraft.world.phys.Vec3;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

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
	private int speedIndex = 2; // index into SPEED_OPTIONS
	private float targetDistance = 10f;
	private float healthRatio = 1.0f;
	private Runnable onStateChanged;
	private boolean ysmPreviewCasterEnabled = false;

	/** Duration of the last tick() call in nanoseconds. */
	private long lastTickNanos = 0;

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
		this.holder.setOnSpellSwitch(this::switchSpellDefinition);
		this.holder.setOnPhaseSwitch(this::switchPreviewPhase);
		// Wire hit callback: when a danmaku hits the target AABB, notify runtime
		// Use mobAttack(fakeCaster) so source.getEntity() instanceof LivingEntity passes
		this.holder.setOnTargetHit(() -> {
			var ds = level.damageSources().mobAttack(holder.getFakeCaster());
			runtime.hurt(holder, ds, 2.0f);
		});
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
		runtime.tick(holder);
		holder.tick();
		// Safety: auto-pause if entity count exceeds limit
		if (holder.isSafetyTripped()) {
			playing = false;
		}
	}

	public void play() {
		playing = true;
	}

	public void pause() {
		playing = false;
	}

	public void togglePlayPause() {
		playing = !playing;
	}

	public void step() {
		playing = false;
		doTick();
	}

	public void reset() {
		playing = false;
		runtime.setPhasePreviewLock(null);
		runtime.reset();
		holder.clear();
		holder.clearYsmRenderOverride();
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

	public boolean isYsmPreviewCasterEnabled() {
		return ysmPreviewCasterEnabled;
	}

	public void setYsmPreviewCasterEnabled(boolean enabled) {
		this.ysmPreviewCasterEnabled = enabled;
	}

	public String describeYsmPreviewCaster() {
		if (!ysmPreviewCasterEnabled) {
			return "disabled";
		}
		return holder.hasYsmRenderOverride() ? holder.describeYsmRenderOverride() : "default yh/remilia";
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
