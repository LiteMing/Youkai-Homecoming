package dev.xkmc.youkaishomecoming.content.spell.editor;

import dev.xkmc.youkaishomecoming.content.spell.definition.PhaseDefinition;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDisplay;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellItemForm;
import dev.xkmc.youkaishomecoming.content.spell.difficulty.DifficultyProfile;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;

public class SpellEditorTemplates {

	private SpellEditorTemplates() {
	}

	public static SpellDefinition createBlank(ResourceLocation id) {
		ResourceLocation mainPhase = new ResourceLocation(id.getNamespace(), id.getPath() + "/main");
		LinkedHashMap<ResourceLocation, PhaseDefinition> phases = new LinkedHashMap<>();
		phases.put(mainPhase, new PhaseDefinition(mainPhase, List.of(), List.of(), List.of(), List.of()));
		return new SpellDefinition(
				id,
				new SpellDisplay(id.toString(), "", null, null),
				SpellItemForm.NONE,
				mainPhase,
				phases,
				DifficultyProfile.DEFAULT
		);
	}
}
