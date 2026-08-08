package dev.xkmc.youkaishomecoming.content.spell.analysis;

import dev.xkmc.youkaishomecoming.content.entity.danmaku.HitBehavior;
import dev.xkmc.youkaishomecoming.content.spell.action.BurstAction;
import dev.xkmc.youkaishomecoming.content.spell.action.ConfineTargetAction;
import dev.xkmc.youkaishomecoming.content.spell.action.DelayAction;
import dev.xkmc.youkaishomecoming.content.spell.action.EraseEnemyDanmakuAction;
import dev.xkmc.youkaishomecoming.content.spell.action.FireDanmakuAction;
import dev.xkmc.youkaishomecoming.content.spell.action.FireLaserAction;
import dev.xkmc.youkaishomecoming.content.spell.action.FireTextDanmakuAction;
import dev.xkmc.youkaishomecoming.content.spell.action.LegacyTickerAction;
import dev.xkmc.youkaishomecoming.content.spell.action.RunCommandAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SetEntityFlagAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SetSpellCircleAction;
import dev.xkmc.youkaishomecoming.content.spell.action.ShowSpellTitleAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpawnShooterAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellActions;
import dev.xkmc.youkaishomecoming.content.spell.action.TeleportAction;
import dev.xkmc.youkaishomecoming.content.spell.action.TeleportRandomAction;
import dev.xkmc.youkaishomecoming.content.spell.action.YsmRenderAction;
import dev.xkmc.youkaishomecoming.content.spell.condition.SpellCondition;
import dev.xkmc.youkaishomecoming.content.spell.definition.MoverConfig;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberProvider;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberProviders;
import dev.xkmc.youkaishomecoming.content.spell.definition.OriginConfig;
import dev.xkmc.youkaishomecoming.content.spell.definition.PhaseDefinition;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.definition.Transition;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Static analyzer for untrusted spell definitions (design doc §10, §11, §12).
 * <p>
 * Pipeline order matters (D9):
 * <ol>
 *   <li>structural eligibility scan (phase count / entry / key-id, legacy precheck);</li>
 *   <li>generic walk: nesting depth, string/expression length, lifetime/duration/health
 *   number fields, wide action count;</li>
 *   <li>semantic walk: capability collection, amplification math
 *   (repeat × burst × count × outer × shooter × hook), bounded NumberProvider analysis;</li>
 *   <li>finalize: profile-specific hard-limit checks and capability policy check.</li>
 * </ol>
 * Hard rejections throw {@link SpellAnalysisException} carrying diagnostics.
 * <p>
 * MARKET profile reproduces the historical SpellMarketValidator budget behavior
 * (including its error messages) so market imports do not regress.
 */
public final class SpellAnalyzer {

	private final SpellDefinition definition;
	private final SpellAnalysisProfile profile;
	private final SpellAnalysisLimits limits;

	// semantic counters
	private final EnumSet<SpellCapability> capabilities = EnumSet.noneOf(SpellCapability.class);
	private final List<SpellDiagnostic> diagnostics = new ArrayList<>();
	private final ArrayDeque<String> segments = new ArrayDeque<>();

	private int wideActions;
	private int actions;
	private long marketProjectiles;
	private long marketShooters;
	private long certShooters;
	private long certOneShotSpawns;
	private long certPerTickSpawns;
	private long certMaxOneShotBurst;
	private long certMaxDeferredHookBurst;
	private long deferredBurstAccum;
	private long certShooterTotalSpawns;
	private long certShooterTickSpawns;
	private long certRecurringShooterEntitySpawns;
	private long certShooterPeakAlive;
	private long certPeakShooters;
	private long hookExecutionsOnce;
	private long hookExecutionsPerTick;
	private long lifetimeUpperMax;
	private long expressionOps;
	private boolean inOneShotGroup;
	private long burstAccum;
	private int hookDepth;

	private SpellAnalyzer(SpellDefinition definition, SpellAnalysisProfile profile, SpellAnalysisLimits limits) {
		this.definition = definition;
		this.profile = profile;
		this.limits = limits;
	}

	public static SpellAnalysis analyze(SpellDefinition definition) {
		return analyze(definition, SpellAnalysisProfile.CERTIFICATION);
	}

	public static SpellAnalysis analyze(SpellDefinition definition, SpellAnalysisProfile profile) {
		return analyze(definition, profile, profile == SpellAnalysisProfile.MARKET
				? SpellAnalysisLimits.market() : SpellAnalysisLimits.certification());
	}

	public static SpellAnalysis analyze(SpellDefinition definition, SpellAnalysisProfile profile, SpellAnalysisLimits limits) {
		return new SpellAnalyzer(definition, profile, limits).run();
	}

