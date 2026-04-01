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
					.forGetter(d -> d.difficulty)
	).apply(i, SpellDefinition::new));

	public final ResourceLocation id;
	public final SpellDisplay display;
	public final SpellItemForm itemForm;
	public final ResourceLocation entryPhase;
	public final Map<ResourceLocation, PhaseDefinition> phases;
	public final DifficultyProfile difficulty;

	public SpellDefinition(ResourceLocation id,
						   SpellDisplay display,
						   SpellItemForm itemForm,
						   ResourceLocation entryPhase,
						   Map<ResourceLocation, PhaseDefinition> phases,
						   DifficultyProfile difficulty) {
		this.id = id;
		this.display = display;
		this.itemForm = itemForm;
		this.entryPhase = entryPhase;
		this.phases = new LinkedHashMap<>(phases);
		this.difficulty = difficulty;
	}

	@Nullable
	public PhaseDefinition getPhase(ResourceLocation phaseId) {
		return phases.get(phaseId);
	}

	@Nullable
	public String getModelId() {
		return display.modelId() != null ? display.modelId().toString() : null;
	}
}
