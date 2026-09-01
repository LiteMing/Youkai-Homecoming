package dev.xkmc.youkaishomecoming.content.spell.analysis;

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
import dev.xkmc.youkaishomecoming.content.spell.definition.PhaseDefinition;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.definition.BulletProvider;
import dev.xkmc.youkaishomecoming.content.spell.definition.MoverConfig;
import dev.xkmc.youkaishomecoming.content.spell.definition.MoverConfigs;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberProvider;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberProviders;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.TextDanmakuEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Counts ordinary, advanced-hook, experimental and privileged spell nodes. */
public final class SpecialNodeCounter {

	public record Summary(int actionNodes, int ordinaryNodes, int advancedHookNodes,
			int experimentalNodes, int operatorOnlyNodes, int deniedNodes, int brokenNodes,
			Map<SpellCapability, Integer> experimentalByCapability) {

		public Summary {
			experimentalByCapability = Map.copyOf(experimentalByCapability);
		}

		public int experimentalCount(SpellCapability capability) {
			return experimentalByCapability.getOrDefault(capability, 0);
		}

		public Summary plus(Summary other) {
			EnumMap<SpellCapability, Integer> counts = new EnumMap<>(SpellCapability.class);
			for (SpellCapability cap : SpellCapability.values()) {
				counts.put(cap, experimentalCount(cap) + other.experimentalCount(cap));
			}
			return new Summary(actionNodes + other.actionNodes,
					ordinaryNodes + other.ordinaryNodes,
					advancedHookNodes + other.advancedHookNodes,
					experimentalNodes + other.experimentalNodes,
					operatorOnlyNodes + other.operatorOnlyNodes,
					deniedNodes + other.deniedNodes,
					brokenNodes + other.brokenNodes, counts);
		}
	}

	private SpecialNodeCounter() {
	}

	public static int count(SpellDefinition definition) {
		return summarize(definition).experimentalNodes();
	}

	public static Summary summarize(SpellDefinition definition) {
		MutableSummary summary = new MutableSummary();
		for (PhaseDefinition phase : definition.phases.values()) {
			accumulate(summary, phase);
		}
		return summary.freeze();
	}

	public static Summary summarize(Iterable<SpellDefinition> definitions) {
		MutableSummary summary = new MutableSummary();
		for (SpellDefinition definition : definitions) {
			for (PhaseDefinition phase : definition.phases.values()) accumulate(summary, phase);
		}
		return summary.freeze();
	}

	public static Summary summarize(PhaseDefinition phase) {
		MutableSummary summary = new MutableSummary();
		accumulate(summary, phase);
		return summary.freeze();
	}

	/** Policy of the action node itself. Nested branches are classified separately. */
	public static SpellCapabilityPolicy policy(SpellAction action) {
		SpellCapabilityPolicy result = SpellCapabilityPolicy.ALLOW;
		for (SpellCapability capability : capabilities(unwrap(action))) {
			result = stricter(result, SpellCapabilityPolicies.currentPolicy(capability));
		}
		return result;
	}

	/** Compatibility alias: direct experimental nodes consume the legacy quota. */
	public static boolean consumesQuota(SpellAction action) {
		return policy(action) == SpellCapabilityPolicy.EXPERIMENTAL;
	}

	private static void accumulate(MutableSummary summary, PhaseDefinition phase) {
		accumulate(summary, phase.onEnter);
		accumulate(summary, phase.onTick);
		accumulate(summary, phase.onExit);
		if (!phase.onDamage.isEmpty()) {
			summary.addExperimental(SpellCapability.BOSS_ON_DAMAGE);
		}
		accumulate(summary, phase.onDamage);
	}

	private static void accumulate(MutableSummary summary, List<SpellAction> actions) {
		for (SpellAction action : actions) {
			accumulate(summary, action);
		}
	}

