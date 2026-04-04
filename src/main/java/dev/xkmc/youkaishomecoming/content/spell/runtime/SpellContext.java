package dev.xkmc.youkaishomecoming.content.spell.runtime;

import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.difficulty.DifficultyModifiers;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.CardHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class SpellContext {

	private final CardHolder holder;
	private final SpellDefinition definition;
	private final SpellRuntime runtime;
	private final DifficultyModifiers difficulty;

	public SpellContext(CardHolder holder, SpellDefinition definition,
						SpellRuntime runtime, DifficultyModifiers difficulty) {
		this.holder = holder;
		this.definition = definition;
		this.runtime = runtime;
		this.difficulty = difficulty;
	}

	public CardHolder holder() {
		return holder;
	}

	public SpellDefinition definition() {
		return definition;
	}

	public SpellRuntime runtime() {
		return runtime;
	}

	public DifficultyModifiers difficulty() {
		return difficulty;
	}

	public LivingEntity self() {
		return holder.self();
	}

	public int phaseTick() {
		return runtime.getPhaseTick();
	}

	public int totalTick() {
		return runtime.getTotalTick();
	}

	public ResourceLocation currentPhaseId() {
		return runtime.getCurrentPhaseId();
	}

	public float healthRatio() {
		var self = holder.self();
		return self.getHealth() / self.getMaxHealth();
	}

	public double distanceToTarget() {
		var target = holder.target();
		if (target == null) return Double.MAX_VALUE;
		return holder.center().distanceTo(target);
	}

	public double getVariable(String key) {
		return runtime.getVariable(key);
	}

	public void setVariable(String key, double value) {
		runtime.setVariable(key, value);
	}

	public void clearDanmaku() {
		var self = holder.self();
		if (self instanceof dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity youkai) {
			youkai.eraseAllDanmaku(null);
		}
	}

	public int hitCount() {
		return runtime.getHitCount();
	}

	public Map<String, Double> variables() {
		return runtime.getVariables();
	}

	/**
	 * Returns true if the target entity is on the ground.
	 * Returns false if there is no target.
	 */
	public boolean targetOnGround() {
		var target = holder.targetEntity();
		return target != null && target.onGround();
	}

	/**
	 * Returns the horizontal speed of the target entity.
	 * Returns 0 if there is no target or no velocity data.
	 */
	public double targetSpeed() {
		var vel = holder.targetVelocity();
		if (vel == null) return 0;
		return vel.horizontalDistance();
	}
}