	// ------------------------------------------------------------------ pipeline

	private SpellAnalysis run() {
		checkStructural();
		if (profile == SpellAnalysisProfile.CERTIFICATION && SpellEligibility.hasLegacyTicker(definition)) {
			throw rejected("legacy_ticker", "LegacyTickerAction definitions cannot be serialized or certified (D9 precheck)");
		}
		genericWalk(definition, 0);
		for (var entry : definition.phases.entrySet()) {
			push("phase/" + entry.getKey());
			PhaseDefinition phase = entry.getValue();
			walkList("on_enter", phase.onEnter, false, 1);
			walkList("on_tick", phase.onTick, true, 1);
			walkList("on_exit", phase.onExit, false, 1);
			if (!phase.onDamage.isEmpty()) {
				addCap(SpellCapability.BOSS_ON_DAMAGE);
			}
			walkList("on_damage", phase.onDamage, false, 1);
			checkTransitions(phase);
			pop();
		}
		detectPhaseCycles();
		return finish();
	}

	private void checkStructural() {
		if (definition.phases.isEmpty() || definition.phases.size() > limits.maxPhases()) {
			throw new SpellAnalysisException("Spell phase count must be between 1 and " + limits.maxPhases());
		}
		if (!definition.phases.containsKey(definition.entryPhase)) {
			throw new SpellAnalysisException("Entry phase is not present in phases: " + definition.entryPhase);
		}
		definition.phases.forEach((id, phase) -> {
			if (!id.equals(phase.id)) {
				throw new SpellAnalysisException("Phase key/id mismatch: " + id + " != " + phase.id);
			}
		});
	}

	/**
	 * Legacy precheck lives in {@link SpellEligibility} — the single shared recursive
	 * scan (D9). It is implemented with instanceof only: calling
	 * SpellDefinition.hasLegacyTicker() would initialize the CODEC chain
	 * (SpellActions → YHDanmaku), which is not safe outside FML.
	 */
	private void checkTransitions(PhaseDefinition phase) {
		if (profile != SpellAnalysisProfile.CERTIFICATION) return;
		for (Transition transition : phase.transitions) {
			if (!definition.phases.containsKey(transition.targetPhase())) {
				throw rejected("missing_transition_target",
						"Transition target phase missing: " + transition.targetPhase());
			}
		}
	}

	/** Phase cycles are normal (certification Runtime loops a spell; design §5.4), reported as INFO only. */
	private void detectPhaseCycles() {
		if (profile != SpellAnalysisProfile.CERTIFICATION) return;
		Set<ResourceLocation> visited = new HashSet<>();
		Set<ResourceLocation> stack = new HashSet<>();
		for (ResourceLocation id : definition.phases.keySet()) {
			if (!visited.contains(id)) dfsCycle(id, visited, stack);
		}
	}

	private void dfsCycle(ResourceLocation id, Set<ResourceLocation> visited, Set<ResourceLocation> stack) {
		visited.add(id);
		stack.add(id);
		PhaseDefinition phase = definition.phases.get(id);
		if (phase != null) {
			for (Transition transition : phase.transitions) {
				ResourceLocation target = transition.targetPhase();
				if (!visited.contains(target)) {
					dfsCycle(target, visited, stack);
				} else if (stack.contains(target)) {
					diagnostics.add(SpellDiagnostic.info("phase_cycle", "phase/" + id,
							"Phase transition cycle: " + id + " -> " + target));
				}
			}
		}
		stack.remove(id);
	}

	// ------------------------------------------------------------ generic walk

