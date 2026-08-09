package dev.xkmc.youkaishomecoming.content.spell.analysis;

import dev.xkmc.youkaishomecoming.content.spell.action.BurstAction;
import dev.xkmc.youkaishomecoming.content.spell.action.DelayAction;
import dev.xkmc.youkaishomecoming.content.spell.action.FireDanmakuAction;
import dev.xkmc.youkaishomecoming.content.spell.action.FireLaserAction;
import dev.xkmc.youkaishomecoming.content.spell.action.RunCommandAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpawnShooterAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellActions;
import dev.xkmc.youkaishomecoming.content.spell.definition.PhaseDefinition;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;

import java.util.List;

/**
 * Counts OP (run_command) action nodes in a spell definition. The count is the
 * quantity-controlled resource: boss-drop drafts carry an op node quota in
 * their NBT, and both the editor save and the certification start enforce
 * {@code count <= quota}.
 */
public final class OpNodeCounter {

	private OpNodeCounter() {
	}

	public static int count(SpellDefinition definition) {
		int total = 0;
		for (PhaseDefinition phase : definition.phases.values()) {
			total += count(phase.onEnter);
			total += count(phase.onTick);
			total += count(phase.onExit);
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
		if (action instanceof RunCommandAction) {
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
			return count(danmaku.onExpiry().orElse(List.of()))
					+ count(danmaku.onTrail().orElse(List.of()))
					+ count(danmaku.onHitEntity().orElse(List.of()))
					+ count(danmaku.onHitBlock().orElse(List.of()));
		}
		if (action instanceof FireLaserAction laser) {
			return count(laser.onExpiry().orElse(List.of()))
					+ count(laser.onTrail().orElse(List.of()))
					+ count(laser.onHitEntity().orElse(List.of()))
					+ count(laser.onHitBlock().orElse(List.of()));
		}
		return 0;
	}
}
