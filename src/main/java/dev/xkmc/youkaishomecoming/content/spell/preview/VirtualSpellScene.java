package dev.xkmc.youkaishomecoming.content.spell.preview;

import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.difficulty.DifficultyModifiers;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import net.minecraft.world.phys.Vec3;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Set;

/**
 * Manages a virtual spell preview scene: drives SpellRuntime,
 * manages the entity pool, and provides playback controls.
 */
public class VirtualSpellScene {

	private final SpellDefinition definition;
	private final SpellRuntime runtime;
	private final PreviewCardHolder holder;

	private boolean playing = false;
	private int speedIndex = 2; // index into SPEED_OPTIONS
	private float targetDistance = 10f;
	private float healthRatio = 1.0f;

	public static final float[] SPEED_OPTIONS = {0.25f, 0.5f, 1.0f, 2.0f, 4.0f};
	public static final float[] DISTANCE_OPTIONS = {5f, 10f, 15f, 20f};
	public static final float[] HP_OPTIONS = {1.0f, 0.75f, 0.5f, 0.25f};

	public VirtualSpellScene(SpellDefinition definition) {
		Level level = Minecraft.getInstance().level;
		if (level == null) throw new IllegalStateException("Cannot create preview without a loaded world");
		this.definition = definition;
		this.runtime = new SpellRuntime(definition);
		this.holder = new PreviewCardHolder(level);
	}

	public void tick() {
		if (!playing) return;

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
	}

	private void doTick() {
		holder.setCasterHealth(healthRatio);
		runtime.tick(holder);
		holder.tick();
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
		runtime.reset();
		holder.clear();
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

	public void moveTarget(Vec3 delta) {
		Vec3 current = holder.getTargetPos();
		holder.setTargetPos(current.add(delta));
	}

	public Vec3 getTargetPos() {
		return holder.getTargetPos();
	}

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
		DifficultyModifiers diff = definition.difficulty.resolve(healthRatio);
		SpellContext ctx = new SpellContext(holder, definition, runtime, diff);
		runtime.forceTransition(ctx, phaseId);
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

	public Set<ResourceLocation> getPhaseIds() {
		return definition.phases.keySet();
	}
}
