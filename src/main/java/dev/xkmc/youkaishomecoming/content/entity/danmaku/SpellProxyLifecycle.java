package dev.xkmc.youkaishomecoming.content.entity.danmaku;

/** Shared cast-loop and delayed-callback lifecycle rules for spell proxy entities. */
public final class SpellProxyLifecycle {

	private SpellProxyLifecycle() {
	}

	public static boolean castLoopActive(int maxDuration, int elapsedTicks, boolean runtimeFinished) {
		return maxDuration >= 0 ? elapsedTicks < maxDuration : !runtimeFinished;
	}

	public static boolean shouldCleanup(int maxDuration, int elapsedTicks,
			boolean runtimeFinished, boolean pendingHold) {
		return !castLoopActive(maxDuration, elapsedTicks, runtimeFinished) && !pendingHold;
	}

	public static boolean isFinished(int maxDuration, int elapsedTicks,
			boolean runtimeFinished, boolean pendingHold, boolean danmakuEmpty) {
		return shouldCleanup(maxDuration, elapsedTicks, runtimeFinished, pendingHold) && danmakuEmpty;
	}

	/**
	 * A manually disabled non-spell keeps its proxy alive solely to drain the
	 * projectiles that were already emitted.  It may be removed only after that
	 * virtual projectile list is empty.
	 */
	public static boolean shouldFinishStoppedGeneration(boolean generationStopped, boolean danmakuEmpty) {
		return generationStopped && danmakuEmpty;
	}
}