	private static void accumulate(MutableSummary summary, SpellAction action) {
		summary.actionNodes++;
		SpellAction inner = unwrap(action);
		SpellCapabilityPolicy policy = policy(inner);
		SpellCapability capability = primaryCapability(inner, policy);
		if (policy == SpellCapabilityPolicy.EXPERIMENTAL) summary.addExperimental(capability);
		else if (policy == SpellCapabilityPolicy.OP_ONLY) summary.operatorOnlyNodes++;
		else if (policy == SpellCapabilityPolicy.DENY) summary.deniedNodes++;
		else summary.ordinaryNodes++;
		// Broken nodes are a subset of denied ones; counted separately so the editor
		// can tell "you salvaged an unreadable fragment" from "you used a banned node".
		if (capabilities(inner).contains(SpellCapability.BROKEN_NODE)) summary.brokenNodes++;

		if (inner instanceof SpellActions.ConditionalAction cond) {
			accumulate(summary, cond.ifTrue());
			accumulate(summary, cond.ifFalse());
		}
		if (inner instanceof SpellActions.SequenceAction seq) {
			accumulate(summary, seq.actions());
		}
		if (inner instanceof SpellActions.RepeatAction rep) {
			accumulate(summary, rep.body());
		}
		if (inner instanceof DelayAction delay) {
			accumulate(summary, delay.body());
		}
		if (inner instanceof BurstAction burst) {
			accumulate(summary, burst.body());
		}
		if (inner instanceof SpawnShooterAction shooter) {
			accumulate(summary, shooter.body());
		}
		if (inner instanceof FireDanmakuAction danmaku) {
			accumulateHook(summary, danmaku.onExpiry());
			accumulateHook(summary, danmaku.onTrail());
			accumulateHook(summary, danmaku.onHitEntity());
			accumulateHook(summary, danmaku.onHitBlock());
		}
		if (inner instanceof FireLaserAction laser) {
			accumulateHook(summary, laser.onExpiry());
			accumulateHook(summary, laser.onTrail());
			accumulateHook(summary, laser.onHitEntity());
			accumulateHook(summary, laser.onHitBlock());
		}
	}

	private static void accumulateHook(MutableSummary summary, Optional<List<SpellAction>> hook) {
		if (hook.isPresent() && !hook.get().isEmpty()) {
			summary.ordinaryNodes++;
			summary.advancedHookNodes++;
			accumulate(summary, hook.get());
		}
	}

	private static SpellAction unwrap(SpellAction action) {
		while (action instanceof SpellActions.DisabledAction disabled) {
			action = disabled.inner();
		}
		return action;
	}

	/** Capability of the action itself; nested actions are counted separately. */
	static SpellCapability capability(SpellAction action) {
		return directCapability(unwrap(action));
	}

	/** All capability markers contributed by one action, including data-shape rules. */
	static Set<SpellCapability> capabilities(SpellAction action) {
		SpellAction inner = unwrap(action);
		EnumSet<SpellCapability> result = EnumSet.noneOf(SpellCapability.class);
		SpellCapability direct = directCapability(inner);
		if (direct != null) result.add(direct);
		if (inner instanceof SpellActions.SetVariable set && containsTargetCoordinate(set.value())) {
			result.add(SpellCapability.TARGET_COORDINATE);
		} else if (inner instanceof SpellActions.ConditionalAction conditional
				&& containsTargetCoordinate(conditional.condition())) {
			result.add(SpellCapability.TARGET_COORDINATE);
		} else if (inner instanceof SpellActions.RepeatAction repeat
				&& containsTargetCoordinate(repeat.count())) {
			result.add(SpellCapability.TARGET_COORDINATE);
		} else if (inner instanceof DelayAction delay && containsTargetCoordinate(delay.delayTicks())) {
			result.add(SpellCapability.TARGET_COORDINATE);
		} else if (inner instanceof dev.xkmc.youkaishomecoming.content.spell.action.HoldSourceAction hold
				&& containsTargetCoordinate(hold.duration())) {
			result.add(SpellCapability.TARGET_COORDINATE);
		}
		if (inner instanceof FireDanmakuAction danmaku) {
			addEmitterCapabilities(result, danmaku.origin(), danmaku.mover(), danmaku.lifetime(),
					danmaku.size(), 1.0, danmaku.count(), danmaku.speed(), danmaku.angleOffset(),
					danmaku.spread(), danmaku.elevation(), danmaku.outerCount().orElse(null),
					danmaku.tiltAngle().orElse(null));
		} else if (inner instanceof FireLaserAction laser) {
			addEmitterCapabilities(result, laser.origin(), laser.mover(), laser.lifetime(),
					laser.thickness(), 1.0, laser.length(), laser.angleOffset(), laser.elevation());
		} else if (inner instanceof FireTextDanmakuAction text) {
			addEmitterCapabilities(result, text.origin(), text.mover(), text.lifetime(),
					text.size(), TextDanmakuEntity.DEFAULT_SIZE, text.angleOffset(), text.elevation(), text.roll());
		} else if (inner instanceof SpawnShooterAction shooter) {
			NumberProvider lifetime = NumberProvider.constant(shooter.lifetime());
			addEmitterCapabilities(result, shooter.origin(), shooter.mover(), lifetime, null,
					1.0, shooter.count(), shooter.speed(), shooter.angleOffset(), shooter.spread(), shooter.elevation());
		}
		return Set.copyOf(result);
	}

