package dev.xkmc.youkaishomecoming.compat.kubejs.spell;

import dev.xkmc.youkaishomecoming.content.spell.action.SpellAction;
import dev.xkmc.youkaishomecoming.content.spell.condition.SpellCondition;
import dev.xkmc.youkaishomecoming.content.spell.definition.*;
import dev.xkmc.youkaishomecoming.content.spell.difficulty.DifficultyProfile;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public class SpellDefinitionBuilderJS {

	private final ResourceLocation id;
	private String displayName;
	private String description = "";
	@Nullable
	private ResourceLocation icon;
	@Nullable
	private ResourceLocation modelId;
	private SpellItemForm itemForm = SpellItemForm.NONE;
	private DifficultyProfile difficulty = DifficultyProfile.DEFAULT;
	private final Map<ResourceLocation, PhaseBuilderJS> phases = new LinkedHashMap<>();
	private ResourceLocation entryPhase;

	SpellDefinitionBuilderJS(ResourceLocation id) {
		this.id = id;
		this.displayName = id.toLanguageKey("spell") + ".name";
	}

	public SpellDefinitionBuilderJS display(String name, String description) {
		this.displayName = name;
		this.description = description;
		return this;
	}

	public SpellDefinitionBuilderJS modelId(String modelId) {
		this.modelId = new ResourceLocation(modelId);
		return this;
	}

	public SpellDefinitionBuilderJS icon(String icon) {
		this.icon = new ResourceLocation(icon);
		return this;
	}

	public SpellDefinitionBuilderJS phase(String phaseId, Consumer<PhaseBuilderJS> config) {
		ResourceLocation phaseLoc = new ResourceLocation(id.getNamespace(), id.getPath() + "/" + phaseId);
		var builder = new PhaseBuilderJS(phaseLoc);
		config.accept(builder);
		phases.put(phaseLoc, builder);
		if (entryPhase == null) {
			entryPhase = phaseLoc;
		}
		return this;
	}

	public SpellDefinitionBuilderJS entryPhase(String phaseId) {
		this.entryPhase = new ResourceLocation(id.getNamespace(), id.getPath() + "/" + phaseId);
		return this;
	}

	public SpellDefinitionBuilderJS itemForm(int cooldown, boolean requireTarget) {
		return itemForm(cooldown, requireTarget, "normal");
	}

	/** Defines the item form and its pre-certification card type. */
	public SpellDefinitionBuilderJS itemForm(int cooldown, boolean requireTarget, String cardType) {
		this.itemForm = new SpellItemForm(true, cooldown, requireTarget, Optional.empty(), false, 0, 0,
				SpellCardType.byName(cardType));
		return this;
	}

	/** Changes only the card type while preserving the existing item form fields. */
	public SpellDefinitionBuilderJS cardType(String cardType) {
		this.itemForm = this.itemForm.withCardType(SpellCardType.byName(cardType));
		return this;
	}

	SpellDefinition build() {
		if (entryPhase == null && !phases.isEmpty()) {
			entryPhase = phases.keySet().iterator().next();
		}
		if (entryPhase == null) {
			entryPhase = new ResourceLocation(id.getNamespace(), id.getPath() + "/main");
		}

		Map<ResourceLocation, PhaseDefinition> phaseDefs = new LinkedHashMap<>();
		for (var entry : phases.entrySet()) {
			phaseDefs.put(entry.getKey(), entry.getValue().build());
		}

		SpellDisplay display = new SpellDisplay(displayName, description, Optional.ofNullable(icon), Optional.ofNullable(modelId));
		return new SpellDefinition(id, display, itemForm, entryPhase, phaseDefs, difficulty);
	}
}
