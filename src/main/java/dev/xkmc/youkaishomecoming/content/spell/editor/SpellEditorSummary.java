package dev.xkmc.youkaishomecoming.content.spell.editor;

import dev.xkmc.youkaishomecoming.content.spell.action.SpellAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellActions;
import dev.xkmc.youkaishomecoming.content.spell.condition.SpellCondition;
import dev.xkmc.youkaishomecoming.content.spell.condition.SpellConditions;
import dev.xkmc.youkaishomecoming.content.spell.definition.Transition;

public class SpellEditorSummary {

	private SpellEditorSummary() {
	}

	public static String summarizeAction(SpellAction action) {
		if (action instanceof SpellActions.NoopAction) return "noop";
		if (action instanceof SpellActions.ClearScreen) return "clear screen";
		if (action instanceof SpellActions.SetVariable set) return "set " + set.key() + " = " + trim(set.value());
		if (action instanceof SpellActions.AddVariable add) return "add " + add.key() + " += " + trim(add.delta());
		if (action instanceof SpellActions.ForcePhase phase) return "force " + phase.phaseId();
		if (action instanceof SpellActions.PlaySoundAction sound) return "sound " + sound.soundId();
		if (action instanceof SpellActions.ConditionalAction conditional) {
			return "if " + summarizeCondition(conditional.condition()) +
					" ? " + conditional.ifTrue().size() + ":" + conditional.ifFalse().size();
		}
		if (action instanceof SpellActions.SequenceAction sequence) return "sequence x" + sequence.actions().size();
		if (action instanceof dev.xkmc.youkaishomecoming.content.spell.action.LegacyTickerAction) {
			return "legacy ticker";
		}
		return action.getClass().getSimpleName();
	}

	public static String summarizeCondition(SpellCondition condition) {
		if (condition instanceof SpellConditions.AlwaysCondition always) return always.value() ? "always" : "never";
		if (condition instanceof SpellConditions.HealthBelow below) return "hp < " + trim(below.threshold());
		if (condition instanceof SpellConditions.HealthAbove above) return "hp > " + trim(above.threshold());
		if (condition instanceof SpellConditions.TickElapsed tick) return "tick >= " + tick.ticks();
		if (condition instanceof SpellConditions.DistanceBelow below) return "dist < " + trim(below.distance());
		if (condition instanceof SpellConditions.DistanceAbove above) return "dist > " + trim(above.distance());
		if (condition instanceof SpellConditions.HitCountCondition hit) return "hit >= " + hit.count();
		if (condition instanceof SpellConditions.VariableCheck check) return check.key() + " " + check.op() + " " + trim(check.value());
		if (condition instanceof SpellConditions.NotCondition not) return "not (" + summarizeCondition(not.condition()) + ")";
		if (condition instanceof SpellConditions.AndCondition and) return "and x" + and.conditions().size();
		if (condition instanceof SpellConditions.OrCondition or) return "or x" + or.conditions().size();
		return condition.getClass().getSimpleName();
	}

	public static String summarizeTransition(Transition transition) {
		return summarizeCondition(transition.condition()) + " -> " + transition.targetPhase() +
				" [" + transition.mode().name().toLowerCase(java.util.Locale.ROOT) + "]";
	}

	private static String trim(double value) {
		long integer = (long) value;
		return value == integer ? Long.toString(integer) : Double.toString(value);
	}
}
