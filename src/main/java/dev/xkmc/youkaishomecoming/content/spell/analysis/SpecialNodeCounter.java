package dev.xkmc.youkaishomecoming.content.spell.analysis;

import dev.xkmc.youkaishomecoming.content.spell.action.BurstAction;
import dev.xkmc.youkaishomecoming.content.spell.action.ConfineTargetAction;
import dev.xkmc.youkaishomecoming.content.spell.action.DelayAction;
import dev.xkmc.youkaishomecoming.content.spell.action.EraseEnemyDanmakuAction;
import dev.xkmc.youkaishomecoming.content.spell.action.FireDanmakuAction;
import dev.xkmc.youkaishomecoming.content.spell.action.FireLaserAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SetEntityFlagAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpawnShooterAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellActions;
import dev.xkmc.youkaishomecoming.content.spell.action.TeleportAction;
import dev.xkmc.youkaishomecoming.content.spell.action.TeleportRandomAction;
import dev.xkmc.youkaishomecoming.content.spell.definition.PhaseDefinition;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;

import java.util.List;
import java.util.Optional;

/**
 * Counts "special" action nodes in a spell definition: the capability classes
 * that are EXPERIMENTAL by default (teleport, confine, erase, clear, flags,
 * force/fire spell, boss on_damage hooks, fire/laser hooks). run_command is NOT
 * counted — it stays operator-only forever.
 * <p>
 * The count of the boss's own spell definition is the op-node quota a
 * boss-drop draft card unlocks: the draft may use at most that many special
 * nodes, enforced on editor save and certification start.
 */
public final class SpecialNodeCounter {

	/**
	 * The capability set that is EXPERIMENTAL by default (denied in certification
	 * unless the draft quota covers it). run_command intentionally stays OUT —
	 * it is OP_ONLY forever and never unlocked by draft quotas.
	 */
	public static final java.util.Set<SpellCapability> EXPERIMENTAL_CAPS = java.util.Set.of(
			SpellCapability.BOSS_ON_DAMAGE,
			SpellCapability.TELEPORT,
			SpellCapability.CONFINED_TARGET,
			SpellCapability.ERASE_ENEMY_DANMAKU,
			SpellCapability.CLEAR_SCREEN,
			SpellCapability.SET_ENTITY_FLAG,
			SpellCapability.FORCE_SPELL,
			SpellCapability.FIRE_SPELL
	);

	private SpecialNodeCounter() {
	}

	public static int count(SpellDefinition definition) {
		int total = 0;
		for (PhaseDefinition phase : definition.phases.values()) {
			total += count(phase.onEnter);
			total += count(phase.onTick);
			total += count(phase.onExit);
			if (!phase.onDamage.isEmpty()) {
				total++; // BOSS_ON_DAMAGE
			}
			total += count(phase.onDamage);
		}
		return total;
	}

	private static int count(List<SpellAction> actions) {
		int total = 0;
		for (SpellAction action : actions) {
			total += count(action);
		}
		return total;
	}

	private static int count(SpellAction action) {
		if (action instanceof TeleportAction || action instanceof TeleportRandomAction
				|| action instanceof ConfineTargetAction || action instanceof EraseEnemyDanmakuAction
				|| action instanceof SpellActions.ClearScreen || action instanceof SetEntityFlagAction
				|| action instanceof SpellActions.ForceSpell || action instanceof SpellActions.FireSpell) {
			return 1;
		}
		if (action instanceof SpellActions.ConditionalAction cond) {
			return count(cond.ifTrue()) + count(cond.ifFalse());
		}
		if (action instanceof SpellActions.SequenceAction seq) {
			return count(seq.actions());
		}
		if (action instanceof SpellActions.RepeatAction rep) {
			return count(rep.body());
		}
		if (action instanceof SpellActions.DisabledAction disabled) {
			return count(disabled.inner());
		}
		if (action instanceof DelayAction delay) {
			return count(delay.body());
		}
		if (action instanceof BurstAction burst) {
			return count(burst.body());
		}
		if (action instanceof SpawnShooterAction shooter) {
			return count(shooter.body());
		}
		if (action instanceof FireDanmakuAction danmaku) {
			return hookCount(danmaku.onExpiry()) + hookCount(danmaku.onTrail())
					+ hookCount(danmaku.onHitEntity()) + hookCount(danmaku.onHitBlock());
		}
		if (action instanceof FireLaserAction laser) {
			return hookCount(laser.onExpiry()) + hookCount(laser.onTrail())
					+ hookCount(laser.onHitEntity()) + hookCount(laser.onHitBlock());
		}
		return 0;
	}

	private static int hookCount(Optional<List<SpellAction>> hook) {
		if (hook.isEmpty() || hook.get().isEmpty()) {
			return 0;
		}
		return 1 + count(hook.get());
	}
}