	private void genericWalk(Object node, int depth) {
		if (node == null) return;
		if (depth > limits.maxDepth()) {
			throw new SpellAnalysisException("Spell nesting exceeds " + limits.maxDepth());
		}
		Class<?> clazz = node.getClass();
		if (clazz == String.class) {
			if (((String) node).length() > limits.maxExpressionLength()) {
				throw new SpellAnalysisException("Spell string/expression exceeds " + limits.maxExpressionLength() + " characters");
			}
			return;
		}
		if (node instanceof Optional<?> opt) {
			if (opt.isPresent()) genericWalk(opt.get(), depth + 1);
			return;
		}
		if (node instanceof List<?> list) {
			for (Object child : list) genericWalk(child, depth + 1);
			return;
		}
		if (node instanceof Map<?, ?> map) {
			for (var entry : map.entrySet()) {
				genericWalk(entry.getKey(), depth + 1);
				genericWalk(entry.getValue(), depth + 1);
			}
			return;
		}
		if (node instanceof Number || node instanceof Boolean || clazz.isEnum()) return;
		if (node instanceof LegacyTickerAction) return; // runtime-only factory/card, never walk
		if (node instanceof SpellAction) wideActions++;
		if (node instanceof NumberProvider p) {
			expressionOps++;
			// historical market counting only counted JSON objects with a "type" key;
			// bare numeric literals decode to Constant and must NOT inflate the action
			// budget (acceptance review issue 1b)
			if (!(p instanceof NumberProviders.Constant)) wideActions++;
		}
		if (node instanceof SpellCondition || node instanceof MoverConfig) wideActions++;
		// only walk mod-owned data types; registries/RLs/Components are opaque
		if (!clazz.getName().startsWith("dev.xkmc.youkaishomecoming")) return;
		for (Field field : clazz.getDeclaredFields()) {
			if (Modifier.isStatic(field.getModifiers())) continue;
			field.setAccessible(true);
			Object value;
			try {
				value = field.get(node);
			} catch (IllegalAccessException e) {
				continue;
			}
			if (value instanceof Number n && isLifetimeField(field.getName())
					&& n.doubleValue() > limits.maxLifetime()) {
				throw new SpellAnalysisException(field.getName() + " exceeds " + limits.maxLifetime());
			}
			genericWalk(value, depth + 1);
		}
	}

	private static boolean isLifetimeField(String name) {
		return name.equals("lifetime") || name.equals("duration") || name.equals("health");
	}

	// ------------------------------------------------------------ semantic walk

	private void walkList(String label, List<SpellAction> list, boolean perTick, long mult) {
		boolean outerGroup = !perTick && !inOneShotGroup;
		if (outerGroup) {
			inOneShotGroup = true;
			burstAccum = 0;
		}
		// per-list deferred hook aggregation: all hook callbacks of the same execution
		// group (one-shot list, or one tick of a recurring list) fire in the same tick,
		// so their bursts must be summed, not maxed (acceptance review issue 5)
		long savedDeferred = deferredBurstAccum;
		deferredBurstAccum = 0;
		push(label);
		int index = 0;
		for (SpellAction action : list) {
			push(Integer.toString(index));
			walkAction(action, perTick, mult, false);
			pop();
			index++;
		}
		pop();
		if (deferredBurstAccum > certMaxDeferredHookBurst) certMaxDeferredHookBurst = deferredBurstAccum;
		deferredBurstAccum = savedDeferred;
		if (outerGroup) {
			inOneShotGroup = false;
			if (burstAccum > certMaxOneShotBurst) certMaxOneShotBurst = burstAccum;
		}
	}

