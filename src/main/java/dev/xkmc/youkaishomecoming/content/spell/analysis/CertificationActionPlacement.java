package dev.xkmc.youkaishomecoming.content.spell.analysis;

import dev.xkmc.youkaishomecoming.content.spell.action.BurstAction;
import dev.xkmc.youkaishomecoming.content.spell.action.DelayAction;
import dev.xkmc.youkaishomecoming.content.spell.action.FireDanmakuAction;
import dev.xkmc.youkaishomecoming.content.spell.action.FireLaserAction;
import dev.xkmc.youkaishomecoming.content.spell.action.HoldSourceAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SetSpellHealthAction;
import dev.xkmc.youkaishomecoming.content.spell.action.ShowSpellTitleAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpawnShooterAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellActions;
import dev.xkmc.youkaishomecoming.content.spell.definition.PhaseDefinition;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Certification-only placement contract for phase initialization actions. */
public final class CertificationActionPlacement {

	private CertificationActionPlacement() {
	}

	public static void validate(SpellDefinition definition) {
		for (PhaseDefinition phase : definition.phases.values()) {
			validateList(phase.onEnter, true, phase.id + "/on_enter");
			validateList(phase.onTick, false, phase.id + "/on_tick");
			validateList(phase.onExit, false, phase.id + "/on_exit");
			validateList(phase.onDamage, false, phase.id + "/on_damage");
		}
	}

	private static void validateList(List<SpellAction> actions, boolean initializationContext, String path) {
		for (int i = 0; i < actions.size(); i++) {
			validateAction(actions.get(i), initializationContext, path + "/" + i);
		}
	}

	private static void validateAction(SpellAction action, boolean initializationContext, String path) {
		if (action instanceof SpellActions.DisabledAction) return;
		if ((action instanceof SetSpellHealthAction || action instanceof ShowSpellTitleAction)
				&& !initializationContext) {
			throw new PlacementException(path);
		}
		if (action instanceof SpellActions.SequenceAction sequence) {
			validateList(sequence.actions(), initializationContext, path + "/actions");
			return;
		}
		if (action instanceof SpellActions.ConditionalAction conditional) {
			validateList(conditional.ifTrue(), initializationContext, path + "/if_true");
			validateList(conditional.ifFalse(), initializationContext, path + "/if_false");
			return;
		}
		if (action instanceof SpellActions.RepeatAction repeat) {
			validateList(repeat.body(), initializationContext, path + "/body");
			return;
		}
		if (action instanceof BurstAction burst) {
			validateList(burst.body(), initializationContext && burst.interval() == 0, path + "/body");
			return;
		}
		for (SpellAction child : deferredChildren(action)) {
			validateAction(child, false, path + "/event");
		}
	}

	private static List<SpellAction> deferredChildren(SpellAction action) {
		if (action instanceof DelayAction delay) return delay.body();
		if (action instanceof SpawnShooterAction shooter) return shooter.body();
		if (action instanceof HoldSourceAction hold) return hold.onRelease();
		if (action instanceof FireDanmakuAction fire) {
			return hooks(fire.onExpiry(), fire.onTrail(), fire.onHitEntity(), fire.onHitBlock());
		}
		if (action instanceof FireLaserAction laser) {
			return hooks(laser.onExpiry(), laser.onTrail(), laser.onHitEntity(), laser.onHitBlock());
		}
		return List.of();
	}

	@SafeVarargs
	private static List<SpellAction> hooks(Optional<List<SpellAction>>... hooks) {
		List<SpellAction> result = new ArrayList<>();
		for (Optional<List<SpellAction>> hook : hooks) hook.ifPresent(result::addAll);
		return result;
	}

	public static final class PlacementException extends IllegalArgumentException {
		public PlacementException(String path) {
			super("set_spell_health and show_spell_title may only execute during on_enter: " + path);
		}
	}
}
