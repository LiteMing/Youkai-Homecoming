package dev.xkmc.youkaishomecoming.compat.kubejs.spell;

import dev.xkmc.youkaishomecoming.content.spell.action.SpellAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellActions;

public class KubeJSSpellActions {

	public SpellAction setVariable(String key, double value) {
		return new SpellActions.SetVariable(key, value);
	}

	public SpellAction addVariable(String key, double delta) {
		return new SpellActions.AddVariable(key, delta);
	}

	public SpellAction clearScreen() {
		return new SpellActions.ClearScreen();
	}

	public SpellAction forcePhase(String phaseId) {
		return new SpellActions.ForcePhase(KubeJSSpellSupport.parseId(phaseId));
	}

	public SpellAction playSound(String soundId) {
		return new SpellActions.PlaySoundAction(KubeJSSpellSupport.parseId(soundId), 1.0f, 1.0f);
	}

	public SpellAction playSound(String soundId, float volume, float pitch) {
		return new SpellActions.PlaySoundAction(KubeJSSpellSupport.parseId(soundId), volume, pitch);
	}

	public SpellAction sequence(Object... actions) {
		return new SpellActions.SequenceAction(KubeJSSpellSupport.toActionList(actions));
	}

	public SpellAction conditional(Object condition, Object ifTrue, Object ifFalse) {
		return new SpellActions.ConditionalAction(
				KubeJSSpellSupport.toCondition(condition),
				KubeJSSpellSupport.toActionList(ifTrue),
				KubeJSSpellSupport.toActionList(ifFalse)
		);
	}

	public SpellAction noop() {
		return new SpellActions.NoopAction();
	}
}
