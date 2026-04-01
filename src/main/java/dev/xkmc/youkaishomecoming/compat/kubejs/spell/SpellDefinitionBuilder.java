package dev.xkmc.youkaishomecoming.compat.kubejs.spell;

import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.util.UtilsJS;
import dev.latvian.mods.rhino.BaseFunction;
import dev.xkmc.youkaishomecoming.content.spell.definition.PhaseDefinition;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDisplay;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellItemForm;
import dev.xkmc.youkaishomecoming.content.spell.difficulty.DifficultyProfile;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRegistry;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

public class SpellDefinitionBuilder {

	private final ResourceLocation id;
	private String name;
	private String description = "";
	@Nullable
	private ResourceLocation icon;
	@Nullable
	private ResourceLocation modelId;
	private SpellItemForm itemForm = SpellItemForm.NONE;
	@Nullable
	private ResourceLocation entryPhase;
	private final Map<ResourceLocation, SpellPhaseBuilder> phases = new LinkedHashMap<>();

	public SpellDefinitionBuilder(ResourceLocation id) {
		this.id = id;
		this.name = id.toString();
	}

	public SpellDefinitionBuilder display(String name, String description) {
		this.name = name;
		this.description = description == null ? "" : description;
		return this;
	}

	public SpellDefinitionBuilder icon(String id) {
		this.icon = KubeJSSpellSupport.parseId(id);
		return this;
	}

	public SpellDefinitionBuilder model(String id) {
		this.modelId = KubeJSSpellSupport.parseId(id);
		return this;
	}

	public SpellDefinitionBuilder entryPhase(String phaseId) {
		this.entryPhase = KubeJSSpellSupport.parsePhaseId(id, phaseId);
		return this;
	}

	public SpellDefinitionBuilder itemForm(Object options) {
		var map = KubeJSSpellSupport.unwrapMap(options);
		boolean generate = KubeJSSpellSupport.getBoolean(map, "generate", "generate", true);
		int cooldown = KubeJSSpellSupport.getInt(map, "cooldown", "cooldown", 100);
		boolean requiresTarget = KubeJSSpellSupport.getBoolean(map, "requiresTarget", "requires_target", false);
		ResourceLocation iconItem = KubeJSSpellSupport.parseNullableId(
				map.containsKey("iconItem") ? map.get("iconItem") : map.get("icon_item"));
		this.itemForm = new SpellItemForm(generate, cooldown, requiresTarget, iconItem);
		return this;
	}

	public SpellPhaseBuilder phase(String phaseId) {
		ResourceLocation resolved = KubeJSSpellSupport.parsePhaseId(id, phaseId);
		return phases.computeIfAbsent(resolved, key -> new SpellPhaseBuilder(id, key));
	}

	public SpellDefinitionBuilder phase(String phaseId, BaseFunction callback) {
		SpellPhaseBuilder phase = phase(phaseId);
		KubeJSSpellSupport.SpellPhaseBuilderCallback consumer =
				UtilsJS.makeFunctionProxy(ScriptType.STARTUP, KubeJSSpellSupport.SpellPhaseBuilderCallback.class, callback);
		consumer.accept(phase);
		return this;
	}

	public SpellDefinition build() {
		if (phases.isEmpty()) {
			throw new IllegalStateException("Spell " + id + " must define at least one phase");
		}
		if (entryPhase == null) {
			entryPhase = phases.keySet().iterator().next();
		}
		Map<ResourceLocation, PhaseDefinition> builtPhases = new LinkedHashMap<>();
		for (var entry : phases.entrySet()) {
			builtPhases.put(entry.getKey(), entry.getValue().build());
		}
		return new SpellDefinition(
				id,
				new SpellDisplay(name, description, icon, modelId),
				itemForm,
				entryPhase,
				builtPhases,
				DifficultyProfile.DEFAULT
		);
	}

	public SpellDefinitionBuilder copyFrom(String spellId) {
		var definition = SpellRegistry.get(KubeJSSpellSupport.parseId(spellId));
		if (definition == null) {
			throw new IllegalArgumentException("Unknown spell: " + spellId);
		}
		this.name = definition.display.name();
		this.description = definition.display.description();
		this.icon = definition.display.icon();
		this.modelId = definition.display.modelId();
		this.itemForm = definition.itemForm;
		this.entryPhase = definition.entryPhase;
		this.phases.clear();
		for (var entry : definition.phases.entrySet()) {
			SpellPhaseBuilder builder = new SpellPhaseBuilder(id, entry.getKey());
			builder.copyFrom(entry.getValue());
			this.phases.put(entry.getKey(), builder);
		}
		return this;
	}
}