	private static void addEmitterCapabilities(EnumSet<SpellCapability> result,
			dev.xkmc.youkaishomecoming.content.spell.definition.OriginConfig origin,
			Optional<MoverConfig> mover, NumberProvider lifetimeProvider,
			NumberProvider sizeProvider, double sizeDefault, NumberProvider... providers) {
		if (origin != null) {
			if (origin.mode() == dev.xkmc.youkaishomecoming.content.spell.definition.OriginConfig.OriginMode.TARGET) {
				result.add(SpellCapability.ORIGIN_TARGET);
			}
			if (containsTargetCoordinate(origin.offsetX()) || containsTargetCoordinate(origin.offsetY())
					|| containsTargetCoordinate(origin.offsetZ()) || containsTargetCoordinate(origin.rotation())) {
				result.add(SpellCapability.TARGET_COORDINATE);
			}
		}
		for (NumberProvider provider : providers) {
			if (containsTargetCoordinate(provider)) result.add(SpellCapability.TARGET_COORDINATE);
		}
		if (sizeProvider != null && containsTargetCoordinate(sizeProvider)) {
			result.add(SpellCapability.TARGET_COORDINATE);
		}
		if (containsTargetCoordinate(lifetimeProvider)) result.add(SpellCapability.TARGET_COORDINATE);
		if (lifetimeExceeds200(lifetimeProvider)) result.add(SpellCapability.LONG_LIFETIME);
		if (sizeProvider != null && differsFromDefault(sizeProvider, sizeDefault)) {
			result.add(SpellCapability.SIZED_PROJECTILE);
		}
		if (mover != null && mover.isPresent()) {
			MoverConfig config = mover.get();
			if (containsTrackingMover(config)) result.add(SpellCapability.TRACKING_MOVER);
			if (containsTargetCoordinate(config)) result.add(SpellCapability.TARGET_COORDINATE);
		}
	}

	private static boolean differsFromDefault(NumberProvider provider, double defaultValue) {
		if (provider instanceof NumberProviders.Constant constant) {
			return Math.abs(constant.value() - defaultValue) > 1.0e-6;
		}
		return true;
	}

	private static boolean lifetimeExceeds200(NumberProvider provider) {
		NumberBounds bounds = NumberBounds.resolve(provider);
		return !bounds.bounded() || bounds.max() > 200;
	}

	private static boolean containsTargetCoordinate(Object value) {
		return containsTargetCoordinate(value, java.util.Collections.newSetFromMap(new IdentityHashMap<>()));
	}

	private static boolean containsTargetCoordinate(Object value, Set<Object> seen) {
		if (value == null || !seen.add(value)) return false;
		if (value instanceof NumberProviders.TargetX || value instanceof NumberProviders.TargetY
				|| value instanceof NumberProviders.TargetZ || value instanceof NumberProviders.TargetHeight) return true;
		if (value instanceof String text) {
			String normalized = text.replace("_", "").toLowerCase(java.util.Locale.ROOT);
			return normalized.contains("targetx") || normalized.contains("targety")
					|| normalized.contains("targetz") || normalized.contains("targetxyz");
		}
		if (value instanceof Optional<?> optional) return optional.isPresent() && containsTargetCoordinate(optional.get(), seen);
		if (value instanceof Iterable<?> iterable) {
			for (Object child : iterable) if (containsTargetCoordinate(child, seen)) return true;
			return false;
		}
		Class<?> type = value.getClass();
		if (!type.getName().startsWith("dev.xkmc.youkaishomecoming")) return false;
		for (Field field : type.getDeclaredFields()) {
			if (Modifier.isStatic(field.getModifiers())) continue;
			try {
				field.setAccessible(true);
				if (containsTargetCoordinate(field.get(value), seen)) return true;
			} catch (ReflectiveOperationException ignored) {
			}
		}
		return false;
	}

	private static boolean containsTrackingMover(MoverConfig mover) {
		return containsTrackingMover(mover, java.util.Collections.newSetFromMap(new IdentityHashMap<>()));
	}