	private void walkAction(SpellAction action, boolean perTick, long mult, boolean insideDisabled) {
		if (action instanceof SpellActions.DisabledAction disabled) {
			// Disabled nodes never execute: no budgets, no capabilities (cert).
			// Market keeps its historical behavior of rejecting banned actions
			// anywhere in the JSON, including inside disabled subtrees.
			if (profile == SpellAnalysisProfile.MARKET) {
				walkAction(disabled.inner(), perTick, mult, true);
			}
			return;
		}
		if (insideDisabled) {
			if (isMarketBanned(action)) {
				throw banned(bannedTypeName(action));
			}
			for (SpellAction child : containerChildrenIncludingHooks(action)) {
				walkAction(child, perTick, mult, true);
			}
			return;
		}
		actions++;
		if (actions > limits.maxActions()) {
			throw new SpellAnalysisException("Spell contains too many actions: " + actions);
		}
		boolean handled = true;
		if (action instanceof FireDanmakuAction a) {
			handleFire(a, perTick, mult);
		} else if (action instanceof FireLaserAction a) {
			handleLaser(a, perTick, mult);
		} else if (action instanceof FireTextDanmakuAction a) {
			handleText(a, perTick, mult);
		} else if (action instanceof SpawnShooterAction a) {
			handleShooter(a, perTick, mult);
		} else if (action instanceof SpellActions.RepeatAction a) {
			long count = boundCount(a.count(), "repeat count");
			walkList("body", a.body(), perTick, satMul(mult, count));
		} else if (action instanceof BurstAction a) {
			// conservative: all waves counted within the same tick accounting
			walkList("body", a.body(), perTick, satMul(mult, a.waves()));
		} else if (action instanceof DelayAction a) {
			if (profile == SpellAnalysisProfile.CERTIFICATION
					&& !NumberBounds.resolve(a.delayTicks()).bounded()) {
				diagnostics.add(SpellDiagnostic.warning("unbounded_delay", path(),
						"delay_ticks cannot be bounded statically"));
			}
			walkList("body", a.body(), perTick, mult);
		} else if (action instanceof SpellActions.ConditionalAction a) {
			walkList("if_true", a.ifTrue(), perTick, mult);
			walkList("if_false", a.ifFalse(), perTick, mult);
		} else if (action instanceof SpellActions.SequenceAction a) {
			walkList("actions", a.actions(), perTick, mult);
		} else if (action instanceof TeleportAction || action instanceof TeleportRandomAction) {
			addCap(SpellCapability.TELEPORT);
		} else if (action instanceof ConfineTargetAction) {
			addCap(SpellCapability.CONFINED_TARGET);
		} else if (action instanceof EraseEnemyDanmakuAction) {
			addCap(SpellCapability.ERASE_ENEMY_DANMAKU);
		} else if (action instanceof SpellActions.ClearScreen) {
			addCap(SpellCapability.CLEAR_SCREEN);
		} else if (action instanceof SetEntityFlagAction) {
			addCap(SpellCapability.SET_ENTITY_FLAG);
		} else if (action instanceof SpellActions.ForceSpell) {
			checkMarketBanned(action);
			addCap(SpellCapability.FORCE_SPELL);
		} else if (action instanceof SpellActions.FireSpell) {
			checkMarketBanned(action);
			addCap(SpellCapability.FIRE_SPELL);
		} else if (action instanceof RunCommandAction) {
			checkMarketBanned(action);
			addCap(SpellCapability.RUN_COMMAND);
		} else if (action instanceof SetSpellCircleAction) {
			addCap(SpellCapability.SET_SPELL_CIRCLE);
		} else if (action instanceof ShowSpellTitleAction) {
			addCap(SpellCapability.SHOW_SPELL_TITLE);
		} else if (action instanceof YsmRenderAction) {
			addCap(SpellCapability.YSM_RENDER);
		} else if (action instanceof LegacyTickerAction) {
			// rejected by the certification precheck (D9); market keeps the historical
			// behavior of accepting it (runtime factory is lost → no-op)
			handled = true;
		} else if (isSafeNoCostAction(action)) {
			// SetVariable / AddVariable / ForcePhase / Noop / PlaySoundAction:
			// explicitly whitelisted, no capability, no cost.
			handled = true;
		} else {
			handled = false;
		}
		if (!handled) {
			// unknown action: fail closed in certification — a future action type that
			// the analyzer does not understand must never pass as "safe" (reverse of
			// the unknown-capability default-DENY principle)
			if (profile == SpellAnalysisProfile.CERTIFICATION) {
				throw rejected("unknown_action",
						"Unsupported action in certification: " + action.getClass().getName());
			}
		}
	}

	private static boolean isSafeNoCostAction(SpellAction action) {
		return action instanceof SpellActions.SetVariable
				|| action instanceof SpellActions.AddVariable
				|| action instanceof SpellActions.ForcePhase
				|| action instanceof SpellActions.NoopAction
				|| action instanceof SpellActions.PlaySoundAction;
	}

	/**
	 * All nested actions reachable from this action, including fire/laser hook lists.
	 * Used by the MARKET disabled penetration scan so banned actions cannot hide
	 * inside on_expiry / on_trail / on_hit subtrees.
	 */
	private static List<SpellAction> containerChildrenIncludingHooks(SpellAction action) {
		if (action instanceof SpellActions.ConditionalAction a) {
			List<SpellAction> out = new ArrayList<>(a.ifTrue());
			out.addAll(a.ifFalse());
			return out;
		}
		if (action instanceof SpellActions.SequenceAction a) return a.actions();
		if (action instanceof SpellActions.RepeatAction a) return a.body();
		if (action instanceof DelayAction a) return a.body();
		if (action instanceof BurstAction a) return a.body();
		if (action instanceof SpawnShooterAction a) return a.body();
		if (action instanceof FireDanmakuAction f) return hookLists(f.onExpiry(), f.onTrail(), f.onHitEntity(), f.onHitBlock());
		if (action instanceof FireLaserAction f) return hookLists(f.onExpiry(), f.onTrail(), f.onHitEntity(), f.onHitBlock());
		return List.of();
	}

	private static List<SpellAction> hookLists(Optional<List<SpellAction>> expiry, Optional<List<SpellAction>> trail,
											   Optional<List<SpellAction>> hitEntity, Optional<List<SpellAction>> hitBlock) {
		List<SpellAction> out = new ArrayList<>();
		expiry.ifPresent(out::addAll);
		trail.ifPresent(out::addAll);
		hitEntity.ifPresent(out::addAll);
		hitBlock.ifPresent(out::addAll);
		return out;
	}

	// ------------------------------------------------------------ fire actions

