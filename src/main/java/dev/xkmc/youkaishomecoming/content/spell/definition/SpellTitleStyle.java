package dev.xkmc.youkaishomecoming.content.spell.definition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;
import java.util.Optional;

/** Optional author overrides; absent numeric values use the viewer's YHModConfig. */
public record SpellTitleStyle(Optional<ResourceLocation> background, Optional<Float> backgroundScale,
		Optional<Float> backgroundX, Optional<Float> backgroundY,
		Optional<Integer> gradientStart, Optional<Integer> gradientEnd,
		Optional<GradientDirection> gradientDirection) {

	public static final SpellTitleStyle DEFAULT = new SpellTitleStyle(Optional.empty(), Optional.empty(),
			Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

	public enum GradientDirection implements StringRepresentable {
		LEFT_TO_RIGHT("left_to_right", false, false),
		RIGHT_TO_LEFT("right_to_left", false, true),
		TOP_TO_BOTTOM("top_to_bottom", true, false),
		BOTTOM_TO_TOP("bottom_to_top", true, true);

		public static final Codec<GradientDirection> CODEC = StringRepresentable.fromEnum(GradientDirection::values);
		private final String id;
		private final boolean vertical;
		private final boolean reversed;

		GradientDirection(String id, boolean vertical, boolean reversed) {
			this.id = id;
			this.vertical = vertical;
			this.reversed = reversed;
		}

		@Override public String getSerializedName() { return id; }
		public boolean vertical() { return vertical; }
		public boolean reversed() { return reversed; }
	}

	private static final Codec<Integer> COLOR = Codec.either(Codec.INT, Codec.STRING.<Integer>comapFlatMap(value -> {
		String hex = value.startsWith("#") ? value.substring(1)
				: value.startsWith("0x") || value.startsWith("0X") ? value.substring(2) : value;
		try {
			if (hex.length() != 8) return DataResult.error(() -> "Expected an AARRGGBB color");
			return DataResult.success((int) Long.parseLong(hex, 16));
		} catch (NumberFormatException e) {
			return DataResult.error(() -> "Invalid AARRGGBB color: " + value);
		}
	}, value -> String.format(Locale.ROOT, "#%08X", value))).xmap(
			either -> either.map(value -> value, value -> value), com.mojang.datafixers.util.Either::right);

	public static final Codec<SpellTitleStyle> CODEC = RecordCodecBuilder.create(i -> i.group(
			ResourceLocation.CODEC.optionalFieldOf("background").forGetter(SpellTitleStyle::background),
			Codec.floatRange(0.05f, 8).optionalFieldOf("background_scale").forGetter(SpellTitleStyle::backgroundScale),
			Codec.floatRange(-4096, 4096).optionalFieldOf("background_x").forGetter(SpellTitleStyle::backgroundX),
			Codec.floatRange(-4096, 4096).optionalFieldOf("background_y").forGetter(SpellTitleStyle::backgroundY),
			COLOR.optionalFieldOf("gradient_start").forGetter(SpellTitleStyle::gradientStart),
			COLOR.optionalFieldOf("gradient_end").forGetter(SpellTitleStyle::gradientEnd),
			GradientDirection.CODEC.optionalFieldOf("gradient_direction").forGetter(SpellTitleStyle::gradientDirection)
	).apply(i, SpellTitleStyle::new));

	public CompoundTag toTag() {
		return (CompoundTag) CODEC.encodeStart(NbtOps.INSTANCE, this).getOrThrow(false, message -> {});
	}

	public static SpellTitleStyle fromTag(CompoundTag tag) {
		return tag == null ? DEFAULT : CODEC.parse(NbtOps.INSTANCE, tag).result().orElse(DEFAULT);
	}

	public SpellTitleStyle withBackground(Optional<ResourceLocation> value) {
		return new SpellTitleStyle(value, backgroundScale, backgroundX, backgroundY, gradientStart, gradientEnd, gradientDirection);
	}

	public SpellTitleStyle withBackgroundScale(float value) {
		return new SpellTitleStyle(background, Optional.of(value), backgroundX, backgroundY, gradientStart, gradientEnd, gradientDirection);
	}

	public SpellTitleStyle withBackgroundX(float value) {
		return new SpellTitleStyle(background, backgroundScale, Optional.of(value), backgroundY, gradientStart, gradientEnd, gradientDirection);
	}

	public SpellTitleStyle withBackgroundY(float value) {
		return new SpellTitleStyle(background, backgroundScale, backgroundX, Optional.of(value), gradientStart, gradientEnd, gradientDirection);
	}

	public SpellTitleStyle withGradientStart(int value) {
		return new SpellTitleStyle(background, backgroundScale, backgroundX, backgroundY, Optional.of(value), gradientEnd, gradientDirection);
	}

	public SpellTitleStyle withGradientEnd(int value) {
		return new SpellTitleStyle(background, backgroundScale, backgroundX, backgroundY, gradientStart, Optional.of(value), gradientDirection);
	}

	public SpellTitleStyle withGradientDirection(GradientDirection value) {
		return new SpellTitleStyle(background, backgroundScale, backgroundX, backgroundY, gradientStart, gradientEnd, Optional.of(value));
	}
}
