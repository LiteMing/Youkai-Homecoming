package dev.xkmc.fastprojectileapi.spellcircle;

import dev.xkmc.youkaishomecoming.content.capability.GrazeCapability;
import dev.xkmc.youkaishomecoming.content.spell.certification.network.CertificationClientHandler;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Client-side life-to-opacity policy for the automatic player STG circle.
 * Keeping the policy outside the renderer makes the transition boundaries
 * configurable without changing the editable circle components.
 */
@OnlyIn(Dist.CLIENT)
public final class SpellCircleLifeAlpha {

	private static final int RESOURCE_UNIT = 5;

	private SpellCircleLifeAlpha() {
	}

	public static float compute(GrazeCapability cap) {
		// Certification PREPARE/ACTIVE deliberately keeps the circle fully visible.
		if (CertificationClientHandler.inMyTrial()) {
			return 1.0f;
		}
		double life = cap.getLife() / (double) RESOURCE_UNIT;
		double fadeStart = YHModConfig.COMMON.spellCirclePlayerFadeStartLife.get();
		double fadeEnd = YHModConfig.COMMON.spellCirclePlayerFadeEndLife.get();
		double minAlpha = YHModConfig.COMMON.spellCirclePlayerMinAlpha.get();
		if (life >= fadeStart) {
			return 1.0f;
		}
		// Keep a malformed end >= start setting continuous rather than introducing
		// an opacity step immediately below the start threshold.
		if (fadeEnd >= fadeStart) {
			fadeEnd = 0.0;
		}
		float t = (float) clamp((life - fadeEnd) / (fadeStart - fadeEnd), 0.0, 1.0);
		float smooth = t * t * (3.0f - 2.0f * t);
		float floor = (float) clamp(minAlpha, 0.0, 1.0);
		return floor + (1.0f - floor) * smooth;
	}

	public static boolean shouldRender(float alpha) {
		return alpha > YHModConfig.COMMON.spellCirclePlayerRenderAlphaCutoff.get();
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}
}
