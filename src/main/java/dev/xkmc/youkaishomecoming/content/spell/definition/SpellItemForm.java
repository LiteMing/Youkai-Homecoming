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
		/**
		 * Legacy compatibility flag. Movement is now selected by the regular
		 * {@code caster_moves} action; when absent, movement defaults to random/free.
		 */
		boolean casterMoves,
		/** Spell duration in ticks (= certification timeout). Every player-created
		 * spell must declare it; certification refuses spells without one. */
		int duration,
		/** Spell card HP: the certification enemy's max health (player-set, plain HP). */
		int hp,
		/** Explicit spell category. Missing legacy JSON is a normal spell. */
		SpellCardType cardType,
		/** EX trait: protects this card's spell-health from player spell-card danmaku. */
		boolean exSpell
) {

	public static final SpellItemForm NONE = new SpellItemForm(false, 0, false, Optional.empty(), false, 0, 0,
			SpellCardType.NORMAL, false);

	public static final Codec<SpellItemForm> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("generate", false).forGetter(SpellItemForm::generate),
			Codec.INT.optionalFieldOf("cooldown", 100).forGetter(SpellItemForm::cooldown),
			Codec.BOOL.optionalFieldOf("requires_target", false).forGetter(SpellItemForm::requiresTarget),
			ResourceLocation.CODEC.optionalFieldOf("icon_item").forGetter(SpellItemForm::iconItem),
			Codec.BOOL.optionalFieldOf("caster_moves", false).forGetter(SpellItemForm::casterMoves),
			Codec.INT.optionalFieldOf("duration", 0).forGetter(SpellItemForm::duration),
			Codec.INT.optionalFieldOf("hp", 0).forGetter(SpellItemForm::hp),
			Codec.STRING.xmap(SpellCardType::byName, SpellCardType::getSerializedName)
					.optionalFieldOf("card_type", SpellCardType.NORMAL).forGetter(SpellItemForm::cardType),
			Codec.BOOL.optionalFieldOf("ex_spell", false).forGetter(SpellItemForm::exSpell)
	).apply(i, SpellItemForm::new));

	/** Source compatibility for older KubeJS and Java spell registrations. */
	public SpellItemForm(boolean generate, int cooldown, boolean requiresTarget,
			Optional<ResourceLocation> iconItem, boolean casterMoves, int duration, int hp) {
		this(generate, cooldown, requiresTarget, iconItem, casterMoves, duration, hp, SpellCardType.NORMAL, false);
	}

	public SpellItemForm(boolean generate, int cooldown, boolean requiresTarget,
			Optional<ResourceLocation> iconItem, boolean casterMoves, int duration, int hp,
			SpellCardType cardType) {
		this(generate, cooldown, requiresTarget, iconItem, casterMoves, duration, hp, cardType, false);
	}

	@Nullable
	public ResourceLocation iconItemOrNull() {
		return iconItem.orElse(null);
	}

	/** Copy with a new declared duration (ticks = certification timeout). */
	public SpellItemForm withDuration(int newDuration) {
		return new SpellItemForm(generate, cooldown, requiresTarget, iconItem, casterMoves, newDuration, hp, cardType, exSpell);
	}

	/** Copy with a new spell HP (certification enemy max health). */
	public SpellItemForm withHp(int newHp) {
		return new SpellItemForm(generate, cooldown, requiresTarget, iconItem, casterMoves, duration, newHp, cardType, exSpell);
	}

	public SpellItemForm withCardType(SpellCardType newType) {
		return new SpellItemForm(generate, cooldown, requiresTarget, iconItem, casterMoves, duration, hp,
				newType == null ? SpellCardType.NORMAL : newType, exSpell);
	}

	public SpellItemForm withExSpell(boolean enabled) {
		return new SpellItemForm(generate, cooldown, requiresTarget, iconItem, casterMoves, duration, hp,
				cardType, enabled);
	}
}
