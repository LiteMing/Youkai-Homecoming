package dev.xkmc.youkaishomecoming.content.spell.runtime;

import dev.xkmc.youkaishomecoming.content.spell.action.LegacyTickerAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SetSpellHealthAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellActions;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellHealthPlan;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberProviders;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Runtime state machine for a spell card definition.
 * Holds mutable state: current phase, tick counters, variables.
 * Does NOT extend SpellCard — it replaces the old runtime model.
 */
public class SpellRuntime {
	private static final int MAX_SPELL_HEALTH_SEGMENTS = 64;

	private final SpellDefinition definition;
	private final Function<ResourceLocation, SpellDefinition> definitionResolver;
	@Nullable
	private final SpellHealthPlan declaredHealthPlan;

	private ResourceLocation currentPhaseId;
	private int phaseTick;
	private int totalTick;
	private int hitCount;
	private boolean enteredCurrentPhase;
	private final Map<String, Double> variables = new HashMap<>();
	@Nullable
	private Set<String> trackWritesTo = null;
	private final List<ScheduledAction> scheduledActions = new ArrayList<>();
	private final List<ChildRuntime> childRuntimes = new ArrayList<>();
	private SpellMovementDirective movementDirective = SpellMovementDirective.random();
	private int spellMaxHealth;
	/** Timeout of the current health phase. */
	private int spellDurationTicks;
	private int spellStartTick;
	/** Stable phase order and weights for the current spell-health ring. */
	private final List<String> spellHealthPlanPhases = new ArrayList<>();
	private final List<Integer> spellHealthSegments = new ArrayList<>();
	private final List<Integer> spellDurationSegments = new ArrayList<>();
	private int spellHealthSegmentIndex = -1;
	private int spellHealthCompleted;
	@Nullable
	private SpellAction spellTimeoutAction;
	@Nullable
	private SpellAction spellBreakAction;
	/** Tracks how many consecutive ticks the target has been off the ground. Reset on ground contact. */
	private int targetFlyTime;
	@Nullable
	private ResourceLocation phasePreviewLock;

	@Nullable
	private Consumer<SpellRuntime> onPhaseChange;

	private static final int MAX_CHILD_RUNTIME_DEPTH = 8;
	private static final ThreadLocal<Integer> CHILD_RUNTIME_DEPTH = ThreadLocal.withInitial(() -> 0);

	public SpellRuntime(SpellDefinition definition) {
		this(definition, SpellRegistry::get, inferHealthPlan(definition));
	}

	public SpellRuntime(SpellDefinition definition,
			Function<ResourceLocation, SpellDefinition> definitionResolver,
			@Nullable SpellHealthPlan declaredHealthPlan) {
		this.definition = definition;
		this.definitionResolver = definitionResolver;
		this.declaredHealthPlan = declaredHealthPlan;
		this.currentPhaseId = definition.entryPhase;
		initializeStaticSpellHealthPlan(definition.entryPhase);
	}

	@Nullable
	public SpellDefinition resolveDefinition(ResourceLocation spellId) {
		return definitionResolver.apply(spellId);
	}

	public SpellRuntime continueWith(SpellDefinition nextDefinition) {
		SpellRuntime next = new SpellRuntime(nextDefinition, definitionResolver, declaredHealthPlan);
		if (declaredHealthPlan != null) {
			next.totalTick = totalTick;
			next.hitCount = hitCount;
			next.spellHealthCompleted = Math.min(next.getSpellHealthTotal(), spellHealthCompleted);
		}
		return next;
	}

