package dev.xkmc.youkaishomecoming.content.spell.analysis;

import dev.xkmc.youkaishomecoming.content.spell.action.BurstAction;
import dev.xkmc.youkaishomecoming.content.spell.action.DelayAction;
import dev.xkmc.youkaishomecoming.content.spell.action.FireDanmakuAction;
import dev.xkmc.youkaishomecoming.content.spell.action.FireLaserAction;
import dev.xkmc.youkaishomecoming.content.spell.action.LegacyTickerAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpawnShooterAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellActions;
import dev.xkmc.youkaishomecoming.content.spell.definition.PhaseDefinition;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;

import java.util.List;

/**
 * Shared structural eligibility checks (D9). The single recursive legacy scan used
 * by both {@link SpellAnalyzer} and {@link SpellHash} — do NOT keep two copies.
 * <p>
 * Covers every action container: Conditional/Sequence/Repeat/Delay/Burst/
 * SpawnShooter bodies and the fire/laser hook lists. DisabledAction subtrees are
 * scanned too: a LegacyTickerAction inside a disabled node still holds a
 * non-serializable factory and must fail the round-trip precheck.
 */
public final class SpellEligibility {

	private SpellEligibility() {
	}

	public static boolean hasLegacyTicker(SpellDefinition def) {
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
		if (action instanceof FireDanmakuAction fire) {
			if (fire.onExpiry().isPresent() && actionsHaveLegacy(fire.onExpiry().get())) return true;
			if (fire.onTrail().isPresent() && actionsHaveLegacy(fire.onTrail().get())) return true;
			if (fire.onHitEntity().isPresent() && actionsHaveLegacy(fire.onHitEntity().get())) return true;
			return fire.onHitBlock().isPresent() && actionsHaveLegacy(fire.onHitBlock().get());
		}
		if (action instanceof FireLaserAction laser) {
			if (laser.onExpiry().isPresent() && actionsHaveLegacy(laser.onExpiry().get())) return true;
			if (laser.onTrail().isPresent() && actionsHaveLegacy(laser.onTrail().get())) return true;
			if (laser.onHitEntity().isPresent() && actionsHaveLegacy(laser.onHitEntity().get())) return true;
			return laser.onHitBlock().isPresent() && actionsHaveLegacy(laser.onHitBlock().get());
		}
		return false;
	}
}
