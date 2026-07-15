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

	public static void tickAll(ArrayList<AsyncProjectile> all, BooleanSupplier shouldStop, UserMatrixCache preheatCache) {
		StageTrace trace = TRACE.get();
		trace.reset();
		long totalStart = System.nanoTime();
		ArrayList<AsyncProjectile> active = new ArrayList<>(all);
		trace.projectileCount = active.size();
		boolean useAsync = ENABLE_ASYNC && active.size() >= ASYNC_THRESHOLD && !EXECUTOR.isShutdown();
		trace.asyncUsed = useAsync;

		MoverInfo.OwnerInfo sharedOwnerInfo = active.isEmpty()
				? null : active.get(0).snapshotOwnerInfo(active.get(0).getOwner());

		trace.planNanos = runStageAsync(active, shouldStop, (e, data) -> {
				e.setOldPosAndRot();
				++e.tickCount;
				data.reset();
				data.preheatCache = preheatCache;
				e.beginTick(data, sharedOwnerInfo);
				e.planMove(data);
				e.planPreheatRange(data, preheatCache);
			}, useAsync);

		trace.trimNanos = runStage(active, shouldStop, (e, data) -> e.trimMove(data));
		long preheatStart = System.nanoTime();
		preheatCache.flushPreheat();
		trace.preheatNanos = System.nanoTime() - preheatStart;

		long collisionStart = System.nanoTime();
		runStageAsync(active, shouldStop, (e, data) -> e.collectCollisionInput(data, preheatCache::asyncForEach), useAsync);
		runStage(active, shouldStop, (e, data) -> e.applyMoveTick(data));
		trace.collisionInputNanos = System.nanoTime() - collisionStart;

		trace.resolveNanos = runStageWithAfter(active, shouldStop, (e, data) -> e.resolveCollision(data), (data, result) -> {
			result.hitCount += data.hitEntities.size();
			result.grazeCount += data.grazeCount;
		});
		trace.finishNanos = runStageWithAfter(active, shouldStop, (e, data) -> e.finishTick(data), (data, result) -> {
			result.candidateCount += data.candidateCount;
			if (data.removed) result.removedCount++;
		});
		trace.totalNanos = System.nanoTime() - totalStart;
		trace.log();
	}

	public static StageTrace currentTrace() {
		return TRACE.get();
	}

	private static long runStage(ArrayList<AsyncProjectile> active, BooleanSupplier shouldStop, Stage stage) {
		if (shouldStop.getAsBoolean()) return 0L;
		long start = System.nanoTime();
		int n = active.size();
		for (int i = 0; i < n; i++) {
			var e = active.get(i);
			var data = e.tickData;
			if (!data.stopTick) stage.run(e, data);
		}
		return System.nanoTime() - start;
	}

	private static long runStageWithAfter(ArrayList<AsyncProjectile> active, BooleanSupplier shouldStop, Stage stage, AfterStage afterStage) {
		if (shouldStop.getAsBoolean()) return 0L;
		long start = System.nanoTime();
		StageTrace trace = TRACE.get();
		int n = active.size();
		for (int i = 0; i < n; i++) {
			var e = active.get(i);
			var data = e.tickData;
			if (data.stopTick) continue;
			stage.run(e, data);
			afterStage.run(data, trace);
		}
		return System.nanoTime() - start;
	}

	private static long runStageAsync(ArrayList<AsyncProjectile> active, BooleanSupplier shouldStop, Stage stage, boolean async) {
		if (!async) return runStage(active, shouldStop, stage);
		if (shouldStop.getAsBoolean()) return 0L;
		int n = active.size();
		int numWorkers = Math.min(CPU_COUNT, n);
		int chunkSize = (n + numWorkers - 1) / numWorkers;
		List<Callable<Void>> tasks = new ArrayList<>(numWorkers);
		for (int w = 0; w < numWorkers; w++) {
			int from = w * chunkSize;
			int to = Math.min(from + chunkSize, n);
			tasks.add(() -> {
				for (int i = from; i < to; i++) {
					var e = active.get(i);
					var data = e.tickData;
					if (!data.stopTick) stage.run(e, data);
				}
				return null;
			});
		}
		long start = System.nanoTime();
		List<Future<Void>> futures;
		try {
			futures = EXECUTOR.invokeAll(tasks);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return runStage(active, shouldStop, stage);
		}
		for (int w = 0; w < numWorkers; w++) {
			try {
				futures.get(w).get();
			} catch (Exception ex) {
				int from = w * chunkSize, to = Math.min(from + chunkSize, n);
				for (int i = from; i < to; i++) {
					var e = active.get(i);
					var data = e.tickData;
					if (!data.stopTick) stage.run(e, data);
				}
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
