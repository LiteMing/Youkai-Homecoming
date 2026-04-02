package dev.xkmc.youkaishomecoming.compat.kubejs.spell;

import dev.xkmc.youkaishomecoming.content.spell.action.SpellAction;
import dev.xkmc.youkaishomecoming.content.spell.condition.SpellCondition;
import dev.xkmc.youkaishomecoming.content.spell.definition.PhaseDefinition;
import dev.xkmc.youkaishomecoming.content.spell.definition.Transition;
import dev.xkmc.youkaishomecoming.content.spell.definition.TransitionMode;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class PhaseBuilderJS {

	private final ResourceLocation id;
	private final List<SpellAction> onEnter = new ArrayList<>();
	private final List<SpellAction> onTick = new ArrayList<>();
	private final List<SpellAction> onExit = new ArrayList<>();
	private final List<Transition> transitions = new ArrayList<>();

	PhaseBuilderJS(ResourceLocation id) {
		this.id = id;
	}

	public PhaseBuilderJS onEnter(Consumer<dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext> callback) {
		onEnter.add(new KubeJSSpellActions.JSAction(callback));
		return this;
	}

	public PhaseBuilderJS onTick(Consumer<dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext> callback) {
		onTick.add(new KubeJSSpellActions.JSAction(callback));
		return this;
	}

	public PhaseBuilderJS onExit(Consumer<dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext> callback) {
		onExit.add(new KubeJSSpellActions.JSAction(callback));
		return this;
	}

	public PhaseBuilderJS onEnterAction(SpellAction action) {
		onEnter.add(action);
		return this;
	}

	public PhaseBuilderJS onTickAction(SpellAction action) {
		onTick.add(action);
		return this;
	}

	public PhaseBuilderJS onExitAction(SpellAction action) {
		onExit.add(action);
		return this;
	}

	public PhaseBuilderJS transition(String targetPhaseLocalId,
									  Function<dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext, Boolean> condition) {
		return transition(targetPhaseLocalId, condition, TransitionMode.IMMEDIATE);
	}

	public PhaseBuilderJS transition(String targetPhaseLocalId,
									  Function<dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext, Boolean> condition,
									  TransitionMode mode) {
		// Resolve target phase relative to parent definition id
		ResourceLocation target = resolvePhaseId(targetPhaseLocalId);
		transitions.add(new Transition(
				new KubeJSSpellConditions.JSCondition(ctx -> condition.apply(ctx)),
				target,
				mode
		));
		return this;
	}

	public PhaseBuilderJS transitionOnHealthBelow(String targetPhaseLocalId, float threshold) {
		return transitionOnHealthBelow(targetPhaseLocalId, threshold, TransitionMode.IMMEDIATE);
	}

	public PhaseBuilderJS transitionOnHealthBelow(String targetPhaseLocalId, float threshold, TransitionMode mode) {
		ResourceLocation target = resolvePhaseId(targetPhaseLocalId);
		transitions.add(new Transition(
				new dev.xkmc.youkaishomecoming.content.spell.condition.SpellConditions.HealthBelow(threshold),
				target,
				mode
		));
		return this;
	}

	public PhaseBuilderJS transitionOnTickElapsed(String targetPhaseLocalId, int ticks) {
		return transitionOnTickElapsed(targetPhaseLocalId, ticks, TransitionMode.IMMEDIATE);
	}

	public PhaseBuilderJS transitionOnTickElapsed(String targetPhaseLocalId, int ticks, TransitionMode mode) {
		ResourceLocation target = resolvePhaseId(targetPhaseLocalId);
		transitions.add(new Transition(
				new dev.xkmc.youkaishomecoming.content.spell.condition.SpellConditions.TickElapsed(ticks),
				target,
				mode
		));
		return this;
	}

	private ResourceLocation resolvePhaseId(String localId) {
		// If it already contains ':', it's a full ResourceLocation
		if (localId.contains(":")) {
			return new ResourceLocation(localId);
		}
		// Otherwise resolve relative to this phase's namespace/parent path
		String parentPath = id.getPath();
		int lastSlash = parentPath.lastIndexOf('/');
		String basePath = lastSlash >= 0 ? parentPath.substring(0, lastSlash) : parentPath;
		return new ResourceLocation(id.getNamespace(), basePath + "/" + localId);
	}

	PhaseDefinition build() {
		return new PhaseDefinition(id, onEnter, onTick, onExit, transitions);
	}
}
