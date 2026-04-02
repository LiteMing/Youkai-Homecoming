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
		@Nullable ResourceLocation iconItem
) {

	public static final SpellItemForm NONE = new SpellItemForm(false, 0, false, null);

	public static final Codec<SpellItemForm> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("generate", false).forGetter(SpellItemForm::generate),
			Codec.INT.optionalFieldOf("cooldown", 100).forGetter(SpellItemForm::cooldown),
			Codec.BOOL.optionalFieldOf("requires_target", false).forGetter(SpellItemForm::requiresTarget),
			ResourceLocation.CODEC.optionalFieldOf("icon_item")
					.forGetter(form -> Optional.ofNullable(form.iconItem()))
	).apply(i, (generate, cooldown, requiresTarget, iconItem) ->
			new SpellItemForm(generate, cooldown, requiresTarget, iconItem.orElse(null))));
}
