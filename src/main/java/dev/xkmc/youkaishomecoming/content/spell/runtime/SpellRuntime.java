package dev.xkmc.youkaishomecoming.content.spell.runtime;

import dev.xkmc.youkaishomecoming.content.spell.action.LegacyTickerAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellAction;
import dev.xkmc.youkaishomecoming.content.spell.definition.PhaseDefinition;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.definition.Transition;
import dev.xkmc.youkaishomecoming.content.spell.definition.TransitionMode;
import dev.xkmc.youkaishomecoming.content.spell.difficulty.DifficultyModifiers;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.CardHolder;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.SpellCard;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Runtime state machine for a spell card definition.
 * Holds mutable state: current phase, tick counters, variables.
 * Does NOT extend SpellCard — it replaces the old runtime model.
 */
public class SpellRuntime {

	private final SpellDefinition definition;

	private ResourceLocation currentPhaseId;
	private int phaseTick;
	private int totalTick;
	private int hitCount;
	private final Map<String, Double> variables = new HashMap<>();
	private final Map<Object, Object> actionState = new IdentityHashMap<>();

	@Nullable
	private Consumer<SpellRuntime> onPhaseChange;

	public SpellRuntime(SpellDefinition definition) {
		this.definition = definition;
		this.currentPhaseId = definition.entryPhase;
	}

	public SpellDefinition getDefinition() {
		return definition;
	}

	public ResourceLocation getCurrentPhaseId() {
		return currentPhaseId;
	}

	public int getPhaseTick() {
		return phaseTick;
	}

	public int getTotalTick() {
		return totalTick;
	}

	public int getHitCount() {
		return hitCount;
	}

	public double getVariable(String key) {
		return variables.getOrDefault(key, 0.0);
	}

	public void setVariable(String key, double value) {
		variables.put(key, value);
	}

	public void removeVariable(String key) {
		variables.remove(key);
	}

	public Map<String, Double> getVariables() {
		return Collections.unmodifiableMap(variables);
	}

	public void setOnPhaseChange(@Nullable Consumer<SpellRuntime> listener) {
		this.onPhaseChange = listener;
	}

	public void tick(CardHolder holder) {
		PhaseDefinition phase = definition.getPhase(currentPhaseId);
		if (phase == null) return;

		float healthRatio = holder.self().getHealth() / holder.self().getMaxHealth();
		DifficultyModifiers diff = definition.difficulty.resolve(healthRatio);
		SpellContext ctx = new SpellContext(holder, definition, this, diff);

		// Execute tick actions
		for (SpellAction action : phase.onTick) {
			action.execute(ctx);
		}

		// Evaluate transitions (priority order)
		for (Transition trans : phase.transitions) {
			if (trans.condition().test(ctx)) {
				executeTransition(ctx, trans);
				break;
			}
		}

		phaseTick++;
		totalTick++;
	}

	public void hurt(CardHolder holder, DamageSource source, float amount) {
		if (source.getEntity() instanceof LivingEntity && amount > 1) {
			hitCount++;
		}
	}

	public void reset() {
		currentPhaseId = definition.entryPhase;
		phaseTick = 0;
		totalTick = 0;
		hitCount = 0;
		variables.clear();
		resetActionState();
	}

	public <T> T getOrCreateState(Object key, Supplier<? extends T> factory) {
		@SuppressWarnings("unchecked")
		T value = (T) actionState.computeIfAbsent(key, k -> factory.get());
		return value;
	}

	public SpellRuntime copyStateTo(SpellDefinition newDefinition) {
		SpellRuntime copy = new SpellRuntime(newDefinition);
		copy.currentPhaseId = newDefinition.getPhase(currentPhaseId) != null ? currentPhaseId : newDefinition.entryPhase;
		copy.phaseTick = phaseTick;
		copy.totalTick = totalTick;
		copy.hitCount = hitCount;
		copy.variables.putAll(variables);
		return copy;
	}

	@Nullable
	public <T> T getState(Object key, Class<T> type) {
		Object value = actionState.get(key);
		return type.isInstance(value) ? type.cast(value) : null;
	}

	private void resetActionState() {
		for (Object value : actionState.values()) {
			if (value instanceof SpellCard card) {
				card.reset();
			}
		}
		actionState.clear();
	}

	private void executeTransition(SpellContext ctx, Transition trans) {
		PhaseDefinition oldPhase = definition.getPhase(currentPhaseId);
		if (oldPhase != null) {
			for (SpellAction action : oldPhase.onExit) {
				action.execute(ctx);
			}
		}

		if (trans.mode() == TransitionMode.CLEAR_SCREEN) {
			ctx.clearDanmaku();
		}

		ResourceLocation oldPhaseId = currentPhaseId;
		currentPhaseId = trans.targetPhase();
		phaseTick = 0;

		PhaseDefinition newPhase = definition.getPhase(currentPhaseId);
		if (newPhase != null) {
			for (SpellAction action : newPhase.onEnter) {
				action.execute(ctx);
			}
		}

		if (onPhaseChange != null) {
			onPhaseChange.accept(this);
		}
	}

	/**
	 * Force transition to a specific phase (used by commands/KJS).
	 */
	public void forceTransition(SpellContext ctx, ResourceLocation targetPhase) {
		PhaseDefinition oldPhase = definition.getPhase(currentPhaseId);
		if (oldPhase != null) {
			for (SpellAction action : oldPhase.onExit) {
				action.execute(ctx);
			}
		}

		currentPhaseId = targetPhase;
		phaseTick = 0;

		PhaseDefinition newPhase = definition.getPhase(currentPhaseId);
		if (newPhase != null) {
			for (SpellAction action : newPhase.onEnter) {
				action.execute(ctx);
			}
		}

		if (onPhaseChange != null) {
			onPhaseChange.accept(this);
		}
	}

	/**
	 * Get the legacy SpellCard from a single-phase legacy definition (for DanmakuCommander compatibility).
	 */
	@Nullable
	public SpellCard getLegacyCard() {
		PhaseDefinition phase = definition.getPhase(currentPhaseId);
		if (phase == null) return null;
		for (SpellAction action : phase.onTick) {
			if (action instanceof LegacyTickerAction legacy) {
				return legacy.getCard(this);
			}
		}
		return null;
	}
}
