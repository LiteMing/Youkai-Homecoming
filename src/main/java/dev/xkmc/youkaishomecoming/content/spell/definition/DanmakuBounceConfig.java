package dev.xkmc.youkaishomecoming.content.spell.definition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

public record DanmakuBounceConfig(
		int maxBounces,
		double decay,
		BounceMode mode,
		boolean retarget,
		double groundOffset,
		double stepHeight
) {
	public enum BounceMode implements StringRepresentable {
		SPECULAR("specular"),
		GROUND_GLIDE("ground_glide");

		public static final Codec<BounceMode> CODEC = StringRepresentable.fromEnum(BounceMode::values);

		private final String name;

		BounceMode(String name) {
			this.name = name;
		}

		@Override
		@NotNull
		public String getSerializedName() {
			return name;
		}
	}

	public static final Codec<DanmakuBounceConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("max_bounces", 1).forGetter(DanmakuBounceConfig::maxBounces),
			Codec.DOUBLE.optionalFieldOf("decay", 1.0).forGetter(DanmakuBounceConfig::decay),
			BounceMode.CODEC.optionalFieldOf("mode", BounceMode.SPECULAR).forGetter(DanmakuBounceConfig::mode),
			Codec.BOOL.optionalFieldOf("retarget", false).forGetter(DanmakuBounceConfig::retarget),
			Codec.DOUBLE.optionalFieldOf("ground_offset", 0.3).forGetter(DanmakuBounceConfig::groundOffset),
			Codec.DOUBLE.optionalFieldOf("step_height", 1.25).forGetter(DanmakuBounceConfig::stepHeight)
	).apply(i, DanmakuBounceConfig::new));

	public static DanmakuBounceConfig defaults() {
		return new DanmakuBounceConfig(1, 1.0, BounceMode.SPECULAR, false, 0.3, 1.25);
	}
}
