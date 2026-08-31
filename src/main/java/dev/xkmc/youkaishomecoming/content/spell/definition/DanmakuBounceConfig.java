package dev.xkmc.youkaishomecoming.content.spell.definition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Surface response model for danmaku bouncing / reflection / surface deflection:
 * <ul>
 *   <li><b>normalFactor</b>: Multiplier for normal velocity in range [-5.0, 0.0] (-1.0 for specular reflection, 0.0 for surface deflection)</li>
 *   <li><b>tangentFactor</b>: Multiplier for tangential velocity in range [-5.0, 5.0] (1.0 for frictionless, -1.0 for reversal)</li>
 *   <li><b>tangentOffset</b>: World XYZ velocity offset projected onto the surface tangent plane</li>
 *   <li><b>outputSpeed</b>: Optional absolute scalar speed reset after reflection</li>
 *   <li><b>retarget</b>: Whether to retarget towards target entity after collision</li>
 * </ul>
 */
public record DanmakuBounceConfig(
		int maxBounces,
		double normalFactor,
		double tangentFactor,
		double tangentOffsetX,
		double tangentOffsetY,
		double tangentOffsetZ,
		Optional<Double> outputSpeed,
		boolean retarget
) {
	public static final Codec<DanmakuBounceConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("max_bounces", 1).forGetter(DanmakuBounceConfig::maxBounces),
			Codec.DOUBLE.optionalFieldOf("normal_factor", -1.0).forGetter(DanmakuBounceConfig::normalFactor),
			Codec.DOUBLE.optionalFieldOf("tangent_factor", 1.0).forGetter(DanmakuBounceConfig::tangentFactor),
			Codec.DOUBLE.optionalFieldOf("tangent_offset_x", 0.0).forGetter(DanmakuBounceConfig::tangentOffsetX),
			Codec.DOUBLE.optionalFieldOf("tangent_offset_y", 0.0).forGetter(DanmakuBounceConfig::tangentOffsetY),
			Codec.DOUBLE.optionalFieldOf("tangent_offset_z", 0.0).forGetter(DanmakuBounceConfig::tangentOffsetZ),
			Codec.DOUBLE.optionalFieldOf("output_speed").forGetter(DanmakuBounceConfig::outputSpeed),
			Codec.BOOL.optionalFieldOf("retarget", false).forGetter(DanmakuBounceConfig::retarget)
	).apply(i, DanmakuBounceConfig::new));

	public DanmakuBounceConfig(int maxBounces, double normalFactor, double tangentFactor, Optional<Double> outputSpeed, boolean retarget) {
		this(maxBounces, normalFactor, tangentFactor, 0.0, 0.0, 0.0, outputSpeed, retarget);
	}

	public DanmakuBounceConfig sanitize() {
		int maxB = Math.max(0, Math.min(64, maxBounces));
		// Normal factor must be <= 0 to guarantee outgoing velocity does not penetrate the surface
		double nf = Double.isFinite(normalFactor) ? Math.max(-5.0, Math.min(0.0, normalFactor)) : -1.0;
		double tf = Double.isFinite(tangentFactor) ? Math.max(-5.0, Math.min(5.0, tangentFactor)) : 1.0;
		double tox = Double.isFinite(tangentOffsetX) ? Math.max(-50.0, Math.min(50.0, tangentOffsetX)) : 0.0;
		double toy = Double.isFinite(tangentOffsetY) ? Math.max(-50.0, Math.min(50.0, tangentOffsetY)) : 0.0;
		double toz = Double.isFinite(tangentOffsetZ) ? Math.max(-50.0, Math.min(50.0, tangentOffsetZ)) : 0.0;
		Optional<Double> os = outputSpeed.filter(Double::isFinite).map(s -> Math.max(0.0, Math.min(50.0, s)));
		return new DanmakuBounceConfig(maxB, nf, tf, tox, toy, toz, os, retarget);
	}

	public Vec3 getTangentOffset() {
		return new Vec3(tangentOffsetX, tangentOffsetY, tangentOffsetZ);
	}

	public static DanmakuBounceConfig defaults() {
		return new DanmakuBounceConfig(1, -1.0, 1.0, 0.0, 0.0, 0.0, Optional.empty(), false);
	}
}
