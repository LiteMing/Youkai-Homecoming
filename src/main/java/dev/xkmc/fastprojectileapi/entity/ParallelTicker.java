package dev.xkmc.fastprojectileapi.entity;

import dev.xkmc.fastprojectileapi.collision.UserMatrixCache;

import java.util.ArrayList;
import java.util.function.BooleanSupplier;

public class ParallelTicker {

	private static final ThreadLocal<StageTrace> TRACE = ThreadLocal.withInitial(StageTrace::new);

	public static void tickAll(Iterable<SimplifiedProjectile> all, BooleanSupplier shouldStop) {
		tickAll(all, shouldStop, null);
	}

	public static void tickAll(Iterable<SimplifiedProjectile> all, BooleanSupplier shouldStop, UserMatrixCache preheatCache) {
		StageTrace trace = TRACE.get();
		trace.reset();
		long totalStart = System.nanoTime();
		ArrayList<AsyncProjectile> active = new ArrayList<>();
		for (var e : all) {
			if (e.isAddedToWorld() && !e.isRemoved()) continue;
			if (!e.isValid()) continue;
			trace.projectileCount++;
			e.setOldPosAndRot();
			++e.tickCount;
			if (e instanceof AsyncProjectile async) {
				async.tickData.reset();
				active.add(async);
			} else {
				e.tick();
			}
			if (shouldStop.getAsBoolean()) return;
		}
		trace.beginNanos = runStage(active, shouldStop, (e, data) -> e.beginTick(data), null);
		trace.moveNanos = runStage(active, shouldStop, (e, data) -> e.planMove(data), null);
		trace.preheatNanos = runStage(active, shouldStop, (e, data) -> {
			e.planPreheatRange(data);
		}, null);
		if (preheatCache != null) {
			long start = System.nanoTime();
			preheatCache.preheat();
			trace.preheatNanos += System.nanoTime() - start;
		}
		trace.collisionInputNanos = runStage(active, shouldStop, (e, data) -> e.collectCollisionInput(data), null);
		trace.resolveNanos = runStage(active, shouldStop, (e, data) -> e.resolveCollision(data), (data, result) -> {
			result.hitCount += data.hitEntities.size();
			result.grazeCount += data.grazeCount;
		});
		trace.finishNanos = runStage(active, shouldStop, (e, data) -> e.finishTick(data), (data, result) -> {
			result.candidateCount += data.candidateCount;
			if (data.removed) result.removedCount++;
		});
		trace.totalNanos = System.nanoTime() - totalStart;
	}

	public static StageTrace currentTrace() {
		return TRACE.get();
	}

	private static long runStage(ArrayList<AsyncProjectile> active, BooleanSupplier shouldStop, Stage stage, AfterStage afterStage) {
		long start = System.nanoTime();
		StageTrace trace = TRACE.get();
		for (var e : active) {
			if (shouldStop.getAsBoolean()) return System.nanoTime() - start;
			var data = e.tickData;
			if (data.stopTick) continue;
			stage.run(e, data);
			if (afterStage != null) {
				afterStage.run(data, trace);
			}
		}
		return System.nanoTime() - start;
	}

	@FunctionalInterface
	private interface Stage {

		void run(AsyncProjectile projectile, AsyncProjectile.TickData data);

	}

	@FunctionalInterface
	private interface AfterStage {

		void run(AsyncProjectile.TickData data, StageTrace trace);

	}

}