	private void handleFire(FireDanmakuAction a, boolean perTick, long mult) {
		addCap(SpellCapability.BASE_FIRE);
		checkOrigin(a.origin());
		long count = boundCount(a.count(), "fire_danmaku count");
		long outer = profile == SpellAnalysisProfile.CERTIFICATION
				? boundOptionalCount(a.outerCount(), "outer_count") : 1;
		long contrib = satMul(satMul(mult, outer), count);
		long lifetimeUpper = boundLifetimeUpper(a.lifetime());
		bucketSpawns(contrib, perTick, lifetimeUpper);
		walkHooks(a.onExpiry(), a.onTrail(), a.trailInterval(),
				a.onHitEntity(), a.onHitBlock(),
				a.hitBehaviorEntity(), a.hitBehaviorBlock(),
				contrib, lifetimeUpper, perTick, mult);
	}

	private void handleLaser(FireLaserAction a, boolean perTick, long mult) {
		addCap(SpellCapability.BASE_FIRE);
		checkOrigin(a.origin());
		long contrib = mult;
		long lifetimeUpper = boundLifetimeUpper(a.lifetime());
		bucketSpawns(contrib, perTick, lifetimeUpper);
		walkHooks(a.onExpiry(), a.onTrail(), a.trailInterval(),
				a.onHitEntity(), a.onHitBlock(),
				a.hitBehaviorEntity(), a.hitBehaviorBlock(),
				contrib, lifetimeUpper, perTick, mult);
	}

	private void handleText(FireTextDanmakuAction a, boolean perTick, long mult) {
		addCap(SpellCapability.BASE_FIRE);
		checkOrigin(a.origin());
		long contrib = mult;
		long lifetimeUpper = boundLifetimeUpper(a.lifetime());
		bucketSpawns(contrib, perTick, lifetimeUpper);
	}

	private void walkHooks(Optional<List<SpellAction>> onExpiry, Optional<List<SpellAction>> onTrail,
						   int trailInterval, Optional<List<SpellAction>> onHitEntity,
						   Optional<List<SpellAction>> onHitBlock,
						   HitBehavior hitBehaviorEntity, HitBehavior hitBehaviorBlock,
						   long contrib, long lifetimeUpper, boolean perTick, long mult) {
		if (onExpiry.isPresent()) {
			addCap(SpellCapability.HOOK_ON_EXPIRY);
			walkHook("on_expiry", onExpiry.get(), contrib, perTick, mult);
		}
		if (onTrail.isPresent()) {
			addCap(SpellCapability.HOOK_ON_TRAIL);
			long perProjectile = ceilDiv(lifetimeUpper, Math.max(1, trailInterval));
			walkHook("on_trail", onTrail.get(), satMul(contrib, perProjectile), perTick, mult);
		}
		if (onHitEntity.isPresent() || onHitBlock.isPresent()) {
			addCap(SpellCapability.HOOK_ON_HIT);
			// CONTINUE may hit repeatedly up to the server hard cap (design §10)
			long hits = hitBehaviorEntity == HitBehavior.CONTINUE || hitBehaviorBlock == HitBehavior.CONTINUE
					? limits.maxHitsPerProjectile() : 1;
			long execs = satMul(contrib, hits);
			if (onHitEntity.isPresent()) walkHook("on_hit_entity", onHitEntity.get(), execs, perTick, mult);
			if (onHitBlock.isPresent()) walkHook("on_hit_block", onHitBlock.get(), execs, perTick, mult);
		}
	}

	private void walkHook(String label, List<SpellAction> list, long executions, boolean perTick, long mult) {
		if (profile == SpellAnalysisProfile.CERTIFICATION) {
			if (perTick) hookExecutionsPerTick = satAdd(hookExecutionsPerTick, executions);
			else hookExecutionsOnce = satAdd(hookExecutionsOnce, executions);
		}
		// market keeps the historical flat multiplier (no hook fanout); cert amplifies.
		// executions already carries the full path multiplier (mult × outer × count × hits),
		// so the child multiplier is executions itself — multiplying by mult again would
		// square the parent factor (acceptance review issue 4).
		long childMult = profile == SpellAnalysisProfile.MARKET ? mult : executions;
		hookDepth++;
		long beforePerTick = certPerTickSpawns;
		long beforeOneShot = certOneShotSpawns;
		try {
			walkList(label, list, perTick, childMult);
		} finally {
			hookDepth--;
		}
		// a single hook callback batch may fire in one tick: e.g. on_enter spawns 1000
		// projectiles with identical lifetimes and each on_expiry spawns 10 more — the
		// whole batch expires together. Conservative principle: all eligible projectiles
		// of the batch trigger together, so the hook-derived burst must be tracked as a
		// deferred tick burst. Bursts of the same execution group are summed in
		// deferredBurstAccum and committed by walkList (acceptance review issues 4/5).
		long hookBurst = satAdd(certPerTickSpawns - beforePerTick, certOneShotSpawns - beforeOneShot);
		deferredBurstAccum = satAdd(deferredBurstAccum, hookBurst);
	}

