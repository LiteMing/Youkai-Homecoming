package dev.xkmc.youkaishomecoming.compat.kubejs.spell;

import dev.xkmc.youkaishomecoming.content.spell.action.SpellAction;
import dev.xkmc.youkaishomecoming.content.spell.condition.SpellCondition;
import dev.xkmc.youkaishomecoming.content.spell.definition.PhaseDefinition;
import dev.xkmc.youkaishomecoming.content.spell.definition.Transition;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public class SpellPhaseBuilder {

	private final ResourceLocation spellId;
	private final ResourceLocation id;
	private final List<SpellAction> onEnter = new ArrayList<>();
	private final List<SpellAction> onTick = new ArrayList<>();
	private final List<SpellAction> onExit = new ArrayList<>();
	private final List<Transition> transitions = new ArrayList<>();

	public SpellPhaseBuilder(ResourceLocation spellId, ResourceLocation id) {
		this.spellId = spellId;
		this.id = id;
	}

	public SpellPhaseBuilder onEnter(Object action) {
		KubeJSSpellSupport.appendActions(onEnter, action);
		return this;
	}

	public SpellPhaseBuilder onTick(Object action) {
		KubeJSSpellSupport.appendActions(onTick, action);
		return this;
	}

	public SpellPhaseBuilder onExit(Object action) {
		KubeJSSpellSupport.appendActions(onExit, action);
		return this;
	}

	public SpellPhaseBuilder transition(String targetPhase, Object condition) {
		return transition(targetPhase, condition, null);
	}

	public SpellPhaseBuilder transition(String targetPhase, Object condition, Object mode) {
		SpellCondition resolvedCondition = KubeJSSpellSupport.toCondition(condition);
		ResourceLocation resolvedPhase = KubeJSSpellSupport.parsePhaseId(spellId, targetPhase);
		transitions.add(new Transition(resolvedCondition, resolvedPhase, KubeJSSpellSupport.toMode(mode)));
		return this;
	}

	public PhaseDefinition build() {
		return new PhaseDefinition(id, onEnter, onTick, onExit, transitions);
	}

	public void copyFrom(PhaseDefinition definition) {
		onEnter.clear();
		onEnter.addAll(definition.onEnter);
		onTick.clear();
		onTick.addAll(definition.onTick);
		onExit.clear();
		onExit.addAll(definition.onExit);
		transitions.clear();
		transitions.addAll(definition.transitions);
	}
}