	@Nullable
	private static SpellHealthPlan inferHealthPlan(SpellDefinition definition) {
		try {
			return SpellHealthPlan.analyzeIfPresent(definition, SpellRegistry::get).orElse(null);
		} catch (IllegalArgumentException ignored) {
			// Draft/legacy definitions remain previewable; export and certification
			// are the authoritative rejection boundaries.
			return null;
		}
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

	public int getSpellMaxHealth() {
		return spellMaxHealth;
	}

	public int getSpellDurationTicks() {
		return Math.max(0, spellDurationTicks);
	}

	/** Sum used by certification quotes; the rendered timeout always uses the current segment. */
	public int getSpellPlanDurationTicks() {
		int total = sumSegments(spellDurationSegments, spellDurationSegments.size());
		return total > 0 ? total : getSpellDurationTicks();
	}

	public int getSpellHealthTotal() {
		long total = 0;
		for (int segment : spellHealthSegments) total += Math.max(0, segment);
		return (int) Math.min(Integer.MAX_VALUE, total);
	}

	public int getSpellHealthCompleted() {
		return Math.max(0, Math.min(getSpellHealthTotal(), spellHealthCompleted));
	}

	public int getSpellHealthSegmentCount() {
		return spellHealthSegments.size();
	}

	public int[] getSpellHealthSegments() {
		return spellHealthSegments.stream().mapToInt(Integer::intValue).toArray();
	}

	public int getSpellElapsedTicks() {
		if (spellMaxHealth <= 0) return 0;
		int currentElapsed = getCurrentSpellElapsedTicks();
		if (spellDurationTicks > 0) {
			return Math.min(currentElapsed, spellDurationTicks);
		}
		return 0;
	}

	/** Real runtime age used by lifecycle events; unlike the ring it does not skip unused phase time. */
	public int getBattleElapsedTicks() {
		return Math.max(0, totalTick);
	}

	private int getCurrentSpellElapsedTicks() {
		return Math.max(0, totalTick - spellStartTick);
	}

	public void setSpellHealth(int maxHealth, int durationTicks) {
		setSpellHealth(maxHealth, durationTicks, null, null);
	}

	public void setSpellHealth(int maxHealth, int durationTicks,
			@Nullable SpellAction onTimeout, @Nullable SpellAction onBreak) {
		boolean hadCurrentSegment = spellMaxHealth > 0;
		spellMaxHealth = Math.max(1, maxHealth);
		spellDurationTicks = Math.max(0, durationTicks);
		spellStartTick = totalTick;
		int plannedIndex = spellHealthPlanPhases.indexOf(planKey(definition.id, currentPhaseId));
		if (plannedIndex >= 0) {
			spellHealthSegmentIndex = plannedIndex;
			spellHealthSegments.set(plannedIndex, spellMaxHealth);
			spellDurationSegments.set(plannedIndex, spellDurationTicks);
			spellHealthCompleted = sumSegments(spellHealthSegments, plannedIndex);
		} else if (hadCurrentSegment && spellHealthSegmentIndex >= 0
				&& spellHealthSegmentIndex < spellHealthSegments.size()) {
			spellHealthSegments.set(spellHealthSegmentIndex, spellMaxHealth);
			spellDurationSegments.set(spellHealthSegmentIndex, spellDurationTicks);
		} else {
			appendSpellHealthSegment(definition.id, currentPhaseId, spellMaxHealth, spellDurationTicks);
		}
		spellTimeoutAction = validSpellHealthTarget(onTimeout);
		spellBreakAction = validSpellHealthTarget(onBreak);
	}

	public void clearSpellHealth() {
		spellHealthPlanPhases.clear();
		spellHealthSegments.clear();
		spellDurationSegments.clear();
		spellHealthSegmentIndex = -1;
		spellHealthCompleted = 0;
		clearCurrentSpellHealth();
	}

	/** Ends the current segment while retaining its contribution to the spell ring. */
	private void completeCurrentSpellHealth() {
		if (spellMaxHealth > 0 && spellHealthSegmentIndex >= 0) {
			spellHealthCompleted = sumSegments(spellHealthSegments, spellHealthSegmentIndex + 1);
		}
		spellHealthSegmentIndex = -1;
		clearCurrentSpellHealth();
	}

	private void appendSpellHealthSegment(ResourceLocation spellId, ResourceLocation phaseId,
			int maxHealth, int durationTicks) {
		if (spellHealthSegments.size() >= MAX_SPELL_HEALTH_SEGMENTS) {
			return;
		}
		spellHealthPlanPhases.add(planKey(spellId, phaseId));
		spellHealthSegments.add(Math.max(1, maxHealth));
		spellDurationSegments.add(Math.max(0, durationTicks));
		spellHealthSegmentIndex = spellHealthSegments.size() - 1;
		spellHealthCompleted = sumSegments(spellHealthSegments, spellHealthSegmentIndex);
	}

	private void clearCurrentSpellHealth() {
		spellMaxHealth = 0;
		spellDurationTicks = 0;
		spellStartTick = totalTick;
		spellTimeoutAction = null;
		spellBreakAction = null;
	}

	private void initializeStaticSpellHealthPlan(ResourceLocation startPhase) {
		spellHealthPlanPhases.clear();
		spellHealthSegments.clear();
		spellDurationSegments.clear();
		spellHealthSegmentIndex = -1;
		spellHealthCompleted = 0;
		if (declaredHealthPlan != null) {
			for (SpellHealthPlan.Segment segment : declaredHealthPlan.breakChain()) {
				if (spellHealthSegments.size() >= MAX_SPELL_HEALTH_SEGMENTS) break;
				appendSpellHealthSegment(segment.spellId(), segment.phaseId(),
						segment.health(), segment.durationTicks());
			}
			spellHealthSegmentIndex = -1;
			spellHealthCompleted = 0;
			return;
		}
		Set<ResourceLocation> visited = new HashSet<>();
		ResourceLocation phaseId = startPhase;
		while (phaseId != null && visited.add(phaseId)
				&& spellHealthSegments.size() < MAX_SPELL_HEALTH_SEGMENTS) {
			PhaseDefinition phase = definition.getPhase(phaseId);
			SetSpellHealthAction health = phase == null ? null : findStaticHealthAction(phase.onEnter);
			if (health == null || health.mode() != SetSpellHealthAction.Mode.SET
					|| !(health.health() instanceof NumberProviders.Constant hp)
					|| !(health.duration() instanceof NumberProviders.Constant duration)) {
				break;
			}
			appendSpellHealthSegment(definition.id, phaseId,
					clampPlannedValue(hp.value(), 1, 1_000_000),
					clampPlannedValue(duration.value(), 0, 1_000_000));
			phaseId = deterministicNextPhase(health);
		}
		spellHealthSegmentIndex = -1;
		spellHealthCompleted = 0;
	}

	@Nullable
	private static SetSpellHealthAction findStaticHealthAction(List<SpellAction> actions) {
		SetSpellHealthAction found = null;
		for (SpellAction action : actions) {
			if (action instanceof SetSpellHealthAction health) {
				found = health;
			} else if (action instanceof SpellActions.SequenceAction sequence) {
				SetSpellHealthAction nested = findStaticHealthAction(sequence.actions());
				if (nested != null) found = nested;
			}
		}
		return found;
	}

	@Nullable
	private static ResourceLocation deterministicNextPhase(SetSpellHealthAction health) {
		if (health.onBreak().orElse(null) instanceof SpellActions.ForcePhase phase) {
			return phase.phaseId();
		}
		return null;
	}

	private static String planKey(ResourceLocation spellId, ResourceLocation phaseId) {
		return spellId + "|" + phaseId;
	}

	private static int clampPlannedValue(double value, int min, int max) {
		if (!Double.isFinite(value)) return min;
		return Math.max(min, Math.min(max, (int) Math.round(value)));
	}

	private static int sumSegments(List<Integer> segments, int endExclusive) {
		long total = 0;
		for (int i = 0; i < Math.min(endExclusive, segments.size()); i++) {
			total += Math.max(0, segments.get(i));
		}
		return (int) Math.min(Integer.MAX_VALUE, total);
	}

	private static int saturatedAdd(int a, int b) {
		return (int) Math.min(Integer.MAX_VALUE, (long) Math.max(0, a) + Math.max(0, b));
	}

	public boolean triggerSpellHealthBreak(CardHolder holder) {
		return triggerSpellHealthBreak(holder, null);
	}

	public boolean triggerSpellHealthBreak(CardHolder holder, @Nullable DamageSource source) {
		if (spellMaxHealth <= 0) return false;
		if (spellBreakAction == null) {
			postSpellHealthEvent(holder,
					dev.xkmc.youkaishomecoming.compat.stg.event.SpellCardEvent.Outcome.BROKEN, source);
			clearSpellHealth();
			return false;
		}
		return executeSpellHealthTarget(holder, spellBreakAction,
				dev.xkmc.youkaishomecoming.compat.stg.event.SpellCardEvent.Outcome.BROKEN, source);
	}

	private boolean triggerSpellHealthTimeout(CardHolder holder) {
		if (spellMaxHealth <= 0 || spellDurationTicks <= 0) return false;
		if (getCurrentSpellElapsedTicks() < spellDurationTicks) return false;
		if (holder instanceof SpellRuntimeHost host && host.spellHealthTimeoutEndsFight()) {
			postSpellHealthEvent(holder,
					dev.xkmc.youkaishomecoming.compat.stg.event.SpellCardEvent.Outcome.TIMEOUT, null);
			clearSpellHealth();
			host.settleSpellHealthTimeout();
			return false;
		}
		if (spellTimeoutAction == null) {
			postSpellHealthEvent(holder,
					dev.xkmc.youkaishomecoming.compat.stg.event.SpellCardEvent.Outcome.TIMEOUT, null);
			clearSpellHealth();
			if (holder instanceof SpellRuntimeHost host) host.settleSpellHealthTimeout();
			return false;
		}
		boolean transitioned = executeSpellHealthTarget(holder, spellTimeoutAction,
				dev.xkmc.youkaishomecoming.compat.stg.event.SpellCardEvent.Outcome.TIMEOUT, null);
		if (!transitioned && holder instanceof SpellRuntimeHost host) host.settleSpellHealthTimeout();
		return transitioned;
	}

	private boolean executeSpellHealthTarget(CardHolder holder, SpellAction target,
			dev.xkmc.youkaishomecoming.compat.stg.event.SpellCardEvent.Outcome outcome,
			@Nullable DamageSource source) {
		if (target instanceof SpellActions.ForcePhase phase
				&& definition.getPhase(phase.phaseId()) == null) {
			postSpellHealthEvent(holder, outcome, source);
			clearSpellHealth();
			return false;
		}
		if (target instanceof SpellActions.ForceSpell spell
				&& resolveDefinition(spell.spellId()) == null) {
			postSpellHealthEvent(holder, outcome, source);
			clearSpellHealth();
			return false;
		}
		postSpellHealthEvent(holder, outcome, source);
		float healthRatio = holder.self().getHealth() / holder.self().getMaxHealth();
		SpellContext ctx = new SpellContext(holder, definition, this, definition.difficulty.resolve(healthRatio));
		if (target instanceof SpellActions.ForcePhase || target instanceof SpellActions.ForceSpell) {
			completeCurrentSpellHealth();
		} else {
			clearSpellHealth();
		}
		target.execute(ctx);
		return true;
	}

	private void postSpellHealthEvent(CardHolder holder,
			dev.xkmc.youkaishomecoming.compat.stg.event.SpellCardEvent.Outcome outcome,
			@Nullable DamageSource source) {
		dev.xkmc.youkaishomecoming.compat.stg.event.SpellCardEvent.post(holder, this, outcome, source);
	}

	@Nullable
	private static SpellAction validSpellHealthTarget(@Nullable SpellAction target) {
		return target instanceof SpellActions.ForcePhase || target instanceof SpellActions.ForceSpell
				? target : null;
	}

	public double getVariable(String key) {
		return variables.getOrDefault(key, 0.0);
	}

	public void setVariable(String key, double value) {
		variables.put(key, value);
		if (trackWritesTo != null) {
			trackWritesTo.add(key);
		}
	}

	public Map<String, Double> getVariables() {
		return Collections.unmodifiableMap(variables);
	}

	/**
	 * Begin recording setVariable calls to the returned set. While enabled, every
	 * explicit setVariable records its key here. Used by callbacks (e.g. on_hit)
	 * to distinguish "the callback really wrote this variable" from a temporary
	 * snapshot restore, regardless of whether the written value equals the snapshot.
	 *
	 * @return the set to collect written keys into, or null if tracking is not enabled
	 */
	@Nullable
	public Set<String> beginTrackWrites() {
		if (trackWritesTo != null) {
			trackWritesTo.clear();
		}
		trackWritesTo = new HashSet<>();
		return trackWritesTo;
	}

	/** Stop tracking writes initiated by {@link #beginTrackWrites()}. */
	public void endTrackWrites() {
		trackWritesTo = null;
	}

	public SpellMovementDirective getMovementDirective() {
		return movementDirective;
	}

	public void setMovementDirective(SpellMovementDirective movementDirective) {
		this.movementDirective = movementDirective;
	}

	public void setOnPhaseChange(@Nullable Consumer<SpellRuntime> listener) {
		this.onPhaseChange = listener;
	}

	public void setPhasePreviewLock(@Nullable ResourceLocation phaseId) {
		this.phasePreviewLock = phaseId;
	}

	/** Executes the current phase's on_enter list once. */
	public void enterCurrentPhase(CardHolder holder) {
		if (enteredCurrentPhase) return;
		PhaseDefinition phase = definition.getPhase(currentPhaseId);
		if (phase == null) return;
		enteredCurrentPhase = true;
		float healthRatio = holder.self().getHealth() / holder.self().getMaxHealth();
		SpellContext ctx = new SpellContext(holder, definition, this,
				definition.difficulty.resolve(healthRatio));
		for (SpellAction action : phase.onEnter) {
			action.execute(ctx);
		}
	}

	public void tick(CardHolder holder) {
		PhaseDefinition phase = definition.getPhase(currentPhaseId);
		if (phase == null) return;
		movementDirective = SpellMovementDirective.random();

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
		if (!enteredCurrentPhase) {
			enterCurrentPhase(holder);
			phase = definition.getPhase(currentPhaseId);
			if (phase == null) return;
		}

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
		tickChildRuntimes(holder);

		// Evaluate transitions (priority order)
		for (Transition trans : phase.transitions) {
			if (!trans.condition().test(ctx)) continue;
			if (isPhaseLocked(trans.targetPhase())) continue;
			executeTransition(ctx, trans);
			break;
		}

		phaseTick++;
		totalTick++;
		triggerSpellHealthTimeout(holder);
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
		enteredCurrentPhase = false;
		targetFlyTime = 0;
		hurtCooldownRemaining = 0;
		variables.clear();
		scheduledActions.clear();
		childRuntimes.clear();
		movementDirective = SpellMovementDirective.random();
		clearSpellHealth();
		initializeStaticSpellHealthPlan(currentPhaseId);

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

		PhaseDefinition newPhase = definition.getPhase(targetPhase);
		currentPhaseId = targetPhase;
		phaseTick = 0;
		enteredCurrentPhase = false;
		scheduledActions.clear();
		// Ordinary attack-pattern phases may share one spell-health segment. A
		// phase starts a new segment only when it declares set_spell_health itself.
		if (newPhase != null && findStaticHealthAction(newPhase.onEnter) != null) {
			completeCurrentSpellHealth();
		}

		if (newPhase != null) {
			for (SpellAction action : newPhase.onEnter) {
				action.execute(ctx);
			}
			enteredCurrentPhase = true;
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
		enteredCurrentPhase = false;
		targetFlyTime = 0;
		hurtCooldownRemaining = 0;
		variables.clear();
		scheduledActions.clear();
		childRuntimes.clear();
		clearSpellHealth();
		initializeStaticSpellHealthPlan(targetPhase);
		resetLegacyActions(phase);
		for (SpellAction action : phase.onEnter) {
			action.execute(ctx);
		}
		enteredCurrentPhase = true;
		notifyPhaseChange();
	}

	// --- Delayed action scheduling ---

	/**
	 * Schedule a list of actions to execute at a specific totalTick.
	 */
	public void scheduleDelayed(int executeAtTick, List<SpellAction> actions) {
		scheduledActions.add(new ScheduledAction(executeAtTick, actions));
	}

	public void startChildRuntime(CardHolder holder, SpellDefinition definition, @Nullable ResourceLocation phaseId, int duration) {
		if (duration <= 0) {
			return;
		}
		ResourceLocation targetPhase = phaseId == null ? definition.entryPhase : phaseId;
		if (definition.getPhase(targetPhase) == null) {
			return;
		}
		SpellRuntime runtime = new SpellRuntime(definition);
		PhaseDefinition phase = definition.getPhase(targetPhase);
		if (phase == null) {
			return;
		}
		runtime.currentPhaseId = targetPhase;
		runtime.initializeStaticSpellHealthPlan(targetPhase);
		runtime.phaseTick = 0;
		runtime.totalTick = 0;
		runtime.hitCount = 0;
		runtime.enteredCurrentPhase = false;
		runtime.targetFlyTime = 0;
		runtime.hurtCooldownRemaining = 0;
		runtime.variables.clear();
		runtime.variables.putAll(variables);
		runtime.scheduledActions.clear();
		runtime.childRuntimes.clear();
		runtime.resetLegacyActions(phase);
		float healthRatio = holder.self().getHealth() / holder.self().getMaxHealth();
		DifficultyModifiers diff = definition.difficulty.resolve(healthRatio);
		SpellContext ctx = new SpellContext(holder, definition, runtime, diff);
		for (SpellAction action : phase.onEnter) {
			action.execute(ctx);
		}
		runtime.enteredCurrentPhase = true;
		runtime.notifyPhaseChange();
		childRuntimes.add(new ChildRuntime(runtime, duration));
	}

	private void tickChildRuntimes(CardHolder holder) {
		if (childRuntimes.isEmpty()) {
			return;
		}
		var ready = new ArrayList<>(childRuntimes);
		childRuntimes.clear();
		for (ChildRuntime child : ready) {
			if (child.remainingTicks() <= 0 || child.runtime().isFinished()) {
				continue;
			}
			int depth = CHILD_RUNTIME_DEPTH.get();
			if (depth >= MAX_CHILD_RUNTIME_DEPTH) {
				continue;
			}
			CHILD_RUNTIME_DEPTH.set(depth + 1);
			try {
				child.runtime().tick(holder);
			} finally {
				CHILD_RUNTIME_DEPTH.set(depth);
			}
			int remaining = child.remainingTicks() - 1;
			if (remaining > 0 && !child.runtime().isFinished()) {
				childRuntimes.add(new ChildRuntime(child.runtime(), remaining));
			}
		}
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

	private record ChildRuntime(SpellRuntime runtime, int remainingTicks) {
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
		tag.putInt("SpellMaxHealth", spellMaxHealth);
		tag.putInt("SpellDurationTicks", spellDurationTicks);
		tag.putInt("SpellStartTick", spellStartTick);
		if (!spellHealthSegments.isEmpty()) {
			tag.putIntArray("SpellHealthSegments", getSpellHealthSegments());
			tag.putIntArray("SpellDurationSegments",
					spellDurationSegments.stream().mapToInt(Integer::intValue).toArray());
			var phases = new net.minecraft.nbt.ListTag();
			for (String phase : spellHealthPlanPhases) {
				phases.add(net.minecraft.nbt.StringTag.valueOf(phase));
			}
			tag.put("SpellHealthPlanPhases", phases);
			tag.putInt("SpellHealthSegmentIndex", spellHealthSegmentIndex);
			tag.putInt("SpellHealthCompleted", spellHealthCompleted);
		}
		writeAction(tag, "SpellTimeoutAction", spellTimeoutAction);
		writeAction(tag, "SpellBreakAction", spellBreakAction);
		tag.putBoolean("EnteredCurrentPhase", enteredCurrentPhase);
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
			this.spellMaxHealth = Math.max(0, tag.getInt("SpellMaxHealth"));
			this.spellDurationTicks = Math.max(0, tag.getInt("SpellDurationTicks"));
			this.spellStartTick = Math.max(0, tag.getInt("SpellStartTick"));
			int[] savedHealth = tag.getIntArray("SpellHealthSegments");
			if (savedHealth.length > 0) {
				this.spellHealthSegments.clear();
				for (int segment : savedHealth) {
					if (segment > 0 && this.spellHealthSegments.size() < MAX_SPELL_HEALTH_SEGMENTS) {
						this.spellHealthSegments.add(segment);
					}
				}
			}
			int[] savedDurations = tag.getIntArray("SpellDurationSegments");
			if (savedDurations.length > 0) {
				this.spellDurationSegments.clear();
				for (int duration : savedDurations) {
					if (this.spellDurationSegments.size() >= this.spellHealthSegments.size()) break;
					this.spellDurationSegments.add(Math.max(0, duration));
				}
			}
			while (this.spellDurationSegments.size() < this.spellHealthSegments.size()) {
				this.spellDurationSegments.add(this.spellDurationSegments.isEmpty()
						? this.spellDurationTicks : 0);
			}
			while (this.spellDurationSegments.size() > this.spellHealthSegments.size()) {
				this.spellDurationSegments.remove(this.spellDurationSegments.size() - 1);
			}
			if (tag.contains("SpellHealthPlanPhases", net.minecraft.nbt.Tag.TAG_LIST)) {
				this.spellHealthPlanPhases.clear();
				var phases = tag.getList("SpellHealthPlanPhases", net.minecraft.nbt.Tag.TAG_STRING);
				for (int i = 0; i < phases.size() && i < this.spellHealthSegments.size(); i++) {
					this.spellHealthPlanPhases.add(phases.getString(i));
				}
			}
			if (this.spellHealthSegments.isEmpty() && this.spellMaxHealth > 0) {
				appendSpellHealthSegment(definition.id, phaseId, this.spellMaxHealth, this.spellDurationTicks);
			}
			if (this.spellHealthPlanPhases.size() != this.spellHealthSegments.size()) {
				this.spellHealthPlanPhases.clear();
				for (int i = 0; i < this.spellHealthSegments.size(); i++) {
					this.spellHealthPlanPhases.add(i + 1 == this.spellHealthSegments.size()
							? planKey(definition.id, phaseId) : "");
				}
			}
			this.spellHealthSegmentIndex = tag.contains("SpellHealthSegmentIndex")
					? tag.getInt("SpellHealthSegmentIndex")
					: this.spellHealthPlanPhases.indexOf(planKey(definition.id, phaseId));
			if (this.spellHealthSegmentIndex < 0 && this.spellMaxHealth > 0) {
				this.spellHealthSegmentIndex = Math.max(0, this.spellHealthSegments.size() - 1);
			}
			this.spellHealthSegmentIndex = Math.min(this.spellHealthSegmentIndex,
					this.spellHealthSegments.size() - 1);
			this.spellHealthCompleted = Math.max(0,
					Math.min(getSpellHealthTotal(), tag.getInt("SpellHealthCompleted")));
			this.spellTimeoutAction = readAction(tag, "SpellTimeoutAction");
			this.spellBreakAction = readAction(tag, "SpellBreakAction");
			this.enteredCurrentPhase = tag.contains("EnteredCurrentPhase") ?
					tag.getBoolean("EnteredCurrentPhase") : true;
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

	private static void writeAction(net.minecraft.nbt.CompoundTag tag, String key, @Nullable SpellAction action) {
		if (action == null) return;
		SpellAction.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, action)
				.result().ifPresent(value -> tag.put(key, value));
	}

	@Nullable
	private static SpellAction readAction(net.minecraft.nbt.CompoundTag tag, String key) {
		if (!tag.contains(key)) return null;
		return SpellAction.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag.get(key))
				.result().map(SpellRuntime::validSpellHealthTarget).orElse(null);
	}
}
