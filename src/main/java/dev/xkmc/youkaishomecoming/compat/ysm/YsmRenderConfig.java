package dev.xkmc.youkaishomecoming.compat.ysm;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A small, shared initial YSM override used by spell actions.
 *
 * <p>This is deliberately limited to the legacy override fields that the
 * client renderer still consumes: model, texture and an animation hint. It
 * does not contain the newer YHModel presentation state (presets, parameters
 * or exact clips); those belong to the YSM editor and its shared profile.
 */
public record YsmRenderConfig(String model, String texture, String hint, int duration, String clearTarget) {

	public static final YsmRenderConfig EMPTY = new YsmRenderConfig("", "", "", 0, "changed");

	public static final Codec<YsmRenderConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.optionalFieldOf("model", "").forGetter(YsmRenderConfig::model),
			Codec.STRING.optionalFieldOf("texture", "").forGetter(YsmRenderConfig::texture),
			Codec.STRING.optionalFieldOf("hint", "").forGetter(YsmRenderConfig::hint),
			Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("duration", 0).forGetter(YsmRenderConfig::duration),
			Codec.STRING.optionalFieldOf("clear_target", "changed").forGetter(YsmRenderConfig::clearTarget)
	).apply(i, YsmRenderConfig::new));

	public YsmRenderConfig {
		model = normalize(model);
		texture = normalize(texture);
		hint = normalize(hint);
		duration = Math.max(0, duration);
		clearTarget = normalize(clearTarget);
		if (clearTarget.isEmpty()) clearTarget = "changed";
	}

	public boolean enabled() {
		return !model.isBlank() || !texture.isBlank() || !hint.isBlank();
	}

	public void apply(YsmRenderOverrideTarget target) {
		if (target != null && enabled()) {
			target.setYsmRenderOverride(model, texture, hint, duration, clearTarget);
		}
	}

	public static YsmRenderConfig hint(String value, int duration) {
		return new YsmRenderConfig("", "", value, duration, "animation");
	}

	public YsmRenderConfig withModel(String value) {
		return new YsmRenderConfig(value, texture, hint, duration, clearTarget);
	}

	public YsmRenderConfig withTexture(String value) {
		return new YsmRenderConfig(model, value, hint, duration, clearTarget);
	}

	public YsmRenderConfig withHint(String value) {
		return new YsmRenderConfig(model, texture, value, duration, clearTarget);
	}

	public YsmRenderConfig withDuration(int value) {
		return new YsmRenderConfig(model, texture, hint, value, clearTarget);
	}

	public YsmRenderConfig withClearTarget(String value) {
		return new YsmRenderConfig(model, texture, hint, duration, value);
	}

	private static String normalize(String value) {
		return value == null ? "" : value.trim();
	}
}
