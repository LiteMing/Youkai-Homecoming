package dev.xkmc.fastprojectileapi.entity;

public class StageTrace {

	public long beginNanos;
	public long moveNanos;
	public long preheatNanos;
	public long collisionInputNanos;
	public long resolveNanos;
	public long finishNanos;
	public long totalNanos;

	public int projectileCount;
	public int touchedSections;
	public int preheatedSections;
	public int candidateCount;
	public int hitCount;
	public int grazeCount;
	public int removedCount;

	public void reset() {
		beginNanos = 0L;
		moveNanos = 0L;
		preheatNanos = 0L;
		collisionInputNanos = 0L;
		resolveNanos = 0L;
		finishNanos = 0L;
		totalNanos = 0L;
		projectileCount = 0;
		touchedSections = 0;
		preheatedSections = 0;
		candidateCount = 0;
		hitCount = 0;
		grazeCount = 0;
		removedCount = 0;
	}

}