	private void handleShooter(SpawnShooterAction a, boolean perTick, long mult) {
		long count = boundCount(a.count(), "shooter count");
		long shooterCount = satMul(mult, count);
		if (profile == SpellAnalysisProfile.MARKET) {
			marketShooters = satAdd(marketShooters, shooterCount);
			walkList("body", a.body(), true, shooterCount);
			return;
		}
		certShooters = satAdd(certShooters, shooterCount);
		long lifetime = Math.max(0, a.lifetime());
		long window = limits.certificationWindowTicks();
		// body per single shooter per tick (multiplier 1: the body semantics are
		// "each shooter executes the body once per tick")
		long savedPerTick = certPerTickSpawns;
		long savedOneShot = certOneShotSpawns;
		long savedBurst = certMaxOneShotBurst;
		walkList("body", a.body(), true, 1);
		long bodyPerShooterTick = certPerTickSpawns - savedPerTick;
		certPerTickSpawns = savedPerTick;
		certOneShotSpawns = savedOneShot;
		certMaxOneShotBurst = savedBurst;
		// concurrency and totals depend on whether shooters are spawned once
		// (on_enter/on_exit) or recurring (on_tick). The recurring model multiplies by
		// the alive cohort count × window; it may slightly overestimate window edges
		// but must never underestimate.
		long shooterPeak;
		long bodyPerGlobalTick;
		long bodyTotal;
		if (perTick) {
			shooterPeak = satMul(shooterCount, Math.min(lifetime, window));
			bodyPerGlobalTick = satMul(bodyPerShooterTick, shooterPeak);
			bodyTotal = satMul(bodyPerGlobalTick, window);
			// recurring shooter entities spawn every tick and join the ordinary tick burst
			certRecurringShooterEntitySpawns = satAdd(certRecurringShooterEntitySpawns, shooterCount);
		} else {
			shooterPeak = shooterCount;
			bodyPerGlobalTick = satMul(bodyPerShooterTick, shooterCount);
			bodyTotal = satMul(bodyPerGlobalTick, lifetime);
			// the shooter entities themselves are one-shot spawns of this tick group
			if (inOneShotGroup) burstAccum = satAdd(burstAccum, shooterCount);
		}
		// concurrent peak across actions sums (fail-closed: two shooters in one tick
		// group may coexist, even if cross-phase summation slightly overestimates)
		certPeakShooters = satAdd(certPeakShooters, shooterPeak);
		certShooterTickSpawns = satAdd(certShooterTickSpawns, bodyPerGlobalTick);
		// body totals must NOT feed the peak formula: window-total spawns are not
		// concurrently alive (acceptance review issue 3)
		certShooterTotalSpawns = satAdd(certShooterTotalSpawns, bodyTotal);
		// peak alive: shooter entities + body bullets alive at once
		certShooterPeakAlive = satAdd(certShooterPeakAlive, shooterPeak);
		certShooterPeakAlive = satAdd(certShooterPeakAlive,
				satMul(bodyPerGlobalTick, Math.min(Math.max(1, lifetimeUpperMax), window)));
	}

	// ------------------------------------------------------------ helpers

	private void bucketSpawns(long contrib, boolean perTick, long lifetimeUpper) {
		if (profile == SpellAnalysisProfile.MARKET) {
			marketProjectiles = satAdd(marketProjectiles, contrib);
			return;
		}
		if (perTick) {
			certPerTickSpawns = satAdd(certPerTickSpawns, contrib);
		} else {
			certOneShotSpawns = satAdd(certOneShotSpawns, contrib);
			// one-shot spawns of the same tick group (a full on_enter/on_exit list runs
			// in a single tick) count toward maxSpawnPerTick; hook-derived spawns are
			// spread over projectile lifetimes and must not join the same-tick group
			if (hookDepth == 0 && inOneShotGroup) burstAccum = satAdd(burstAccum, contrib);
		}
		if (lifetimeUpper > lifetimeUpperMax) lifetimeUpperMax = lifetimeUpper;
	}

	private void checkOrigin(OriginConfig origin) {
		if (origin == null) return;
		switch (origin.mode()) {
			case TARGET -> addCap(SpellCapability.ORIGIN_TARGET);
			case ABSOLUTE -> addCap(SpellCapability.ORIGIN_ABSOLUTE);
			default -> {
			}
		}
	}

