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

	private final SpellDefinition definition;	private final SpellAnalysisProfile profile;
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
	private long hookExecutionsOnce;
	private long hookExecutionsPerTick;
	private long lifetimeUpperMax;
	private long expressionOps;

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
		if (profile == SpellAnalysisProfile.CERTIFICATION && hasLegacyTicker(definition)) {
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
	 * Legacy detection equivalent to SpellDefinition.hasLegacyTicker, but implemented
	 * with instanceof only: calling the instance method would initialize SpellDefinition's
	 * CODEC chain (SpellActions → YHDanmaku), which is not safe outside FML.
	 * Also covers legacy tickers inside Burst/SpawnShooter bodies, which the original
	 * container recursion missed.
	 */
	private static boolean hasLegacyTicker(SpellDefinition def) {
		for (PhaseDefinition phase : def.phases.values()) {
			if (actionsHaveLegacy(phase.onEnter)
					|| actionsHaveLegacy(phase.onTick)
					|| actionsHaveLegacy(phase.onExit)
					|| actionsHaveLegacy(phase.onDamage)) {
				return true;
			}
		}
		return false;
	}

	private static boolean actionsHaveLegacy(List<SpellAction> actions) {
		for (SpellAction action : actions) {
			if (actionHasLegacy(action)) return true;
		}
		return false;
	}

	private static boolean actionHasLegacy(SpellAction action) {
		if (action instanceof LegacyTickerAction) return true;
		if (action instanceof SpellActions.ConditionalAction cond) {
			return actionsHaveLegacy(cond.ifTrue()) || actionsHaveLegacy(cond.ifFalse());
		}
		if (action instanceof SpellActions.SequenceAction seq) return actionsHaveLegacy(seq.actions());
		if (action instanceof SpellActions.RepeatAction rep) return actionsHaveLegacy(rep.body());
		if (action instanceof SpellActions.DisabledAction disabled) return actionHasLegacy(disabled.inner());
		if (action instanceof DelayAction delay) return actionsHaveLegacy(delay.body());
		if (action instanceof BurstAction burst) return actionsHaveLegacy(burst.body());
		if (action instanceof SpawnShooterAction shooter) return actionsHaveLegacy(shooter.body());
		return false;
	}

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
		if (node instanceof NumberProvider) {
			wideActions++;
			expressionOps++;
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
		push(label);
		int index = 0;
		for (SpellAction action : list) {
			push(Integer.toString(index));
			walkAction(action, perTick, mult, false);
			pop();
			index++;
		}
		pop();
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
			for (SpellAction child : containerChildren(action)) {
				walkAction(child, perTick, mult, true);
			}
			return;
		}
		actions++;
		if (actions > limits.maxActions()) {
			throw new SpellAnalysisException("Spell contains too many actions: " + actions);
		}
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
		}
		// SetVariable / AddVariable / ForcePhase / Noop / PlaySoundAction: no capability, no cost.
		// LegacyTickerAction is rejected by the certification precheck (D9); the market
		// profile keeps its historical behavior of accepting it (runtime factory is lost,
		// so it degrades to a no-op there).
	}

	private static List<SpellAction> containerChildren(SpellAction action) {
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
		return List.of();
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
		// market keeps the historical flat multiplier (no hook fanout); cert amplifies
		long childMult = profile == SpellAnalysisProfile.MARKET ? mult : satMul(mult, executions);
		walkList(label, list, perTick, childMult);
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
		// body fires every tick while the shooter lives; spawns are per-tick for
		// concurrency accounting and total over lifetime for the duration projection
		long before = certPerTickSpawns;
		walkList("body", a.body(), true, shooterCount);
		long bodyPerTick = certPerTickSpawns - before;
		certOneShotSpawns = satAdd(certOneShotSpawns, satMul(bodyPerTick, Math.max(0, a.lifetime())));
	}

	// ------------------------------------------------------------ helpers

	private void bucketSpawns(long contrib, boolean perTick, long lifetimeUpper) {
		if (profile == SpellAnalysisProfile.MARKET) {
			marketProjectiles = satAdd(marketProjectiles, contrib);
			return;
		}
		if (perTick) certPerTickSpawns = satAdd(certPerTickSpawns, contrib);
		else certOneShotSpawns = satAdd(certOneShotSpawns, contrib);
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

	/** Market: literal-only counts ≤ maxRepeat (historical behavior). Cert: any bounded provider. */
	private long boundCount(NumberProvider provider, String label) {
		if (profile == SpellAnalysisProfile.MARKET) {
			if (provider instanceof NumberProviders.Constant c) {
				long value = (long) Math.ceil(c.value());
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

	/** Certification only: lifetime must be bounded and within maxLifetime. Market: no check. */
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
			if (certShooters > limits.maxShooters()) {
				throw new SpellAnalysisException("Spell shooter budget exceeds " + limits.maxShooters());
			}
			long window = limits.certificationWindowTicks();
			long life = Math.max(1, lifetimeUpperMax);
			maxSpawnPerTick = certPerTickSpawns;
			totalSpawnUpperBound = satAdd(certOneShotSpawns, satMul(certPerTickSpawns, window));
			peakAliveUpperBound = satAdd(certOneShotSpawns, satMul(certPerTickSpawns, Math.min(life, window)));
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
