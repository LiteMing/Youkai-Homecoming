package dev.xkmc.youkaishomecoming.content.spell.runtime;

import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.difficulty.DifficultyModifiers;
import dev.xkmc.youkaishomecoming.content.spell.item.PlayerHolder;
import dev.xkmc.youkaishomecoming.content.spell.item.RuntimeItemSpell;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.CardHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class SpellContext {

	private final CardHolder holder;
	@Nullable
	private final SpellRuntimeHost host;
	private final SpellDefinition definition;
	private final SpellRuntime runtime;
	private final DifficultyModifiers difficulty;

	public SpellContext(CardHolder holder, SpellDefinition definition,
						SpellRuntime runtime, DifficultyModifiers difficulty) {
		this.holder = holder;
		this.host = holder instanceof SpellRuntimeHost spellHost ? spellHost : null;
		this.definition = definition;
		this.runtime = runtime;
		this.difficulty = difficulty;
	}

	public CardHolder holder() {
		return holder;
	}

	@Nullable
	public SpellRuntimeHost host() {
		return host;
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

	public void setMovementDirective(SpellMovementDirective directive) {
		runtime.setMovementDirective(directive);
	}

	public void clearDanmaku() {
		if (holder instanceof dev.xkmc.youkaishomecoming.content.spell.preview.PreviewCardHolder preview) {
			preview.clear();
			return;
		}
		if (holder instanceof PlayerHolder playerHolder && playerHolder.spell() instanceof RuntimeItemSpell runtimeItemSpell) {
			runtimeItemSpell.clearDanmaku();
			return;
		}
		if (host != null) {
			host.eraseDanmaku(null);
		}
	}

	public boolean switchSpell(ResourceLocation spellId, boolean clearScreen) {
		var def = SpellRegistry.get(spellId);
		if (def == null) {
			return false;
		}
		if (holder instanceof dev.xkmc.youkaishomecoming.content.spell.preview.PreviewCardHolder preview) {
			return preview.switchSpell(def, clearScreen);
		}
		if (holder instanceof PlayerHolder playerHolder && playerHolder.spell() instanceof RuntimeItemSpell runtimeItemSpell) {
			runtimeItemSpell.switchSpell(def, clearScreen);
			return true;
		}
		if (host != null) {
			host.switchSpellDefinition(def, clearScreen);
			return true;
		}
		return false;
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
		if (holder instanceof dev.xkmc.youkaishomecoming.content.spell.preview.PreviewCardHolder preview) {
			return preview.isTargetOnGround();
		}
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

	/**
	 * Returns the health ratio of the target entity (0.0 ~ 1.0).
	 * Returns 1.0 if there is no target.
	 */
	public float targetHealthRatio() {
		if (holder instanceof dev.xkmc.youkaishomecoming.content.spell.preview.PreviewCardHolder preview) {
			return preview.getTargetHealthRatio();
		}
		var target = holder.targetEntity();
		if (target == null) return 1.0f;
		return target.getHealth() / target.getMaxHealth();
	}

	/**
	 * Returns true if the target entity is flying (abilities.flying).
	 */
	public boolean targetIsFlying() {
		if (holder instanceof dev.xkmc.youkaishomecoming.content.spell.preview.PreviewCardHolder preview) {
			return preview.isTargetFlying();
		}
		var target = holder.targetEntity();
		if (target == null) return false;
		if (target instanceof net.minecraft.world.entity.player.Player p) {
			return p.getAbilities().flying;
		}
		return !target.onGround() && target.fallDistance == 0;
	}

	/**
	 * Returns how many consecutive ticks the target has been off the ground.
	 * Tracked by {@link SpellRuntime} each tick for accuracy.
	 * Returns 0 if there is no target or the target is on the ground.
	 */
	public int targetFlyTime() {
		return runtime.getTargetFlyTime();
	}

	/**
	 * Returns true if the target entity is elytra gliding.
	 */
	public boolean targetIsFallFlying() {
		if (holder instanceof dev.xkmc.youkaishomecoming.content.spell.preview.PreviewCardHolder preview) {
			return preview.isTargetFallFlying();
		}
		var target = holder.targetEntity();
		return target != null && target.isFallFlying();
	}
}
