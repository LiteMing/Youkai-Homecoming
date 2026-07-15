package dev.xkmc.youkaishomecoming.content.spell.definition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.content.spell.condition.SpellCondition;
import net.minecraft.resources.ResourceLocation;

public record Transition(
		SpellCondition condition,
		ResourceLocation targetPhase,
		TransitionMode mode
) {

	public static final Codec<Transition> CODEC = RecordCodecBuilder.create(i -> i.group(
			SpellCondition.CODEC.fieldOf("condition").forGetter(Transition::condition),
			ResourceLocation.CODEC.fieldOf("target_phase").forGetter(Transition::targetPhase),
			TransitionMode.CODEC.optionalFieldOf("mode", TransitionMode.IMMEDIATE).forGetter(Transition::mode)
	).apply(i, Transition::new));
}
