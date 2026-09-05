package dev.xkmc.youkaishomecoming.content.entity.danmaku;

import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellHitContext;

/** Shared live/preview interpretation of source dispositions for whole lasers. */
public enum LaserHitDispositionEffect {
	UNRESOLVED,
	KEEP,
	DISCARD,
	EXPIRE;

	public static LaserHitDispositionEffect from(SpellHitContext.HitDisposition disposition) {
		return switch (disposition) {
			case CONTINUE -> KEEP;
			case DISCARD -> DISCARD;
			case EXPIRE -> EXPIRE;
			case BOUNCE, HOLD, UNRESOLVED -> UNRESOLVED;
		};
	}
}
