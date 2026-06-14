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
	/** Tracks how many consecutive ticks the target has been off the ground. Reset on ground contact. */
	private int targetFlyTime;
	@Nullable
	private ResourceLocation phasePreviewLock;

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

	/**
	 * Returns true when the spell has naturally finished — i.e. the current phase
	 * no longer exists in the definition (transitioned to a terminal/undefined phase).
	 */
	public boolean isFinished() {
		return definition.getPhase(currentPhaseId) == null;
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

	public int getTargetFlyTime() {
		return targetFlyTime;
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

	public void setPhasePreviewLock(@Nullable ResourceLocation phaseId) {
		this.phasePreviewLock = phaseId;
	}

	public void tick(CardHolder holder) {
		PhaseDefinition phase = definition.getPhase(currentPhaseId);
		if (phase == null) return;

		// Track target fly time — use the same logic as SpellContext.targetOnGround()
		// so preview mode uses the simulated targetOnGround property instead of entity physics.
		boolean onGround;
		if (holder instanceof dev.xkmc.youkaishomecoming.content.spell.preview.PreviewCardHolder preview) {
			onGround = preview.isTargetOnGround();
		} else {
			var targetEntity = holder.targetEntity();
			onGround = targetEntity == null || targetEntity.onGround();
		}
		if (!onGround) {
			targetFlyTime++;
		} else {
			targetFlyTime = 0;
		}

		float healthRatio = holder.self().getHealth() / holder.self().getMaxHealth();
		DifficultyModifiers diff = definition.difficulty.resolve(healthRatio);
		SpellContext ctx = new SpellContext(holder, definition, this, diff);

		// Execute tick actions
		for (int i = 0; i < phase.onTick.size(); i++) {
			// Track action index for preview highlighting
			if (holder instanceof dev.xkmc.youkaishomecoming.content.spell.preview.PreviewCardHolder preview) {
				preview.setCurrentSpawningActionIndex(i);
			}
			phase.onTick.get(i).execute(ctx);
		}
		// Reset action index after tick
		if (holder instanceof dev.xkmc.youkaishomecoming.content.spell.preview.PreviewCardHolder preview) {
			preview.setCurrentSpawningActionIndex(-1);
		}

		// Execute scheduled delayed actions
		executeScheduledActions(ctx);

		// Evaluate transitions (priority order)
		for (Transition trans : phase.transitions) {
			if (!trans.condition().test(ctx)) continue;
			if (isPhaseLocked(trans.targetPhase())) continue;
			executeTransition(ctx, trans);
			break;
		}

		phaseTick++;
		totalTick++;
		if (hurtCooldownRemaining > 0) hurtCooldownRemaining--;
	}

	public void tick(SpellRuntimeHost host) {
		tick((CardHolder) host);
	}

	/** Minimum ticks between on_damage triggers to prevent feedback loops (e.g. border → hit → on_hurt → border). */
	private static final int HURT_COOLDOWN = 20;
	private int hurtCooldownRemaining = 0;

	public void hurt(CardHolder holder, DamageSource source, float amount) {
		if (source.getEntity() instanceof LivingEntity && amount > 1) {
			hitCount++;

			// Enforce cooldown to prevent exponential feedback from danmaku-hit → on_hurt → more danmaku
			if (hurtCooldownRemaining > 0) return;
			hurtCooldownRemaining = HURT_COOLDOWN;

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

	public void hurt(SpellRuntimeHost host, DamageSource source, float amount) {
		hurt((CardHolder) host, source, amount);
	}

	public void reset() {
		currentPhaseId = definition.entryPhase;
		phaseTick = 0;
		totalTick = 0;
		hitCount = 0;
		targetFlyTime = 0;
		hurtCooldownRemaining = 0;
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
		boolean clearScreen = trans.mode() == TransitionMode.CLEAR_SCREEN
				|| trans.mode() == TransitionMode.CLEAR_AND_RESET;
		boolean resetVars = trans.mode() == TransitionMode.CLEAR_AND_RESET;
		doTransition(ctx, trans.targetPhase(), clearScreen, resetVars);
	}

	/**
	 * Force transition to a specific phase (used by commands/KJS).
	 */
	public void forceTransition(SpellContext ctx, ResourceLocation targetPhase) {
		forceTransition(ctx, targetPhase, true);
	}

	public void forceTransition(SpellContext ctx, ResourceLocation targetPhase, boolean clearScreen) {
		if (definition.getPhase(targetPhase) == null || isPhaseLocked(targetPhase)) {
			return;
		}
		doTransition(ctx, targetPhase, clearScreen, false);
	}

	/**
	 * Core phase transition logic shared by executeTransition and forceTransition.
	 * Executes onExit → optional clear/reset → switch phase → onEnter → notify.
	 */
	private void doTransition(SpellContext ctx, ResourceLocation targetPhase, boolean clearScreen, boolean resetVars) {
		PhaseDefinition oldPhase = definition.getPhase(currentPhaseId);
		if (oldPhase != null) {
			for (SpellAction action : oldPhase.onExit) {
				action.execute(ctx);
			}
		}

		if (clearScreen) {
			ctx.clearDanmaku();
		}
		if (resetVars) {
			variables.clear();
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
		notifyPhaseChange();
	}

	public void restartAtPhase(SpellContext ctx, ResourceLocation targetPhase) {
		PhaseDefinition phase = definition.getPhase(targetPhase);
		if (phase == null) {
			return;
		}
		currentPhaseId = targetPhase;
		phaseTick = 0;
		totalTick = 0;
		hitCount = 0;
		targetFlyTime = 0;
		hurtCooldownRemaining = 0;
		variables.clear();
		scheduledActions.clear();
		resetLegacyActions(phase);
		for (SpellAction action : phase.onEnter) {
			action.execute(ctx);
		}
		notifyPhaseChange();
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

	// === NBT Persistence ===

	/**
	 * Save runtime state to NBT for entity persistence across save/load.
	 * Scheduled actions are NOT saved (they are transient burst/delay state).
	 */
	public net.minecraft.nbt.CompoundTag saveToTag() {
		var tag = new net.minecraft.nbt.CompoundTag();
		tag.putString("DefinitionId", definition.id.toString());
		tag.putString("PhaseId", currentPhaseId.toString());
		tag.putInt("PhaseTick", phaseTick);
		tag.putInt("TotalTick", totalTick);
		tag.putInt("HitCount", hitCount);
		if (!variables.isEmpty()) {
			var varsTag = new net.minecraft.nbt.CompoundTag();
			for (var entry : variables.entrySet()) {
				varsTag.putDouble(entry.getKey(), entry.getValue());
			}
			tag.put("Variables", varsTag);
		}
		return tag;
	}

	/**
	 * Restore runtime state from NBT after entity load.
	 * Only restores if the phase ID is still valid in the current definition.
	 */
	public void loadFromTag(net.minecraft.nbt.CompoundTag tag) {
		var phaseId = ResourceLocation.tryParse(tag.getString("PhaseId"));
		if (phaseId != null && definition.getPhase(phaseId) != null) {
			this.currentPhaseId = phaseId;
			this.phaseTick = tag.getInt("PhaseTick");
			this.totalTick = tag.getInt("TotalTick");
			this.hitCount = tag.getInt("HitCount");
			if (tag.contains("Variables")) {
				var varsTag = tag.getCompound("Variables");
				for (String key : varsTag.getAllKeys()) {
					variables.put(key, varsTag.getDouble(key));
				}
			}
		}
		// If phase ID is invalid (definition was updated), stay at entry phase (default from constructor)
	}

	private boolean isPhaseLocked(ResourceLocation targetPhase) {
		return phasePreviewLock != null && !phasePreviewLock.equals(targetPhase);
	}

	private void notifyPhaseChange() {
		if (onPhaseChange != null) {
			onPhaseChange.accept(this);
		}
	}
}
