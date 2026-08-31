package dev.xkmc.youkaishomecoming.content.spell.preview;

import dev.xkmc.youkaishomecoming.content.spell.action.SpellActions;
import dev.xkmc.youkaishomecoming.content.spell.definition.DanmakuColorAnimation;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberProvider;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberProviders;
import dev.xkmc.youkaishomecoming.content.spell.definition.OriginConfig;
import net.minecraft.resources.ResourceLocation;

public final class ActionEditorValueUpdatesTest {

	private ActionEditorValueUpdatesTest() {
	}

	public static int runAllTests() {
		int checks = 0;

		OriginConfig origin = new OriginConfig(OriginConfig.OriginMode.CASTER,
				constant(1), constant(2), constant(3), constant(4));
		OriginConfig changedOrigin = ActionEditorValueUpdates.withOriginMode(
				origin, OriginConfig.OriginMode.ABSOLUTE);
		check("EDITOR_ORIGIN_MODE_CHANGES", changedOrigin.mode() == OriginConfig.OriginMode.ABSOLUTE);
		checks++;
		check("EDITOR_ORIGIN_MODE_PRESERVES_OFFSETS",
				value(changedOrigin.offsetX()) == 1 && value(changedOrigin.offsetY()) == 2
						&& value(changedOrigin.offsetZ()) == 3 && value(changedOrigin.rotation()) == 4);
		checks++;

		DanmakuColorAnimation.HueCycle hue = new DanmakuColorAnimation.HueCycle(
				constant(10), constant(20), constant(30), constant(0.4), constant(0.5), constant(0.6));
		hue = ActionEditorValueUpdates.withHuePeriod(hue, constant(11));
		hue = ActionEditorValueUpdates.withHueOffset(hue, constant(21));
		hue = ActionEditorValueUpdates.withHueIndexStep(hue, constant(31));
		hue = ActionEditorValueUpdates.withHueSaturation(hue, constant(0.41));
		hue = ActionEditorValueUpdates.withHueBrightness(hue, constant(0.51));
		hue = ActionEditorValueUpdates.withHueAlpha(hue, constant(0.61));
		check("EDITOR_HUE_EDITS_PRESERVE_PERIOD", value(hue.period()) == 11);
		checks++;
		check("EDITOR_HUE_EDITS_PRESERVE_OFFSET_AND_STEP",
				value(hue.hueOffset()) == 21 && value(hue.indexStep()) == 31);
		checks++;
		check("EDITOR_HUE_EDITS_PRESERVE_COLOR_CHANNELS",
				value(hue.saturation()) == 0.41 && value(hue.brightness()) == 0.51 && value(hue.alpha()) == 0.61);
		checks++;

		ResourceLocation phaseId = new ResourceLocation("dev", "phase_b");
		SpellActions.ForcePhase phase = ActionEditorValueUpdates.withPhaseId(
				new SpellActions.ForcePhase(new ResourceLocation("dev", "phase_a"), false), phaseId);
		phase = ActionEditorValueUpdates.withPhaseClearScreen(phase, true);
		check("EDITOR_FORCE_PHASE_CLEAR_PRESERVES_ID", phase.phaseId().equals(phaseId));
		checks++;
		check("EDITOR_FORCE_PHASE_ID_PRESERVES_CLEAR", phase.clearScreen());
		checks++;

		ResourceLocation spellId = new ResourceLocation("dev", "spell_b");
		SpellActions.ForceSpell spell = ActionEditorValueUpdates.withSpellId(
				new SpellActions.ForceSpell(new ResourceLocation("dev", "spell_a"), false), spellId);
		spell = ActionEditorValueUpdates.withSpellClearScreen(spell, true);
		check("EDITOR_FORCE_SPELL_CLEAR_PRESERVES_ID", spell.spellId().equals(spellId));
		checks++;
		check("EDITOR_FORCE_SPELL_ID_PRESERVES_CLEAR", spell.clearScreen());
		checks++;

		return checks;
	}

	private static NumberProvider constant(double value) {
		return NumberProvider.constant(value);
	}

	private static double value(NumberProvider provider) {
		return ((NumberProviders.Constant) provider).value();
	}

	private static void check(String name, boolean condition) {
		if (!condition) throw new AssertionError(name);
		System.out.println("PASS  " + name);
	}
}