	private static boolean containsTrackingMover(Object value, Set<Object> seen) {
		if (value == null || !seen.add(value)) return false;
		if (value instanceof MoverConfigs.HomingMoverConfig) return true;
		if (value instanceof Optional<?> optional) return optional.isPresent() && containsTrackingMover(optional.get(), seen);
		if (value instanceof Iterable<?> iterable) {
			for (Object child : iterable) if (containsTrackingMover(child, seen)) return true;
			return false;
		}
		Class<?> type = value.getClass();
		if (!type.getName().startsWith("dev.xkmc.youkaishomecoming")) return false;
		for (Field field : type.getDeclaredFields()) {
			if (Modifier.isStatic(field.getModifiers())) continue;
			try {
				field.setAccessible(true);
				if (containsTrackingMover(field.get(value), seen)) return true;
			} catch (ReflectiveOperationException ignored) {
			}
		}
		return false;
	}

	private static SpellCapability primaryCapability(SpellAction action, SpellCapabilityPolicy policy) {
		for (SpellCapability capability : capabilities(action)) {
			if (SpellCapabilityPolicies.currentPolicy(capability) == policy) return capability;
		}
		return directCapability(action);
	}

	private static SpellCapabilityPolicy stricter(SpellCapabilityPolicy left, SpellCapabilityPolicy right) {
		return rank(right) > rank(left) ? right : left;
	}

	private static int rank(SpellCapabilityPolicy policy) {
		return switch (policy) {
			case ALLOW -> 0;
			case EXPERIMENTAL -> 1;
			case OP_ONLY -> 2;
			case DENY -> 3;
		};
	}

	private static SpellCapability directCapability(SpellAction action) {
		if (action instanceof SpellActions.BrokenAction) return SpellCapability.BROKEN_NODE;
		if (action instanceof FireDanmakuAction danmaku) {
			return isExperimentalBullet(danmaku.bulletType())
					? SpellCapability.EXPERIMENTAL_FIRE : SpellCapability.BASE_FIRE;
		}
		if (action instanceof FireLaserAction || action instanceof FireTextDanmakuAction
				|| action instanceof SpawnShooterAction) return SpellCapability.EXPERIMENTAL_FIRE;
		if (action instanceof TeleportAction || action instanceof TeleportRandomAction) return SpellCapability.TELEPORT;
		if (action instanceof ConfineTargetAction) return SpellCapability.CONFINED_TARGET;
		if (action instanceof EraseEnemyDanmakuAction) return SpellCapability.ERASE_ENEMY_DANMAKU;
		if (action instanceof SpellActions.ClearScreen) return SpellCapability.CLEAR_SCREEN;
		if (action instanceof SetEntityFlagAction) return SpellCapability.SET_ENTITY_FLAG;
		if (action instanceof SpellActions.ForcePhase) return SpellCapability.FORCE_PHASE;
		if (action instanceof SpellActions.ForceSpell) return SpellCapability.FORCE_SPELL;
		if (action instanceof SpellActions.FireSpell) return SpellCapability.FIRE_SPELL;
		if (action instanceof RunCommandAction) return SpellCapability.RUN_COMMAND;
		if (action instanceof SetSpellCircleAction) return SpellCapability.SET_SPELL_CIRCLE;
		if (action instanceof ShowSpellTitleAction) return SpellCapability.SHOW_SPELL_TITLE;
		if (action instanceof YsmRenderAction) return SpellCapability.YSM_RENDER;
		if (action instanceof LegacyTickerAction) return SpellCapability.LEGACY_TICKER;
		return null;
	}

	private static boolean isExperimentalBullet(BulletProvider provider) {
		if (provider instanceof BulletProvider.Constant constant) {
			return !constant.bullet().isBillboard();
		}
		if (provider instanceof BulletProvider.Indexed indexed) {
			return indexed.palette().stream().anyMatch(bullet -> !bullet.isBillboard());
		}
		if (provider instanceof BulletProvider.RandomChoice random) {
			return random.palette().stream().anyMatch(bullet -> !bullet.isBillboard());
		}
		// Unknown providers must fail closed: a future provider may select geometry
		// that is not safe for the ordinary player-facing budget.
		return true;
	}

	private static final class MutableSummary {
		private int actionNodes;
		private int ordinaryNodes;
		private int advancedHookNodes;
		private int experimentalNodes;
		private int operatorOnlyNodes;
		private int deniedNodes;
		private int brokenNodes;
		private final EnumMap<SpellCapability, Integer> experimentalByCapability =
				new EnumMap<>(SpellCapability.class);

		private void addExperimental(SpellCapability capability) {
			experimentalNodes++;
			if (capability != null) experimentalByCapability.merge(capability, 1, Integer::sum);
		}

		private Summary freeze() {
			return new Summary(actionNodes, ordinaryNodes, advancedHookNodes,
					experimentalNodes, operatorOnlyNodes, deniedNodes, brokenNodes,
					experimentalByCapability);
		}
	}
}
