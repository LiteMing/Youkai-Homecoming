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
		@Nullable ResourceLocation icon,
		@Nullable ResourceLocation modelId
) {

	public static final Codec<SpellDisplay> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("name").forGetter(SpellDisplay::name),
			Codec.STRING.optionalFieldOf("description", "").forGetter(SpellDisplay::description),
			ResourceLocation.CODEC.optionalFieldOf("icon")
					.forGetter(display -> Optional.ofNullable(display.icon())),
			ResourceLocation.CODEC.optionalFieldOf("model_id")
					.forGetter(display -> Optional.ofNullable(display.modelId()))
	).apply(i, (name, description, icon, modelId) ->
			new SpellDisplay(name, description, icon.orElse(null), modelId.orElse(null))));

	public Component displayName() {
		return isTranslationKey(name) ? Component.translatable(name) : Component.literal(name);
	}

	public Component displayDesc() {
		if (description.isEmpty()) {
			return Component.empty();
		}
		return isTranslationKey(description) ? Component.translatable(description) : Component.literal(description);
	}

	private static boolean isTranslationKey(String text) {
		return text.indexOf(' ') < 0 && text.indexOf('.') >= 0;
	}
}
