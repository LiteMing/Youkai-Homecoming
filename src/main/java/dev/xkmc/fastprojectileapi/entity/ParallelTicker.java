package dev.xkmc.fastprojectileapi.entity;

import dev.xkmc.fastprojectileapi.collision.IEntityIterator;
import dev.xkmc.fastprojectileapi.collision.UserMatrixCache;
import dev.xkmc.youkaishomecoming.content.spell.mover.MoverInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;

public class ParallelTicker {

	public static boolean ENABLE_ASYNC = true;
	public static boolean ENABLE_LOG = false;
	public static int LOG_INTERVAL = 1;
	static int logTickCounter = 0;

	private static final int ASYNC_THRESHOLD = 64;
	private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
	private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(
			Math.max(1, CPU_COUNT),
			r -> {
				Thread t = new Thread(r, "ParallelTicker-Worker");
				t.setDaemon(true);
				return t;
			}
	);

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
			if (e instanceof AsyncProjectile async) {
				active.add(async);
			} else {
				e.setOldPosAndRot();
				++e.tickCount;
				e.tick();
			}
			if (shouldStop.getAsBoolean()) return;
		}
		trace.projectileCount = active.size();
		boolean useAsync = ENABLE_ASYNC && active.size() >= ASYNC_THRESHOLD && !EXECUTOR.isShutdown();
		trace.asyncUsed = useAsync;

		MoverInfo.OwnerInfo sharedOwnerInfo = preheatCache != null && !active.isEmpty()
				? active.get(0).snapshotOwnerInfo(active.get(0).getOwner()) : null;

		trace.moveNanos = useAsync
				? runStageAsync(active, shouldStop, (e, data) -> {
					e.setOldPosAndRot();
					++e.tickCount;
					data.reset();
					e.beginTick(data, sharedOwnerInfo);
					e.planMove(data);
					e.planPreheatRange(data, preheatCache);
				})
				: runStage(active, shouldStop, (e, data) -> {
					e.setOldPosAndRot();
					++e.tickCount;
					data.reset();
					e.beginTick(data, sharedOwnerInfo);
					e.planMove(data);
					e.planPreheatRange(data, preheatCache);
				}, null);

		trace.moveNanos += runStage(active, shouldStop, (e, data) -> e.trimMove(data), null);
		trace.preheatNanos = 0L;
		if (preheatCache != null) {
			long start = System.nanoTime();
			preheatCache.flushPreheat();
			trace.preheatNanos = System.nanoTime() - start;
		}

		trace.collisionInputNanos = useAsync
				? runStageAsync(active, shouldStop, (e, data) -> {
					IEntityIterator iterator = preheatCache == null ? e.getEntityIterator() : preheatCache::asyncForEach;
					e.collectCollisionInput(data, iterator);
				})
				: runStage(active, shouldStop, (e, data) -> {
					IEntityIterator iterator = preheatCache == null ? e.getEntityIterator() : preheatCache::asyncForEach;
					e.collectCollisionInput(data, iterator);
				}, null);

		trace.resolveNanos = runStage(active, shouldStop, (e, data) -> e.resolveCollision(data), (data, result) -> {
			result.hitCount += data.hitEntities.size();
			result.grazeCount += data.grazeCount;
		});
		trace.finishNanos = runStage(active, shouldStop, (e, data) -> e.finishTick(data), (data, result) -> {
			result.candidateCount += data.candidateCount;
			if (data.removed) result.removedCount++;
		});
		trace.totalNanos = System.nanoTime() - totalStart;
		trace.log();
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

	private static long runStageAsync(ArrayList<AsyncProjectile> active, BooleanSupplier shouldStop, Stage stage) {
		int n = active.size();
		int numWorkers = Math.min(CPU_COUNT, n);
		int chunkSize = (n + numWorkers - 1) / numWorkers;
		List<Callable<Void>> tasks = new ArrayList<>(numWorkers);
		for (int w = 0; w < numWorkers; w++) {
			int from = w * chunkSize;
			int to = Math.min(from + chunkSize, n);
			tasks.add(() -> {
				for (int i = from; i < to; i++) {
					if (shouldStop.getAsBoolean()) return null;
					var e = active.get(i);
					var data = e.tickData;
					if (data.stopTick) continue;
					stage.run(e, data);
				}
				return null;
			});
		}
		long start = System.nanoTime();
		try {
			List<Future<Void>> futures = EXECUTOR.invokeAll(tasks);
			for (var f : futures) {
				f.get();
			}
		} catch (Exception ex) {
			for (var e : active) {
				if (shouldStop.getAsBoolean()) break;
				var data = e.tickData;
				if (data.stopTick) continue;
				stage.run(e, data);
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