	/**
	 * Market-banned detection uses instanceof only: type IDs would force
	 * SpellActions class init, which is not safe outside FML.
	 */
	private static boolean isMarketBanned(SpellAction action) {
		return action instanceof RunCommandAction
				|| action instanceof SpellActions.ForceSpell
				|| action instanceof SpellActions.FireSpell;
	}

	private static String bannedTypeName(SpellAction action) {
		if (action instanceof RunCommandAction) return "run_command";
		if (action instanceof SpellActions.ForceSpell) return "force_spell";
		if (action instanceof SpellActions.FireSpell) return "fire_spell";
		return "unknown";
	}

	private void checkMarketBanned(SpellAction action) {
		if (profile == SpellAnalysisProfile.MARKET) {
			throw banned(bannedTypeName(action));
		}
	}

	private SpellAnalysisException banned(String typeId) {
		return new SpellAnalysisException("Automatic market imports may not use action: " + typeId);
	}

	/** Market: literal-only counts ≤ maxRepeat, truncated like the historical getAsLong (acceptance review issue 1c). Cert: any bounded provider. */
	private long boundCount(NumberProvider provider, String label) {
		if (profile == SpellAnalysisProfile.MARKET) {
			if (provider instanceof NumberProviders.Constant c) {
				long value = (long) c.value();
				if (value < 0 || value > limits.maxRepeat()) {
					throw new SpellAnalysisException(label + " exceeds " + limits.maxRepeat());
				}
				return value;
			}
			throw new SpellAnalysisException(label + " must be a bounded numeric literal");
		}
		NumberBounds bounds = NumberBounds.resolve(provider);
		if (!bounds.bounded()) {
			throw rejected("unbounded_value", label + " cannot be bounded statically");
		}
		return Math.max(0, (long) Math.ceil(bounds.max()));
	}

	private long boundOptionalCount(Optional<NumberProvider> provider, String label) {
		if (provider.isEmpty()) return 1;
		NumberBounds bounds = NumberBounds.resolve(provider.get());
		if (!bounds.bounded()) {
			throw rejected("unbounded_value", label + " cannot be bounded statically");
		}
		return Math.max(0, (long) Math.ceil(bounds.max()));
	}

	/**
	 * Lifetime bound. MARKET: historical hard limits are exclusively enforced by the
	 * raw-JSON guard (HistoricalMarketJsonGuard); the analyzer must not add a second,
	 * stricter rule for object-form providers (e.g. random lifetime with max > 12000
	 * passed historically and must keep passing — acceptance review issue B). Only
	 * certification performs full boundedness analysis.
	 */
	private long boundLifetimeUpper(NumberProvider provider) {
		if (profile == SpellAnalysisProfile.MARKET) return 0;
		NumberBounds bounds = NumberBounds.resolve(provider);
		if (!bounds.bounded()) {
			throw rejected("unbounded_value", "lifetime cannot be bounded statically");
		}
		long upper = Math.max(0, (long) Math.ceil(bounds.max()));
		if (upper > limits.maxLifetime()) {
			throw new SpellAnalysisException("lifetime exceeds " + limits.maxLifetime());
		}
		return upper;
	}

	// ------------------------------------------------------------ finalize

