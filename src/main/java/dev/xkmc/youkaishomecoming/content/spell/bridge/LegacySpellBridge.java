package dev.xkmc.youkaishomecoming.content.spell.bridge;

import dev.xkmc.youkaishomecoming.content.spell.action.LegacyTickerAction;
import dev.xkmc.youkaishomecoming.content.spell.definition.*;
import dev.xkmc.youkaishomecoming.content.spell.difficulty.DifficultyProfile;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.SpellCard;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Bridges legacy Java-defined SpellCard subclasses into the new SpellDefinition system.
 * Creates a single-phase definition that delegates tick() to the legacy card.
 */
public class LegacySpellBridge {

	public static SpellDefinition fromLegacy(
			ResourceLocation id,
			Supplier<? extends SpellCard> factory,
			String modelId) {
		ResourceLocation mainPhase = new ResourceLocation(id.getNamespace(), id.getPath() + "/main");
		PhaseDefinition phase = PhaseDefinition.singlePhase(mainPhase, new LegacyTickerAction(factory));

		SpellDisplay display = new SpellDisplay(
				id.toLanguageKey("spell") + ".name",
				id.toLanguageKey("spell") + ".desc",
				Optional.empty(),
				Optional.of(new ResourceLocation(modelId))
		);

		return new SpellDefinition(
				id,
				display,
				SpellItemForm.NONE,
				mainPhase,
				Map.of(mainPhase, phase),
				DifficultyProfile.DEFAULT
		);
	}

	public static SpellDefinition fromLegacy(
			ResourceLocation id,
			Supplier<? extends SpellCard> factory,
			SpellDisplay display) {
		ResourceLocation mainPhase = new ResourceLocation(id.getNamespace(), id.getPath() + "/main");
		PhaseDefinition phase = PhaseDefinition.singlePhase(mainPhase, new LegacyTickerAction(factory));

		return new SpellDefinition(
				id,
				display,
				SpellItemForm.NONE,
				mainPhase,
				Map.of(mainPhase, phase),
				DifficultyProfile.DEFAULT
		);
	}
}
