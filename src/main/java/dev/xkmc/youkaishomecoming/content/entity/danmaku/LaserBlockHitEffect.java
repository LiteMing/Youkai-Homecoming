package dev.xkmc.youkaishomecoming.content.entity.danmaku;

/** Laser-specific wall behavior: every outcome preserves the wall-clipped prefix. */
public enum LaserBlockHitEffect {
	CLIP_ONLY,
	CLIP_AND_SUPPRESS_EXPIRY,
	CLIP_AND_RUN_EXPIRY;

	public static LaserBlockHitEffect from(HitBehavior behavior) {
		return switch (behavior) {
			case CONTINUE -> CLIP_ONLY;
			case DISCARD -> CLIP_AND_SUPPRESS_EXPIRY;
			case EXPIRE -> CLIP_AND_RUN_EXPIRY;
		};
	}

	public static float clipLength(float visualLength, double wallDistance) {
		return wallDistance >= 0 ? Math.min(visualLength, (float) wallDistance) : visualLength;
	}
}
