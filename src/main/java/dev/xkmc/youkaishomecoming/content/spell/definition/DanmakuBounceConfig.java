package dev.xkmc.youkaishomecoming.content.spell.definition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record DanmakuBounceConfig(
		int maxBounces,
		double decay,
		boolean retarget
) {
	public static final Codec<DanmakuBounceConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("max_bounces", 1).forGetter(DanmakuBounceConfig::maxBounces),
			Codec.DOUBLE.optionalFieldOf("decay", 1.0).forGetter(DanmakuBounceConfig::decay),
			Codec.BOOL.optionalFieldOf("retarget", false).forGetter(DanmakuBounceConfig::retarget)
	).apply(i, DanmakuBounceConfig::new));

	public DanmakuBounceConfig sanitize() {
		int maxB = Math.max(0, Math.min(64, maxBounces));
		double dec = Double.isFinite(decay) ? Math.max(0.0, Math.min(2.0, decay)) : 1.0;
		return new DanmakuBounceConfig(maxB, dec, retarget);
	}

	public static DanmakuBounceConfig defaults() {
		return new DanmakuBounceConfig(1, 1.0, false);
	}
}
