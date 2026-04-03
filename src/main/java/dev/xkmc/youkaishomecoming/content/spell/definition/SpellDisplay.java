package dev.xkmc.youkaishomecoming.content.spell.definition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public record SpellDisplay(
		String name,
		String description,
		Optional<ResourceLocation> icon,
		Optional<ResourceLocation> modelId
) {

	public static final Codec<SpellDisplay> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("name").forGetter(SpellDisplay::name),
			Codec.STRING.optionalFieldOf("description", "").forGetter(SpellDisplay::description),
			ResourceLocation.CODEC.optionalFieldOf("icon").forGetter(SpellDisplay::icon),
			ResourceLocation.CODEC.optionalFieldOf("model_id").forGetter(SpellDisplay::modelId)
	).apply(i, SpellDisplay::new));

	public Component displayName() {
		return Component.translatable(name);
	}

	public Component displayDesc() {
		return Component.translatable(description);
	}

	@Nullable
	public ResourceLocation iconOrNull() {
		return icon.orElse(null);
	}

	@Nullable
	public ResourceLocation modelIdOrNull() {
		return modelId.orElse(null);
	}
}