	private SpellAnalysis finish() {
		long totalSpawnUpperBound;
		long projectileTicks;
		long peakAliveUpperBound;
		long maxSpawnPerTick;
		long hookExecutionUpperBound;
		if (profile == SpellAnalysisProfile.MARKET) {
			if (wideActions > limits.maxActions()) {
				throw new SpellAnalysisException("Spell contains too many actions: " + wideActions);
			}
			if (marketProjectiles > limits.maxTotalProjectiles()) {
				throw new SpellAnalysisException("Spell projectile budget exceeds " + limits.maxTotalProjectiles());
			}
			if (marketShooters > limits.maxShooters()) {
				throw new SpellAnalysisException("Spell shooter budget exceeds " + limits.maxShooters());
			}
			totalSpawnUpperBound = marketProjectiles;
			maxSpawnPerTick = marketProjectiles;
			peakAliveUpperBound = marketProjectiles;
			projectileTicks = marketProjectiles;
			hookExecutionUpperBound = 0;
		} else {
			if (actions > limits.maxActions()) {
				throw new SpellAnalysisException("Spell contains too many actions: " + actions);
			}
			// shooter budget covers both the total spawn count and the concurrent peak
			// (recurring shooters: count × min(lifetime, window) alive at once)
			long shooterBudget = Math.max(certShooters, certPeakShooters);
			if (shooterBudget > limits.maxShooters()) {
				throw new SpellAnalysisException("Spell shooter budget exceeds " + limits.maxShooters());
			}
			long window = limits.certificationWindowTicks();
			long life = Math.max(1, lifetimeUpperMax);
			// single-tick spawn ceiling — the runtime executes on_enter and on_tick in
			// the SAME server tick, and alive shooter bodies fire in the same tick as
			// top-level on_tick spawns; when events cannot be proven to interleave,
			// they are summed, not maxed (acceptance review issue 3):
			//   ordinaryTickBurst = top-level per-tick + shooter body + recurring shooter entities
			//   phaseEntryBurst  = one-shot tick group + ordinaryTickBurst
			//   deferredHookBurst = largest single-tick hook-derived batch (summed per group)
			long ordinaryTickBurst = satAdd(satAdd(certPerTickSpawns, certShooterTickSpawns),
					certRecurringShooterEntitySpawns);
			long phaseEntryBurst = satAdd(certMaxOneShotBurst, ordinaryTickBurst);
			maxSpawnPerTick = Math.max(phaseEntryBurst,
					Math.max(ordinaryTickBurst, certMaxDeferredHookBurst));
			// totals: direct one-shot + direct per-tick × window + shooter body totals.
			// Shooter body totals must NOT appear in the peak formula — window totals are
			// not concurrently alive (acceptance review issue 3).
			totalSpawnUpperBound = satAdd(satAdd(certOneShotSpawns, certShooterTotalSpawns),
					satMul(certPerTickSpawns, window));
			peakAliveUpperBound = satAdd(satAdd(certOneShotSpawns,
					satMul(certPerTickSpawns, Math.min(life, window))), certShooterPeakAlive);
			projectileTicks = satMul(totalSpawnUpperBound, life);
			hookExecutionUpperBound = satAdd(hookExecutionsOnce, satMul(hookExecutionsPerTick, window));
			if (maxSpawnPerTick > limits.maxSpawnPerTick()) {
				throw new SpellAnalysisException("Certification rejected: maxSpawnPerTick " + maxSpawnPerTick
						+ " exceeds limit " + limits.maxSpawnPerTick());
			}
			if (peakAliveUpperBound > limits.maxPeakAlive()) {
				throw new SpellAnalysisException("Certification rejected: peakAliveUpperBound " + peakAliveUpperBound
						+ " exceeds limit " + limits.maxPeakAlive());
			}
			if (projectileTicks > limits.maxProjectileTicks()) {
				throw new SpellAnalysisException("Certification rejected: projectileTicks " + projectileTicks
						+ " exceeds limit " + limits.maxProjectileTicks());
			}
			if (hookExecutionUpperBound > limits.maxHookExecutions()) {
				throw new SpellAnalysisException("Certification rejected: hookExecutions " + hookExecutionUpperBound
						+ " exceeds limit " + limits.maxHookExecutions());
			}
			for (SpellCapability cap : capabilities) {
				SpellCapabilityPolicy policy = SpellCapabilityPolicies.defaultPolicy(cap);
				if (!policy.allowsCertification()) {
					throw new SpellAnalysisException("Certification rejected: capability " + cap.id()
							+ " policy " + policy);
				}
			}
		}
		return new SpellAnalysis(totalSpawnUpperBound, projectileTicks,
				(int) Math.min(peakAliveUpperBound, Integer.MAX_VALUE),
				(int) Math.min(maxSpawnPerTick, Integer.MAX_VALUE),
				hookExecutionUpperBound, expressionOps,
				(double) projectileTicks, (double) peakAliveUpperBound,
				(double) totalSpawnUpperBound + hookExecutionUpperBound,
				Set.copyOf(capabilities), List.copyOf(diagnostics));
	}

	private void addCap(SpellCapability cap) {
		capabilities.add(cap);
	}

	private SpellAnalysisException rejected(String code, String message) {
		return new SpellAnalysisException(message, List.of(
				SpellDiagnostic.error(code, path(), message)));
	}

	private void push(String segment) {
		segments.addLast(segment);
	}

	private void pop() {
		segments.removeLast();
	}

	private String path() {
		return String.join("/", segments);
	}

	private static long satAdd(long a, long b) {
		try {
			return Math.addExact(a, b);
		} catch (ArithmeticException e) {
			return Long.MAX_VALUE;
		}
	}

	private static long satMul(long a, long b) {
		if (a == 0 || b == 0) return 0;
		try {
			return Math.multiplyExact(a, b);
		} catch (ArithmeticException e) {
			return Long.MAX_VALUE;
		}
	}

	private static long ceilDiv(long a, long b) {
		return b <= 0 ? 0 : (a + b - 1) / b;
	}
}
