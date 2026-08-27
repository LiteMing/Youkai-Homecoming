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

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
			for (SpellCapability cap : EXPERIMENTAL_CAPS) {
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

	/**
	 * The capability set unlockable through a boss-draft's special-node quota:
	 * on_damage, teleport, erase enemy danmaku and clear screen (legitimate
	 * certification tactics). Confine/entity flags/force/fire spell and
	 * run_command are OP_ONLY — boss-authoring freedom, never unlocked for
	 * players. Danmaku hooks (HOOK_ON_*) stay ALLOW but still count toward the
	 * quota (quantity control).
	 */
	public static final java.util.Set<SpellCapability> EXPERIMENTAL_CAPS = java.util.Set.of(
			SpellCapability.BOSS_ON_DAMAGE,
			SpellCapability.TELEPORT,
			SpellCapability.ERASE_ENEMY_DANMAKU,
			SpellCapability.CLEAR_SCREEN
	);

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
		SpellCapability capability = directCapability(unwrap(action));
		return capability == null ? SpellCapabilityPolicy.ALLOW
				: SpellCapabilityPolicies.defaultPolicy(capability);
	}

	/** Compatibility alias: direct experimental nodes consume the legacy quota. */
	public static boolean consumesQuota(SpellAction action) {
		SpellCapability capability = directCapability(unwrap(action));
		return capability != null && EXPERIMENTAL_CAPS.contains(capability);
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
		SpellCapability capability = directCapability(inner);
		if (policy == SpellCapabilityPolicy.EXPERIMENTAL) summary.addExperimental(capability);
		else if (policy == SpellCapabilityPolicy.OP_ONLY) summary.operatorOnlyNodes++;
		else if (policy == SpellCapabilityPolicy.DENY) summary.deniedNodes++;
		else summary.ordinaryNodes++;
		// Broken nodes are a subset of denied ones; counted separately so the editor
		// can tell "you salvaged an unreadable fragment" from "you used a banned node".
		if (capability == SpellCapability.BROKEN_NODE) summary.brokenNodes++;

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

	private static SpellCapability directCapability(SpellAction action) {
		if (action instanceof SpellActions.BrokenAction) return SpellCapability.BROKEN_NODE;
		if (action instanceof FireDanmakuAction || action instanceof FireLaserAction
				|| action instanceof FireTextDanmakuAction) return SpellCapability.BASE_FIRE;
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
