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
		return displayText(name);
	}

	public Component displayDesc() {
		return displayText(description);
	}

	public static Component displayText(String keyOrText) {
		// Keep localization deferred to the client, but never use user text as a
		// format pattern: Forge can crash on unfinished braces while editing.
		return Component.translatableWithFallback(keyOrText, "%s", Component.literal(keyOrText));
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
