package dev.xkmc.youkaishomecoming.content.spell.definition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * Surface response model for danmaku bouncing / reflection / surface deflection:
 * <ul>
 *   <li><b>normalFactor</b>: Multiplier for normal velocity (e.g. -1.0 for specular reflection, 0.0 for surface deflection)</li>
 *   <li><b>tangentFactor</b>: Multiplier for tangential velocity (e.g. 1.0 for frictionless, 0.8 for friction)</li>
 *   <li><b>outputSpeed</b>: Optional absolute scalar speed reset after reflection</li>
 *   <li><b>retarget</b>: Whether to retarget towards target entity after collision</li>
 * </ul>
 */
public record DanmakuBounceConfig(
		int maxBounces,
		double normalFactor,
		double tangentFactor,
		Optional<Double> outputSpeed,
		boolean retarget
) {
	public static final Codec<DanmakuBounceConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("max_bounces", 1).forGetter(DanmakuBounceConfig::maxBounces),
			Codec.DOUBLE.optionalFieldOf("normal_factor", -1.0).forGetter(DanmakuBounceConfig::normalFactor),
			Codec.DOUBLE.optionalFieldOf("tangent_factor", 1.0).forGetter(DanmakuBounceConfig::tangentFactor),
			Codec.DOUBLE.optionalFieldOf("output_speed").forGetter(DanmakuBounceConfig::outputSpeed),
			Codec.BOOL.optionalFieldOf("retarget", false).forGetter(DanmakuBounceConfig::retarget)
	).apply(i, DanmakuBounceConfig::new));

	public DanmakuBounceConfig sanitize() {
		int maxB = Math.max(0, Math.min(64, maxBounces));
		double nf = Double.isFinite(normalFactor) ? Math.max(-5.0, Math.min(5.0, normalFactor)) : -1.0;
		double tf = Double.isFinite(tangentFactor) ? Math.max(0.0, Math.min(5.0, tangentFactor)) : 1.0;
		Optional<Double> os = outputSpeed.filter(Double::isFinite).map(s -> Math.max(0.0, Math.min(20.0, s)));
		return new DanmakuBounceConfig(maxB, nf, tf, os, retarget);
	}

	public static DanmakuBounceConfig defaults() {
		return new DanmakuBounceConfig(1, -1.0, 1.0, Optional.empty(), false);
	}
}
