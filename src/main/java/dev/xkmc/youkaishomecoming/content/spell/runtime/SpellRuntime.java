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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

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
	private final List<ScheduledAction> scheduledActions = new ArrayList<>();

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

		// Execute scheduled delayed actions
		executeScheduledActions(ctx);

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

			// Execute on_damage actions for current phase
			PhaseDefinition phase = definition.getPhase(currentPhaseId);
			if (phase != null && !phase.onDamage.isEmpty()) {
				float healthRatio = holder.self().getHealth() / holder.self().getMaxHealth();
				DifficultyModifiers diff = definition.difficulty.resolve(healthRatio);
				SpellContext ctx = new SpellContext(holder, definition, this, diff);
				for (SpellAction action : phase.onDamage) {
					action.execute(ctx);
				}
			}
		}
	}

	public void reset() {
		currentPhaseId = definition.entryPhase;
		phaseTick = 0;
		totalTick = 0;
		hitCount = 0;
		variables.clear();
		scheduledActions.clear();

		// Reset any legacy ticker actions
		resetLegacyActions(definition.getPhase(currentPhaseId));
	}

	private void resetLegacyActions(@Nullable PhaseDefinition phase) {
		if (phase == null) return;
		for (SpellAction action : phase.onTick) {
			if (action instanceof LegacyTickerAction legacy) {
				legacy.reset();
			}
		}
	}

	private void executeTransition(SpellContext ctx, Transition trans) {
		PhaseDefinition oldPhase = definition.getPhase(currentPhaseId);
		if (oldPhase != null) {
			for (SpellAction action : oldPhase.onExit) {
				action.execute(ctx);
			}
		}

		if (trans.mode() == TransitionMode.CLEAR_SCREEN || trans.mode() == TransitionMode.CLEAR_AND_RESET) {
			ctx.clearDanmaku();
		}
		if (trans.mode() == TransitionMode.CLEAR_AND_RESET) {
			variables.clear();
		}

		ResourceLocation oldPhaseId = currentPhaseId;
		currentPhaseId = trans.targetPhase();
		phaseTick = 0;
		scheduledActions.clear(); // Clear delayed actions from previous phase

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
		scheduledActions.clear();

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

	// --- Delayed action scheduling ---

	/**
	 * Schedule a list of actions to execute at a specific totalTick.
	 */
	public void scheduleDelayed(int executeAtTick, List<SpellAction> actions) {
		scheduledActions.add(new ScheduledAction(executeAtTick, actions));
	}

	/**
	 * Execute all delayed actions whose time has come.
	 * Called from tick() after regular onTick actions.
	 * Snapshot the list first to avoid ConcurrentModificationException,
	 * since executed actions may schedule new delayed actions.
	 */
	private void executeScheduledActions(SpellContext ctx) {
		// Snapshot: collect ready actions and remove them before executing
		var ready = new java.util.ArrayList<ScheduledAction>();
		var iter = scheduledActions.iterator();
		while (iter.hasNext()) {
			var scheduled = iter.next();
			if (totalTick >= scheduled.executeAtTick()) {
				ready.add(scheduled);
				iter.remove();
			}
		}
		// Execute outside the iteration so new scheduleDelayed() calls are safe
		for (var scheduled : ready) {
			for (var action : scheduled.actions()) {
				action.execute(ctx);
			}
		}
	}

	/**
	 * A delayed action entry: actions to execute when totalTick reaches executeAtTick.
	 */
	private record ScheduledAction(int executeAtTick, List<SpellAction> actions) {
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
				return legacy.getCard();
			}
		}
		return null;
	}
}
