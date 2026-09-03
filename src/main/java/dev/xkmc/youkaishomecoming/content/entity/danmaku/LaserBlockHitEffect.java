package dev.xkmc.youkaishomecoming.content.entity.danmaku;

/** Laser-specific wall behavior after the block callback has resolved. */
public enum LaserBlockHitEffect {
	PASS_THROUGH,
	CLIP_ONLY,
	CLIP_AND_SUPPRESS_EXPIRY,
	CLIP_AND_RUN_EXPIRY;

	public static LaserBlockHitEffect from(HitBehavior behavior) {
		return switch (behavior) {
			case CONTINUE -> PASS_THROUGH;
			case DISCARD -> CLIP_AND_SUPPRESS_EXPIRY;
			case EXPIRE -> CLIP_AND_RUN_EXPIRY;
		};
	}

	public float visibleLength(float visualLength, double wallDistance) {
		return this == PASS_THROUGH ? visualLength : clipLength(visualLength, wallDistance);
	}

	public static float clipLength(float visualLength, double wallDistance) {
		return wallDistance >= 0 ? Math.min(visualLength, (float) wallDistance) : visualLength;
	}
}
