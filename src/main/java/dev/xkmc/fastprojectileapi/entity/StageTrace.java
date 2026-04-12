package dev.xkmc.fastprojectileapi.entity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StageTrace {

	private static final Logger LOGGER = LoggerFactory.getLogger("ParallelTicker");

	public long moveNanos;
	public long preheatNanos;
	public long collisionInputNanos;
	public long resolveNanos;
	public long applyMoveNanos;
	public long finishNanos;
	public long totalNanos;

	public int projectileCount;
	public int touchedSections;
	public int preheatedSections;
	public int candidateCount;
	public int hitCount;
	public int grazeCount;
	public int removedCount;

	public boolean asyncUsed;

	public void reset() {
		moveNanos = 0L;
		preheatNanos = 0L;
		collisionInputNanos = 0L;
		resolveNanos = 0L;
		applyMoveNanos = 0L;
		finishNanos = 0L;
		totalNanos = 0L;
		projectileCount = 0;
		touchedSections = 0;
		preheatedSections = 0;
		candidateCount = 0;
		hitCount = 0;
		grazeCount = 0;
		removedCount = 0;
		asyncUsed = false;
	}

	public void log() {
		if (!ParallelTicker.ENABLE_LOG || totalNanos <= 0 || projectileCount == 0) return;
		ParallelTicker.logTickCounter++;
		if (ParallelTicker.logTickCounter < ParallelTicker.LOG_INTERVAL) return;
		ParallelTicker.logTickCounter = 0;
		LOGGER.info("[ParallelTicker] mode={} count={} total={}ms | plan+trim={} preheat={} collision={} resolve={} applyMove={} finish={}ms | hits={} graze={} removed={}",
				asyncUsed ? "ASYNC" : "SERIAL",
				projectileCount,
				totalNanos / 1_000_000,
				moveNanos / 1_000_000,
				preheatNanos / 1_000_000,
				collisionInputNanos / 1_000_000,
				resolveNanos / 1_000_000,
				applyMoveNanos / 1_000_000,
				finishNanos / 1_000_000,
				hitCount,
				grazeCount,
				removedCount);
	}

}
