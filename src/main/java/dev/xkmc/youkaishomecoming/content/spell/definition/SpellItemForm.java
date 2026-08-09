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
		Optional<ResourceLocation> iconItem,
		/** Caster movement: while the spell is being cast, the caster may move freely
		 * (certification: the player is rooted and the certification enemy stands
		 * still unless the spell declares caster movement). */
		boolean casterMoves
) {

	public static final SpellItemForm NONE = new SpellItemForm(false, 0, false, Optional.empty(), false);

	public static final Codec<SpellItemForm> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("generate", false).forGetter(SpellItemForm::generate),
			Codec.INT.optionalFieldOf("cooldown", 100).forGetter(SpellItemForm::cooldown),
			Codec.BOOL.optionalFieldOf("requires_target", false).forGetter(SpellItemForm::requiresTarget),
			ResourceLocation.CODEC.optionalFieldOf("icon_item").forGetter(SpellItemForm::iconItem),
			Codec.BOOL.optionalFieldOf("caster_moves", false).forGetter(SpellItemForm::casterMoves)
	).apply(i, SpellItemForm::new));

	@Nullable
	public ResourceLocation iconItemOrNull() {
		return iconItem.orElse(null);
	}
}
