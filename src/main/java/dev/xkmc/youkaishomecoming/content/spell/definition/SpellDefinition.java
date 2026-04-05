package dev.xkmc.youkaishomecoming.content.spell.definition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.content.spell.difficulty.DifficultyProfile;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

public class SpellDefinition {

	public static final Codec<SpellDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
			ResourceLocation.CODEC.fieldOf("id").forGetter(d -> d.id),
			SpellDisplay.CODEC.fieldOf("display").forGetter(d -> d.display),
			SpellItemForm.CODEC.optionalFieldOf("item_form", SpellItemForm.NONE).forGetter(d -> d.itemForm),
			ResourceLocation.CODEC.fieldOf("entry_phase").forGetter(d -> d.entryPhase),
			Codec.unboundedMap(ResourceLocation.CODEC, PhaseDefinition.CODEC)
					.fieldOf("phases").forGetter(d -> d.phases),
			DifficultyProfile.CODEC.optionalFieldOf("difficulty", DifficultyProfile.DEFAULT)
					.forGetter(d -> d.difficulty),
			Codec.unboundedMap(Codec.STRING, Codec.STRING)
					.optionalFieldOf("custom_names", Map.of())
					.forGetter(d -> d.customNames)
	).apply(i, SpellDefinition::new));

	public final ResourceLocation id;
	public final SpellDisplay display;
	public final SpellItemForm itemForm;
	public final ResourceLocation entryPhase;
	public final Map<ResourceLocation, PhaseDefinition> phases;
	public final DifficultyProfile difficulty;
	/** Editor custom node names: path key → display name. Persisted in JSON. */
	public final Map<String, String> customNames;

	public SpellDefinition(ResourceLocation id,
						   SpellDisplay display,
						   SpellItemForm itemForm,
						   ResourceLocation entryPhase,
						   Map<ResourceLocation, PhaseDefinition> phases,
						   DifficultyProfile difficulty,
						   Map<String, String> customNames) {
		this.id = id;
		this.display = display;
		this.itemForm = itemForm;
		this.entryPhase = entryPhase;
		this.phases = new LinkedHashMap<>(phases);
		this.difficulty = difficulty;
		this.customNames = new java.util.HashMap<>(customNames);
	}

	/** Backward-compatible constructor (no custom names). */
	public SpellDefinition(ResourceLocation id,
						   SpellDisplay display,
						   SpellItemForm itemForm,
						   ResourceLocation entryPhase,
						   Map<ResourceLocation, PhaseDefinition> phases,
						   DifficultyProfile difficulty) {
		this(id, display, itemForm, entryPhase, phases, difficulty, Map.of());
	}

	@Nullable
	public PhaseDefinition getPhase(ResourceLocation phaseId) {
		return phases.get(phaseId);
	}

	@Nullable
	public String getModelId() {
		return display.modelIdOrNull() != null ? display.modelIdOrNull().toString() : null;
	}
}
