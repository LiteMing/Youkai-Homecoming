package dev.xkmc.youkaishomecoming.content.spell.definition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public record SpellItemForm(
		boolean generate,
		int cooldown,
		boolean requiresTarget,
		int duration,
		Optional<ResourceLocation> iconItem
) {

	public SpellItemForm {
		if (cooldown < 0) {
			throw new IllegalArgumentException("SpellItemForm cooldown must be >= 0");
		}
		if (duration < 0) {
			throw new IllegalArgumentException("SpellItemForm duration must be >= 0");
		}
		iconItem = iconItem == null ? Optional.empty() : iconItem;
	}

	public static final SpellItemForm NONE = new SpellItemForm(false, 0, false, 0, Optional.empty());

	public SpellItemForm(boolean generate, int cooldown, boolean requiresTarget, Optional<ResourceLocation> iconItem) {
		this(generate, cooldown, requiresTarget, 0, iconItem);
	}

	public static final Codec<SpellItemForm> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("generate", false).forGetter(SpellItemForm::generate),
			Codec.INT.optionalFieldOf("cooldown", 100).forGetter(SpellItemForm::cooldown),
			Codec.BOOL.optionalFieldOf("requires_target", false).forGetter(SpellItemForm::requiresTarget),
			Codec.INT.optionalFieldOf("duration", 0).forGetter(SpellItemForm::duration),
			ResourceLocation.CODEC.optionalFieldOf("icon_item").forGetter(SpellItemForm::iconItem)
	).apply(i, SpellItemForm::new));

	@Nullable
	public ResourceLocation iconItemOrNull() {
		return iconItem.orElse(null);
	}
}
