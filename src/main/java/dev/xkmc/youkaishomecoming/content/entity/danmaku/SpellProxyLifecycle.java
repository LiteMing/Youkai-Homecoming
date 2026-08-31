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
}
