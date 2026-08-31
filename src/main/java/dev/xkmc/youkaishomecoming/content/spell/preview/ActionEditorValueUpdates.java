package dev.xkmc.youkaishomecoming.content.spell.preview;

import dev.xkmc.youkaishomecoming.content.spell.action.SpellActions;
import dev.xkmc.youkaishomecoming.content.spell.definition.DanmakuColorAnimation;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberProvider;
import dev.xkmc.youkaishomecoming.content.spell.definition.OriginConfig;
import net.minecraft.resources.ResourceLocation;

final class ActionEditorValueUpdates {

	private ActionEditorValueUpdates() {
	}

	static OriginConfig withOriginMode(OriginConfig current, OriginConfig.OriginMode mode) {
		return new OriginConfig(mode, current.offsetX(), current.offsetY(), current.offsetZ(), current.rotation());
	}

	static DanmakuColorAnimation.HueCycle withHuePeriod(
			DanmakuColorAnimation.HueCycle current, NumberProvider value) {
		return new DanmakuColorAnimation.HueCycle(value, current.hueOffset(), current.indexStep(),
				current.saturation(), current.brightness(), current.alpha());
	}

	static DanmakuColorAnimation.HueCycle withHueOffset(
			DanmakuColorAnimation.HueCycle current, NumberProvider value) {
		return new DanmakuColorAnimation.HueCycle(current.period(), value, current.indexStep(),
				current.saturation(), current.brightness(), current.alpha());
	}

	static DanmakuColorAnimation.HueCycle withHueIndexStep(
			DanmakuColorAnimation.HueCycle current, NumberProvider value) {
		return new DanmakuColorAnimation.HueCycle(current.period(), current.hueOffset(), value,
				current.saturation(), current.brightness(), current.alpha());
	}

	static DanmakuColorAnimation.HueCycle withHueSaturation(
			DanmakuColorAnimation.HueCycle current, NumberProvider value) {
		return new DanmakuColorAnimation.HueCycle(current.period(), current.hueOffset(), current.indexStep(),
				value, current.brightness(), current.alpha());
	}

	static DanmakuColorAnimation.HueCycle withHueBrightness(
			DanmakuColorAnimation.HueCycle current, NumberProvider value) {
		return new DanmakuColorAnimation.HueCycle(current.period(), current.hueOffset(), current.indexStep(),
				current.saturation(), value, current.alpha());
	}

	static DanmakuColorAnimation.HueCycle withHueAlpha(
			DanmakuColorAnimation.HueCycle current, NumberProvider value) {
		return new DanmakuColorAnimation.HueCycle(current.period(), current.hueOffset(), current.indexStep(),
				current.saturation(), current.brightness(), value);
	}

	static SpellActions.ForcePhase withPhaseId(SpellActions.ForcePhase current, ResourceLocation id) {
		return new SpellActions.ForcePhase(id, current.clearScreen());
	}

	static SpellActions.ForcePhase withPhaseClearScreen(SpellActions.ForcePhase current, boolean value) {
		return new SpellActions.ForcePhase(current.phaseId(), value);
	}

	static SpellActions.ForceSpell withSpellId(SpellActions.ForceSpell current, ResourceLocation id) {
		return new SpellActions.ForceSpell(id, current.clearScreen());
	}

	static SpellActions.ForceSpell withSpellClearScreen(SpellActions.ForceSpell current, boolean value) {
		return new SpellActions.ForceSpell(current.spellId(), value);
	}
}
