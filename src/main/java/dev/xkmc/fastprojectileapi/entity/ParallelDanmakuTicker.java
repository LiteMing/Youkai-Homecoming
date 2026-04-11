package dev.xkmc.fastprojectileapi.entity;

import com.mojang.logging.LogUtils;
import dev.xkmc.fastprojectileapi.collision.EntityStorageCache;
import dev.xkmc.fastprojectileapi.collision.EntityStorageHelper;
import dev.xkmc.fastprojectileapi.collision.IEntityCache;
import dev.xkmc.fastprojectileapi.collision.ProjectileHitHelper;
import dev.xkmc.fastprojectileapi.collision.UserCacheHolder;
import dev.xkmc.fastprojectileapi.render.virtual.DanmakuManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Parallel danmaku tick utility for virtual projectiles.
 * <p>
 * Hot-path data now stays on each projectile via {@link DanmakuVirtualTickData}:
 * prepare on the main thread, compute Step 1/2/3 in parallel, then run callbacks on
 * the main thread and apply movement in parallel again.
 */
public class ParallelDanmakuTicker {
	private static final long TRACE_WARN_INTERVAL_MS = 2000L;
	private static final ScheduledExecutorService TRACE_EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread thread = new Thread(r, "YH-DanmakuStageTrace");
		thread.setDaemon(true);
		return thread;
	});
	private static final AtomicLong TRACE_SEQUENCE = new AtomicLong();
	private static volatile boolean STAGE_TRACE_ENABLED = false;

	public static boolean isStageTraceEnabled() {
		return STAGE_TRACE_ENABLED;
	}

	public static void setStageTraceEnabled(boolean enabled) {
		STAGE_TRACE_ENABLED = enabled;
		LOGGER.info("Danmaku stage trace {}", enabled ? "enabled" : "disabled");
	}
	/**
	 * Resolve block hits in parallel with at most 2 concurrent {@code sl.clip()} calls.
	 * This avoids both the serial bottleneck of per-projectile clip() on the main thread
	 * and the thundering-herd hang from fully parallel clip() on ForkJoinPool.commonPool().
	 */
	private static void resolveBlockHitsParallel(ServerLevel sl, List<SimplifiedProjectile> allDanmakus) {
		ArrayList<Integer> indices = new ArrayList<>();
		for (int i = 0; i < allDanmakus.size(); i++) {
			DanmakuVirtualTickData data = allDanmakus.get(i).virtualTickData();
			if (data.isParallelReady() && data.checkBlock()) {
				indices.add(i);
			}
		}
		if (indices.isEmpty()) {
			return;
		}
		// Split into exactly 2 batches — limits concurrent sl.clip() to 2
		int split = (indices.size() + 1) / 2;
		ForkJoinTask<?> t1 = ForkJoinPool.commonPool().submit(() -> {
			for (int j = 0; j < split; j++) {
				doResolveBlockHit(sl, allDanmakus, indices.get(j));
			}
		});
		ForkJoinTask<?> t2 = ForkJoinPool.commonPool().submit(() -> {
			for (int j = split; j < indices.size(); j++) {
				doResolveBlockHit(sl, allDanmakus, indices.get(j));
			}
		});
		t1.join();
		t2.join();
	}

	private static void doResolveBlockHit(ServerLevel sl, List<SimplifiedProjectile> allDanmakus, int index) {
		DanmakuVirtualTickData data = allDanmakus.get(index).virtualTickData();
		Vec3 src = data.src();
		Vec3 dst = data.dst();
		if (src == null || dst == null) {
			return;
		}
		SimplifiedProjectile p = allDanmakus.get(index);
		HitResult bh = resolveBlockHit(sl, p, src, dst, true);
		data.setBlockHit(bh, bh == null ? dst : bh.getLocation());
	}

	@Nullable
	private static TickTrace startTickTrace(ServerLevel sl, int projectileCount, String path, @Nullable LivingEntity self) {
		if (!STAGE_TRACE_ENABLED) {
			return null;
		}
		TickTrace trace = new TickTrace(
				TRACE_SEQUENCE.incrementAndGet(),
				sl.getGameTime(),
				projectileCount,
				path,
				self == null ? "null" : self.getClass().getSimpleName() + "#" + self.getId()
		);
		trace.start();
		return trace;
	}

	private static StageTrace openStage(@Nullable TickTrace trace, String stage) {
		return trace == null ? StageTrace.NOOP : trace.openStage(stage);
	}

	private static final class TickTrace {
		private final long id;
		private final long gameTime;
		private final int projectileCount;
		private final String path;
		private final String owner;

		private TickTrace(long id, long gameTime, int projectileCount, String path, String owner) {
			this.id = id;
			this.gameTime = gameTime;
			this.projectileCount = projectileCount;
			this.path = path;
			this.owner = owner;
		}

		private void start() {
			LOGGER.info("Danmaku trace #{} start: gameTime={} projectiles={} path={} owner={}",
					id, gameTime, projectileCount, path, owner);
		}

		private StageTrace openStage(String stage) {
			return new StageTrace(this, stage);
		}

		private void finish(long totalNanos) {
			LOGGER.info("Danmaku trace #{} end: total={} ms", id, TimeUnit.NANOSECONDS.toMillis(totalNanos));
		}
	}

	private static final class StageTrace implements AutoCloseable {
		private static final StageTrace NOOP = new StageTrace();

		/**
	 * Resolve block hits in parallel with at most 2 concurrent {@code sl.clip()} calls.
	 * This avoids both the serial bottleneck of per-projectile clip() on the main thread
	 * and the thundering-herd hang from fully parallel clip() on ForkJoinPool.commonPool().
	 */
	private static void resolveBlockHitsParallel(ServerLevel sl, List<SimplifiedProjectile> allDanmakus) {
		ArrayList<Integer> indices = new ArrayList<>();
		for (int i = 0; i < allDanmakus.size(); i++) {
			DanmakuVirtualTickData data = allDanmakus.get(i).virtualTickData();
			if (data.isParallelReady() && data.checkBlock()) {
				indices.add(i);
			}
		}
		if (indices.isEmpty()) {
			return;
		}
		// Split into exactly 2 batches — limits concurrent sl.clip() to 2
		int split = (indices.size() + 1) / 2;
		ForkJoinTask<?> t1 = ForkJoinPool.commonPool().submit(() -> {
			for (int j = 0; j < split; j++) {
				doResolveBlockHit(sl, allDanmakus, indices.get(j));
			}
		});
		ForkJoinTask<?> t2 = ForkJoinPool.commonPool().submit(() -> {
			for (int j = split; j < indices.size(); j++) {
				doResolveBlockHit(sl, allDanmakus, indices.get(j));
			}
		});
		t1.join();
		t2.join();
	}

	private static void doResolveBlockHit(ServerLevel sl, List<SimplifiedProjectile> allDanmakus, int index) {
		DanmakuVirtualTickData data = allDanmakus.get(index).virtualTickData();
		Vec3 src = data.src();
		Vec3 dst = data.dst();
		if (src == null || dst == null) {
			return;
		}
		SimplifiedProjectile p = allDanmakus.get(index);
		HitResult bh = resolveBlockHit(sl, p, src, dst, true);
		data.setBlockHit(bh, bh == null ? dst : bh.getLocation());
	}

	@Nullable
		private final TickTrace trace;
		/**
	 * Resolve block hits in parallel with at most 2 concurrent {@code sl.clip()} calls.
	 * This avoids both the serial bottleneck of per-projectile clip() on the main thread
	 * and the thundering-herd hang from fully parallel clip() on ForkJoinPool.commonPool().
	 */
	private static void resolveBlockHitsParallel(ServerLevel sl, List<SimplifiedProjectile> allDanmakus) {
		ArrayList<Integer> indices = new ArrayList<>();
		for (int i = 0; i < allDanmakus.size(); i++) {
			DanmakuVirtualTickData data = allDanmakus.get(i).virtualTickData();
			if (data.isParallelReady() && data.checkBlock()) {
				indices.add(i);
			}
		}
		if (indices.isEmpty()) {
			return;
		}
		// Split into exactly 2 batches — limits concurrent sl.clip() to 2
		int split = (indices.size() + 1) / 2;
		ForkJoinTask<?> t1 = ForkJoinPool.commonPool().submit(() -> {
			for (int j = 0; j < split; j++) {
				doResolveBlockHit(sl, allDanmakus, indices.get(j));
			}
		});
		ForkJoinTask<?> t2 = ForkJoinPool.commonPool().submit(() -> {
			for (int j = split; j < indices.size(); j++) {
				doResolveBlockHit(sl, allDanmakus, indices.get(j));
			}
		});
		t1.join();
		t2.join();
	}

	private static void doResolveBlockHit(ServerLevel sl, List<SimplifiedProjectile> allDanmakus, int index) {
		DanmakuVirtualTickData data = allDanmakus.get(index).virtualTickData();
		Vec3 src = data.src();
		Vec3 dst = data.dst();
		if (src == null || dst == null) {
			return;
		}
		SimplifiedProjectile p = allDanmakus.get(index);
		HitResult bh = resolveBlockHit(sl, p, src, dst, true);
		data.setBlockHit(bh, bh == null ? dst : bh.getLocation());
	}

	@Nullable
		private final String stage;
		private final long startNanos;
		/**
	 * Resolve block hits in parallel with at most 2 concurrent {@code sl.clip()} calls.
	 * This avoids both the serial bottleneck of per-projectile clip() on the main thread
	 * and the thundering-herd hang from fully parallel clip() on ForkJoinPool.commonPool().
	 */
	private static void resolveBlockHitsParallel(ServerLevel sl, List<SimplifiedProjectile> allDanmakus) {
		ArrayList<Integer> indices = new ArrayList<>();
		for (int i = 0; i < allDanmakus.size(); i++) {
			DanmakuVirtualTickData data = allDanmakus.get(i).virtualTickData();
			if (data.isParallelReady() && data.checkBlock()) {
				indices.add(i);
			}
		}
		if (indices.isEmpty()) {
			return;
		}
		// Split into exactly 2 batches — limits concurrent sl.clip() to 2
		int split = (indices.size() + 1) / 2;
		ForkJoinTask<?> t1 = ForkJoinPool.commonPool().submit(() -> {
			for (int j = 0; j < split; j++) {
				doResolveBlockHit(sl, allDanmakus, indices.get(j));
			}
		});
		ForkJoinTask<?> t2 = ForkJoinPool.commonPool().submit(() -> {
			for (int j = split; j < indices.size(); j++) {
				doResolveBlockHit(sl, allDanmakus, indices.get(j));
			}
		});
		t1.join();
		t2.join();
	}

	private static void doResolveBlockHit(ServerLevel sl, List<SimplifiedProjectile> allDanmakus, int index) {
		DanmakuVirtualTickData data = allDanmakus.get(index).virtualTickData();
		Vec3 src = data.src();
		Vec3 dst = data.dst();
		if (src == null || dst == null) {
			return;
		}
		SimplifiedProjectile p = allDanmakus.get(index);
		HitResult bh = resolveBlockHit(sl, p, src, dst, true);
		data.setBlockHit(bh, bh == null ? dst : bh.getLocation());
	}

	@Nullable
		private final ScheduledFuture<?> warningTask;
		private volatile boolean closed;

		private StageTrace() {
			this.trace = null;
			this.stage = null;
			this.startNanos = 0L;
			this.warningTask = null;
			this.closed = true;
		}

		private StageTrace(TickTrace trace, String stage) {
			this.trace = trace;
			this.stage = stage;
			this.startNanos = System.nanoTime();
			LOGGER.info("Danmaku trace #{} -> {}", trace.id, stage);
			this.warningTask = TRACE_EXECUTOR.scheduleAtFixedRate(() -> {
				if (!closed) {
					long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
					LOGGER.warn("Danmaku trace #{} still in {} after {} ms", trace.id, stage, elapsed);
				}
			}, TRACE_WARN_INTERVAL_MS, TRACE_WARN_INTERVAL_MS, TimeUnit.MILLISECONDS);
		}

		@Override
		public void close() {
			if (closed || trace == null || stage == null) {
				return;
			}
			closed = true;
			if (warningTask != null) {
				warningTask.cancel(false);
			}
			long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
			LOGGER.info("Danmaku trace #{} <- {} ({} ms)", trace.id, stage, elapsed);
		}
	}

	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int PARALLEL_THRESHOLD = 500;
	private static final int MAX_THREADS = 4;
	private static final Object SUMMARY_LOCK = new Object();
	private static volatile TickStatsSnapshot LAST_STATS = TickStatsSnapshot.empty();
	private static TickSummaryAccumulator SUMMARY_STATS = new TickSummaryAccumulator();

	public static TickStatsSnapshot getLastStats() {
		return LAST_STATS;
	}

	public static TickSummarySnapshot getSummaryStats() {
		synchronized (SUMMARY_LOCK) {
			return SUMMARY_STATS.snapshot();
		}
	}

	public static void resetSummaryStats() {
		synchronized (SUMMARY_LOCK) {
			SUMMARY_STATS = new TickSummaryAccumulator();
		}
	}

	/**
	 * Tick all virtual danmaku with parallel optimization.
	 *
	 * @param sl          server level
	 * @param allDanmakus list of all virtual danmaku (modified: expired removed)
	 * @param temp        temp list for newly spawned danmaku (set by caller, populated by shoot())
	 * @param toBeSent    list of danmaku to send to clients this tick
	 * @param removeFlag  shared flag: set to true by onHit side effects (eraseAllDanmaku)
	 * @param cacheHolder entity cache holder
	 * @param self        the entity that owns the danmaku (YoukaiEntity or DanmakuProxyEntity)
	 */
	public static void tickAll(ServerLevel sl,
	                           List<SimplifiedProjectile> allDanmakus,
	                           ArrayList<SimplifiedProjectile> temp,
	                           ArrayList<SimplifiedProjectile> toBeSent,
	                           boolean[] removeFlag,
	                           @Nullable UserCacheHolder cacheHolder,
	                           @Nullable LivingEntity self) {
		long totalStart = System.nanoTime();
		String path = allDanmakus.isEmpty() ? "empty" : (allDanmakus.size() >= PARALLEL_THRESHOLD ? "parallel" : "sequential");
		TickTrace trace = startTickTrace(sl, allDanmakus.size(), path, self);
		try {
			long warmStart = System.nanoTime();
			long gameTime;
			try (StageTrace ignored = openStage(trace, "warmup")) {
				gameTime = warmCaches(sl, cacheHolder);
			}
			TickStatsBuilder stats = new TickStatsBuilder(gameTime, allDanmakus.size());
			stats.warmupNanos = System.nanoTime() - warmStart;
			if (allDanmakus.isEmpty()) {
				stats.totalNanos = System.nanoTime() - totalStart;
				LAST_STATS = stats.build();
				return;
			}
			if (allDanmakus.size() >= PARALLEL_THRESHOLD) {
				stats.parallelPath = true;
				tickParallel(sl, allDanmakus, temp, toBeSent, removeFlag, cacheHolder, self, gameTime, stats, trace);
			} else {
				tickSequential(sl, allDanmakus, temp, toBeSent, removeFlag, self, stats, trace);
			}
			stats.totalNanos = System.nanoTime() - totalStart;
			TickStatsSnapshot snapshot = stats.build();
			LAST_STATS = snapshot;
			synchronized (SUMMARY_LOCK) {
				SUMMARY_STATS.record(snapshot);
			}
		} finally {
			if (trace != null) {
				trace.finish(System.nanoTime() - totalStart);
			}
		}
	}

	private static long warmCaches(ServerLevel sl, @Nullable UserCacheHolder cacheHolder) {
		long gameTime = sl.getGameTime();
		if (cacheHolder != null) {
			cacheHolder.setGameTime(gameTime);
		}
		EntityStorageCache.get(sl, gameTime);
		return gameTime;
	}

	private static void tickSequential(ServerLevel sl,
	                                   List<SimplifiedProjectile> allDanmakus,
	                                   ArrayList<SimplifiedProjectile> temp,
	                                   ArrayList<SimplifiedProjectile> toBeSent,
	                                   boolean[] removeFlag,
	                                   @Nullable LivingEntity self,
	                                   TickStatsBuilder stats,
	                                   @Nullable TickTrace trace) {
		long start = System.nanoTime();
		try (StageTrace ignored = openStage(trace, "sequential")) {
			List<SimplifiedProjectile> toRemove = new ArrayList<>();
			for (SimplifiedProjectile projectile : allDanmakus) {
				if (shouldSkipVirtualTick(projectile)) {
					continue;
				}
				if (projectile.isRemoved()) {
					toRemove.add(projectile);
					continue;
				}
				if (projectile.isValid()) {
					sequentialTickOne(projectile);
				}
				if (removeFlag[0]) {
					break;
				}
				if (shouldRemoveFromVirtualList(projectile)) {
					toRemove.add(projectile);
				}
			}
			finalizeTick(allDanmakus, temp, toBeSent, removeFlag, self, toRemove);
		}
		stats.finishNanos = System.nanoTime() - start;
	}

	private static void tickParallel(ServerLevel sl,
	                                 List<SimplifiedProjectile> allDanmakus,
	                                 ArrayList<SimplifiedProjectile> temp,
	                                 ArrayList<SimplifiedProjectile> toBeSent,
	                                 boolean[] removeFlag,
	                                 @Nullable UserCacheHolder cacheHolder,
	                                 @Nullable LivingEntity self,
	                                 long gameTime,
	                                 TickStatsBuilder stats,
	                                 @Nullable TickTrace trace) {
		long start = System.nanoTime();
		try (StageTrace ignored = openStage(trace, "prepare")) {
			prepareParallelTick(allDanmakus, stats);
		}
		stats.prepareNanos = System.nanoTime() - start;

		start = System.nanoTime();
		Step1Summary step1;
		try (StageTrace ignored = openStage(trace, "step1")) {
			step1 = computeStep1(allDanmakus);
		}
		stats.step1Nanos = System.nanoTime() - start;
		stats.parallelReady = step1.readyCount();
		stats.step1Failures = step1.failures();

		TouchedSectionBounds bounds = step1.bounds();
		IEntityCache entityCache = resolveEntityCache(sl, cacheHolder, self, gameTime);
		TouchedSectionMask mask = bounds == null ? null : new TouchedSectionMask(bounds);
		start = System.nanoTime();
		try (StageTrace ignored = openStage(trace, "sections")) {
			if (mask != null) {
				markTouchedSections(allDanmakus, mask);
				warmTouchedSections(entityCache, mask);
			}
		}
		stats.touchedSectionNanos = System.nanoTime() - start;

		start = System.nanoTime();
		try (StageTrace ignored = openStage(trace, "step2")) {
			stats.step2Failures = computeStep2(allDanmakus, entityCache);
		}
		stats.step2Nanos = System.nanoTime() - start;

		start = System.nanoTime();
		try (StageTrace ignored = openStage(trace, "step3")) {
			stats.step3Failures = computeStep3(allDanmakus);
		}
		stats.step3Nanos = System.nanoTime() - start;

		start = System.nanoTime();
		try (StageTrace ignored = openStage(trace, "blockHit")) {
			resolveBlockHitsParallel(sl, allDanmakus);
		}
		stats.blockHitNanos = System.nanoTime() - start;

		start = System.nanoTime();
		try (StageTrace ignored = openStage(trace, "finish")) {
			finishParallelTick(sl, allDanmakus, temp, toBeSent, removeFlag, self, stats);
		}
		stats.finishNanos = System.nanoTime() - start;
	}

	private static void prepareParallelTick(List<SimplifiedProjectile> allDanmakus, TickStatsBuilder stats) {
		for (SimplifiedProjectile projectile : allDanmakus) {
			DanmakuVirtualTickData data = projectile.virtualTickData();
			data.reset();
			if (shouldSkipVirtualTick(projectile) || projectile.isRemoved() || !projectile.isValid()) {
				continue;
			}
			if (!(projectile instanceof BaseProjectile)) {
				data.markStandardSequentialFallback();
				continue;
			}
			projectile.setOldPosAndRot();
			++projectile.tickCount;
			projectile.baseTick();
			if (data.consumePrefetch(projectile.tickCount)) {
				stats.prefetchConsumed++;
			} else {
				data.markPreparedTickState();
			}
		}
	}

	private static Step1Summary computeStep1(List<SimplifiedProjectile> allDanmakus) {
		int size = allDanmakus.size();
		AtomicInteger minX = new AtomicInteger(Integer.MAX_VALUE);
		AtomicInteger minY = new AtomicInteger(Integer.MAX_VALUE);
		AtomicInteger minZ = new AtomicInteger(Integer.MAX_VALUE);
		AtomicInteger maxX = new AtomicInteger(Integer.MIN_VALUE);
		AtomicInteger maxY = new AtomicInteger(Integer.MIN_VALUE);
		AtomicInteger maxZ = new AtomicInteger(Integer.MIN_VALUE);
		AtomicInteger ready = new AtomicInteger();
		AtomicInteger failures = new AtomicInteger();
		runParallel(size, (from, to) -> {
			for (int i = from; i < to; i++) {
				SimplifiedProjectile projectile = allDanmakus.get(i);
				DanmakuVirtualTickData data = projectile.virtualTickData();
				if (data.isParallelReady()) {
					ready.incrementAndGet();
					accumulateMin(minX, data.sectionX0());
					accumulateMin(minY, data.sectionY0());
					accumulateMin(minZ, data.sectionZ0());
					accumulateMax(maxX, data.sectionX1());
					accumulateMax(maxY, data.sectionY1());
					accumulateMax(maxZ, data.sectionZ1());
					continue;
				}
				if (!data.hasPreparedTickState()) {
					continue;
				}
				try {
					computeStep1Result((BaseProjectile) projectile, data);
					ready.incrementAndGet();
					accumulateMin(minX, data.sectionX0());
					accumulateMin(minY, data.sectionY0());
					accumulateMin(minZ, data.sectionZ0());
					accumulateMax(maxX, data.sectionX1());
					accumulateMax(maxY, data.sectionY1());
					accumulateMax(maxZ, data.sectionZ1());
				} catch (Exception ex) {
					data.markPreparedSequentialFallback();
					failures.incrementAndGet();
				}
			}
		});
		if (failures.get() > 0) {
			LOGGER.warn("Parallel danmaku tick Step 1 fell back to prepared sequential for {} projectiles", failures.get());
		}
		if (ready.get() == 0) {
			return new Step1Summary(null, 0, failures.get());
		}
		return new Step1Summary(new TouchedSectionBounds(
				minX.get(), minY.get(), minZ.get(),
				maxX.get(), maxY.get(), maxZ.get()
		), ready.get(), failures.get());
	}

	private static void computeStep1Result(BaseProjectile projectile, DanmakuVirtualTickData data) {
		ProjectileMovement movement = projectile.computeMove();
		Vec3 src = projectile.position();
		Vec3 dst = src.add(movement.vec());
		float radius = projectile.getBbWidth() / 2f;
		float graze = projectile.grazeRange();
		AABB searchBox = projectile.getBoundingBox().expandTowards(movement.vec()).inflate(1 + radius + graze);
		data.markParallelReady(
				movement,
				src,
				dst,
				searchBox,
				radius,
				graze,
				projectile.checkBlockHit(),
				getSectionMin(searchBox.minX),
				getSectionMin(searchBox.minY),
				getSectionMin(searchBox.minZ),
				getSectionMax(searchBox.maxX),
				getSectionMax(searchBox.maxY),
				getSectionMax(searchBox.maxZ)
		);
	}

	private static void markTouchedSections(List<SimplifiedProjectile> allDanmakus, TouchedSectionMask mask) {
		int size = allDanmakus.size();
		runParallel(size, (from, to) -> {
			for (int i = from; i < to; i++) {
				DanmakuVirtualTickData data = allDanmakus.get(i).virtualTickData();
				if (data.isParallelReady()) {
					mask.mark(data);
				}
			}
		});
	}

	private static void warmTouchedSections(@Nullable IEntityCache entityCache, @Nullable TouchedSectionMask mask) {
		if (entityCache == null || mask == null) {
			return;
		}
		mask.forEach((x, y, z) -> entityCache.get(x, y, z));
	}

	/**
	 * Resolve block hits in parallel with at most 2 concurrent {@code sl.clip()} calls.
	 * This avoids both the serial bottleneck of per-projectile clip() on the main thread
	 * and the thundering-herd hang from fully parallel clip() on ForkJoinPool.commonPool().
	 */
	private static void resolveBlockHitsParallel(ServerLevel sl, List<SimplifiedProjectile> allDanmakus) {
		ArrayList<Integer> indices = new ArrayList<>();
		for (int i = 0; i < allDanmakus.size(); i++) {
			DanmakuVirtualTickData data = allDanmakus.get(i).virtualTickData();
			if (data.isParallelReady() && data.checkBlock()) {
				indices.add(i);
			}
		}
		if (indices.isEmpty()) {
			return;
		}
		// Split into exactly 2 batches — limits concurrent sl.clip() to 2
		int split = (indices.size() + 1) / 2;
		ForkJoinTask<?> t1 = ForkJoinPool.commonPool().submit(() -> {
			for (int j = 0; j < split; j++) {
				doResolveBlockHit(sl, allDanmakus, indices.get(j));
			}
		});
		ForkJoinTask<?> t2 = ForkJoinPool.commonPool().submit(() -> {
			for (int j = split; j < indices.size(); j++) {
				doResolveBlockHit(sl, allDanmakus, indices.get(j));
			}
		});
		t1.join();
		t2.join();
	}

	private static void doResolveBlockHit(ServerLevel sl, List<SimplifiedProjectile> allDanmakus, int index) {
		DanmakuVirtualTickData data = allDanmakus.get(index).virtualTickData();
		Vec3 src = data.src();
		Vec3 dst = data.dst();
		if (src == null || dst == null) {
			return;
		}
		SimplifiedProjectile p = allDanmakus.get(index);
		HitResult bh = resolveBlockHit(sl, p, src, dst, true);
		data.setBlockHit(bh, bh == null ? dst : bh.getLocation());
	}

	@Nullable
	private static IEntityCache resolveEntityCache(ServerLevel sl,
	                                               @Nullable UserCacheHolder cacheHolder,
	                                               @Nullable LivingEntity self,
	                                               long gameTime) {
		if (cacheHolder != null && self != null) {
			return cacheHolder.get(sl, self);
		}
		return EntityStorageCache.get(sl, gameTime);
	}

	private static int computeStep2(List<SimplifiedProjectile> allDanmakus,
	                                @Nullable IEntityCache entityCache) {
		int size = allDanmakus.size();
		AtomicInteger failures = new AtomicInteger();
		runParallel(size, (from, to) -> {
			for (int i = from; i < to; i++) {
				SimplifiedProjectile projectile = allDanmakus.get(i);
				DanmakuVirtualTickData data = projectile.virtualTickData();
				if (!data.isParallelReady()) {
					continue;
				}
				try {
					computeStep2Result((BaseProjectile) projectile, data, entityCache);
				} catch (Exception ex) {
					data.markPreparedSequentialFallback();
					failures.incrementAndGet();
				}
			}
		});
		if (failures.get() > 0) {
			LOGGER.warn("Parallel danmaku tick Step 2 fell back to prepared sequential for {} projectiles", failures.get());
		}
		return failures.get();
	}

	private static void computeStep2Result(BaseProjectile projectile,
	                                       DanmakuVirtualTickData data,
	                                       @Nullable IEntityCache entityCache) {
		AABB searchBox = data.searchBox();
		if (searchBox == null) {
			data.markPreparedSequentialFallback();
			return;
		}
		data.clearCandidates();
		collectCandidates(entityCache, searchBox, projectile, data);
	}

	private static void collectCandidates(@Nullable IEntityCache entityCache,
	                                      AABB searchBox,
	                                      SimplifiedProjectile projectile,
	                                      DanmakuVirtualTickData data) {
		if (entityCache == null) {
			return;
		}
		float radius = data.radius();
		float graze = data.graze();
		entityCache.visit(searchBox,
				data.sectionX0(), data.sectionY0(), data.sectionZ0(),
				data.sectionX1(), data.sectionY1(), data.sectionZ1(),
				projectile::canHitEntity,
				candidate -> {
					AABB hitBox = projectile.alterHitBox(candidate.entity(), candidate.boundingBox(), radius, 0);
					AABB grazeBox = graze > 0
							? projectile.alterHitBox(candidate.entity(), candidate.boundingBox(), radius, graze)
							: null;
					data.addCandidate(candidate.entity(), candidate.deltaMovement(), hitBox, grazeBox);
				});
	}

	private static int computeStep3(List<SimplifiedProjectile> allDanmakus) {
		int size = allDanmakus.size();
		AtomicInteger failures = new AtomicInteger();
		runParallel(size, (from, to) -> {
			for (int i = from; i < to; i++) {
				SimplifiedProjectile projectile = allDanmakus.get(i);
				DanmakuVirtualTickData data = projectile.virtualTickData();
				if (!data.isParallelReady()) {
					continue;
				}
				try {
					computeStep3Result(data);
				} catch (Exception ex) {
					data.clearHitAndGraze();
					failures.incrementAndGet();
				}
			}
		});
		if (failures.get() > 0) {
			LOGGER.warn("Parallel danmaku tick Step 3 skipped collision for {} projectiles", failures.get());
		}
		return failures.get();
	}

	private static void computeStep3Result(DanmakuVirtualTickData data) {
		Vec3 src = data.src();
		Vec3 effectiveDst = data.effectiveDst();
		if (src == null || effectiveDst == null) {
			data.clearHitAndGraze();
			return;
		}
		data.clearHitAndGraze();
		double closestDistance = Double.MAX_VALUE;
		Entity hitEntity = null;
		for (DanmakuVirtualTickData.PreparedCandidate candidate : data.candidates()) {
			Entity target = candidate.entity();
			Vec3 hitPos = ProjectileHitHelper.checkHit(candidate.hitBox(), src, effectiveDst, candidate.deltaMovement());
			if (hitPos != null) {
				double distance = src.distanceToSqr(hitPos);
				if (distance < closestDistance) {
					closestDistance = distance;
					hitEntity = target;
				}
				continue;
			}
			if (data.graze() > 0 && target instanceof Player player && candidate.grazeBox() != null) {
				if (ProjectileHitHelper.checkHit(candidate.grazeBox(), src, effectiveDst, candidate.deltaMovement()) != null
						&& !data.grazedPlayers().contains(player)) {
					data.addGraze(player);
				}
			}
		}
		data.setHitEntity(hitEntity);
	}

	private static void finishParallelTick(ServerLevel sl,
	                                       List<SimplifiedProjectile> allDanmakus,
	                                       ArrayList<SimplifiedProjectile> temp,
	                                       ArrayList<SimplifiedProjectile> toBeSent,
	                                       boolean[] removeFlag,
	                                       @Nullable LivingEntity self,
	                                       TickStatsBuilder stats) {
		List<SimplifiedProjectile> toRemove = new ArrayList<>();
		int standardFallbacks = 0;
		int preparedFallbacks = 0;
		int queuedMoves = 0;
		for (SimplifiedProjectile projectile : allDanmakus) {
			if (shouldSkipVirtualTick(projectile)) {
				continue;
			}
			DanmakuVirtualTickData data = projectile.virtualTickData();
			if (data.usesStandardSequentialFallback()) {
				standardFallbacks++;
				sequentialTickOne(projectile);
				if (removeFlag[0]) {
					break;
				}
				if (shouldRemoveFromVirtualList(projectile)) {
					toRemove.add(projectile);
				}
				continue;
			}
			if (data.usesPreparedSequentialFallback()) {
				preparedFallbacks++;
				if (projectile instanceof BaseProjectile bp) {
					finishPreparedSequentialTick(sl, bp);
				}
				if (removeFlag[0]) {
					break;
				}
				if (shouldRemoveFromVirtualList(projectile)) {
					toRemove.add(projectile);
				}
				continue;
			}
			if (!data.isParallelReady()) {
				if (shouldRemoveFromVirtualList(projectile)) {
					toRemove.add(projectile);
				}
				continue;
			}
			BaseProjectile bp = (BaseProjectile) projectile;
			bp.beforeMoveTick();
			for (Player player : data.grazedPlayers()) {
				projectile.doGraze(player);
			}
			HitResult finalHit = data.blockHit();
			if (data.hitEntity() != null) {
				finalHit = new EntityHitResult(data.hitEntity());
			}
			if (finalHit != null) {
				bp.onHit(finalHit);
			}
			if (removeFlag[0]) {
				break;
			}
			data.queueApplyMove();
			queuedMoves++;
		}
		stats.standardSequentialFallbacks = standardFallbacks;
		stats.preparedSequentialFallbacks = preparedFallbacks;
		stats.queuedMoves = queuedMoves;
		if (!removeFlag[0]) {
			stats.applyFailures = applyResolvedMoves(allDanmakus);
			finalizeResolvedMoves(sl, allDanmakus, toRemove);
			PrefetchSummary prefetch = prefetchNextStep1(allDanmakus);
			stats.prefetchEligible = prefetch.eligible();
			stats.prefetchStored = prefetch.stored();
			stats.prefetchFailures = prefetch.failures();
		}
		finalizeTick(allDanmakus, temp, toBeSent, removeFlag, self, toRemove);
	}

	private static void finishPreparedSequentialTick(ServerLevel sl, BaseProjectile projectile) {
		projectile.beforeMoveTick();
		ProjectileMovement movement = projectile.computeMove();
		HitResult hitResult = ProjectileHitHelper.getHitResultOnMoveVector(projectile, movement, projectile.checkBlockHit());
		if (hitResult != null) {
			projectile.onHit(hitResult);
		}
		projectile.applyMove(movement);
		if (projectile.tickCount >= projectile.lifetime() && sl == projectile.level()) {
			projectile.terminate();
			projectile.markErased(false);
			return;
		}
		if (shouldEraseAfterMove(sl, projectile)) {
			projectile.markErased(false);
		}
	}

	private static int applyResolvedMoves(List<SimplifiedProjectile> allDanmakus) {
		int size = allDanmakus.size();
		AtomicInteger failures = new AtomicInteger();
		runParallel(size, (from, to) -> {
			for (int i = from; i < to; i++) {
				SimplifiedProjectile projectile = allDanmakus.get(i);
				DanmakuVirtualTickData data = projectile.virtualTickData();
				if (!data.isApplyMovePending()) {
					continue;
				}
				try {
					ProjectileMovement movement = data.movement();
					if (movement != null) {
						((BaseProjectile) projectile).applyMove(movement);
					}
				} catch (Exception ex) {
					failures.incrementAndGet();
				}
			}
		});
		if (failures.get() > 0) {
			LOGGER.warn("Parallel danmaku tick movement apply failed for {} projectiles", failures.get());
		}
		return failures.get();
	}

	private static void finalizeResolvedMoves(ServerLevel sl,
	                                          List<SimplifiedProjectile> allDanmakus,
	                                          List<SimplifiedProjectile> toRemove) {
		for (SimplifiedProjectile projectile : allDanmakus) {
			if (shouldSkipVirtualTick(projectile)) {
				continue;
			}
			DanmakuVirtualTickData data = projectile.virtualTickData();
			if (!data.isApplyMovePending()) {
				continue;
			}
			data.clearApplyMovePending();
			BaseProjectile bp = (BaseProjectile) projectile;
			if (projectile.tickCount >= bp.lifetime() && sl == projectile.level()) {
				bp.terminate();
				projectile.markErased(false);
			} else if (shouldEraseAfterMove(sl, projectile)) {
				projectile.markErased(false);
			}
			if (shouldRemoveFromVirtualList(projectile)) {
				toRemove.add(projectile);
			}
		}
	}

	private static PrefetchSummary prefetchNextStep1(List<SimplifiedProjectile> allDanmakus) {
		int size = allDanmakus.size();
		AtomicInteger eligible = new AtomicInteger();
		AtomicInteger stored = new AtomicInteger();
		AtomicInteger failures = new AtomicInteger();
		runParallel(size, (from, to) -> {
			for (int i = from; i < to; i++) {
				SimplifiedProjectile projectile = allDanmakus.get(i);
				DanmakuVirtualTickData data = projectile.virtualTickData();
				data.clearPrefetch();
				if (shouldSkipVirtualTick(projectile) || shouldRemoveFromVirtualList(projectile) ||
						!(projectile instanceof BaseProjectile bp) || !bp.allowNextTickStep1Prefetch()) {
					continue;
				}
				eligible.incrementAndGet();
				try {
					storePrefetchedStep1(bp, data);
					stored.incrementAndGet();
				} catch (Exception ex) {
					failures.incrementAndGet();
				}
			}
		});
		if (failures.get() > 0) {
			LOGGER.warn("Parallel danmaku tick next-step prefetch failed for {} projectiles", failures.get());
		}
		return new PrefetchSummary(eligible.get(), stored.get(), failures.get());
	}

	private static void storePrefetchedStep1(BaseProjectile projectile, DanmakuVirtualTickData data) {
		ProjectileMovement movement = projectile.computeMoveForTick(projectile.tickCount + 1);
		Vec3 src = projectile.position();
		Vec3 dst = src.add(movement.vec());
		float radius = projectile.getBbWidth() / 2f;
		float graze = projectile.grazeRange();
		AABB searchBox = projectile.getBoundingBox().expandTowards(movement.vec()).inflate(1 + radius + graze);
		data.storePrefetch(
				projectile.tickCount + 1,
				movement,
				src,
				dst,
				searchBox,
				radius,
				graze,
				projectile.checkBlockHit(),
				getSectionMin(searchBox.minX),
				getSectionMin(searchBox.minY),
				getSectionMin(searchBox.minZ),
				getSectionMax(searchBox.maxX),
				getSectionMax(searchBox.maxY),
				getSectionMax(searchBox.maxZ)
		);
	}

	private static void sequentialTickOne(SimplifiedProjectile projectile) {
		projectile.setOldPosAndRot();
		++projectile.tickCount;
		projectile.tick();
	}

	private static boolean shouldEraseAfterMove(ServerLevel sl, SimplifiedProjectile projectile) {
		if (!projectile.level().hasChunk(projectile.blockPosition().getX() >> 4, projectile.blockPosition().getZ() >> 4)) {
			return true;
		}
		return projectile.isAddedToWorld() && !EntityStorageHelper.isTicking(sl, projectile);
	}

	/**
	 * Resolve block hits in parallel with at most 2 concurrent {@code sl.clip()} calls.
	 * This avoids both the serial bottleneck of per-projectile clip() on the main thread
	 * and the thundering-herd hang from fully parallel clip() on ForkJoinPool.commonPool().
	 */
	private static void resolveBlockHitsParallel(ServerLevel sl, List<SimplifiedProjectile> allDanmakus) {
		ArrayList<Integer> indices = new ArrayList<>();
		for (int i = 0; i < allDanmakus.size(); i++) {
			DanmakuVirtualTickData data = allDanmakus.get(i).virtualTickData();
			if (data.isParallelReady() && data.checkBlock()) {
				indices.add(i);
			}
		}
		if (indices.isEmpty()) {
			return;
		}
		// Split into exactly 2 batches — limits concurrent sl.clip() to 2
		int split = (indices.size() + 1) / 2;
		ForkJoinTask<?> t1 = ForkJoinPool.commonPool().submit(() -> {
			for (int j = 0; j < split; j++) {
				doResolveBlockHit(sl, allDanmakus, indices.get(j));
			}
		});
		ForkJoinTask<?> t2 = ForkJoinPool.commonPool().submit(() -> {
			for (int j = split; j < indices.size(); j++) {
				doResolveBlockHit(sl, allDanmakus, indices.get(j));
			}
		});
		t1.join();
		t2.join();
	}

	private static void doResolveBlockHit(ServerLevel sl, List<SimplifiedProjectile> allDanmakus, int index) {
		DanmakuVirtualTickData data = allDanmakus.get(index).virtualTickData();
		Vec3 src = data.src();
		Vec3 dst = data.dst();
		if (src == null || dst == null) {
			return;
		}
		SimplifiedProjectile p = allDanmakus.get(index);
		HitResult bh = resolveBlockHit(sl, p, src, dst, true);
		data.setBlockHit(bh, bh == null ? dst : bh.getLocation());
	}

	@Nullable
	private static HitResult resolveBlockHit(ServerLevel sl,
	                                         SimplifiedProjectile projectile,
	                                         Vec3 src,
	                                         Vec3 dst,
	                                         boolean checkBlock) {
		if (!checkBlock) {
			return null;
		}
		HitResult blockHit = sl.clip(new ClipContext(
				src,
				dst,
				ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE,
				projectile
		));
		return blockHit.getType() == HitResult.Type.MISS ? null : blockHit;
	}

	private static int getSectionMin(double coordinate) {
		return (((int) coordinate) >> 4) - 1;
	}

	private static int getSectionMax(double coordinate) {
		return (((int) coordinate) >> 4) + 1;
	}

	private static boolean shouldSkipVirtualTick(SimplifiedProjectile projectile) {
		return projectile.isAddedToWorld() && !projectile.isRemoved();
	}

	private static void finalizeTick(List<SimplifiedProjectile> allDanmakus,
	                                 ArrayList<SimplifiedProjectile> temp,
	                                 ArrayList<SimplifiedProjectile> toBeSent,
	                                 boolean[] removeFlag,
	                                 @Nullable LivingEntity self,
	                                 List<SimplifiedProjectile> toRemove) {
		if (!toRemove.isEmpty()) {
			allDanmakus.removeAll(new java.util.HashSet<>(toRemove));
		}
		if (!removeFlag[0]) {
			allDanmakus.addAll(temp);
			DanmakuManager.send(self, toBeSent);
		}
	}

	private static boolean shouldRemoveFromVirtualList(SimplifiedProjectile projectile) {
		return projectile.isRemoved() || !projectile.isValid();
	}

	private static void accumulateMin(AtomicInteger target, int value) {
		while (true) {
			int current = target.get();
			if (value >= current) {
				return;
			}
			if (target.compareAndSet(current, value)) {
				return;
			}
		}
	}

	private static void accumulateMax(AtomicInteger target, int value) {
		while (true) {
			int current = target.get();
			if (value <= current) {
				return;
			}
			if (target.compareAndSet(current, value)) {
				return;
			}
		}
	}

	private static final int BATCH_PER_THREAD = 2000;

	private static void runParallel(int size, RangeTask task) {
		int maxThreads = Runtime.getRuntime().availableProcessors();
		int threads = Math.min(Math.max(MAX_THREADS, (size + BATCH_PER_THREAD - 1) / BATCH_PER_THREAD), maxThreads);
		if (threads <= 1) {
			threads = 2;
		}
		int chunkSize = (size + threads - 1) / threads;
		ForkJoinTask<?>[] tasks = new ForkJoinTask<?>[threads];
		for (int t = 0; t < threads; t++) {
			int from = t * chunkSize;
			int to = Math.min(from + chunkSize, size);
			if (from >= to) {
				continue;
			}
			int taskFrom = from;
			int taskTo = to;
			tasks[t] = ForkJoinPool.commonPool().submit(() -> task.run(taskFrom, taskTo));
		}
		for (ForkJoinTask<?> submitted : tasks) {
			if (submitted != null) {
				submitted.join();
			}
		}
	}

	@FunctionalInterface
	private interface RangeTask {
		void run(int from, int to);
	}

	@FunctionalInterface
	private interface SectionVisitor {
		void accept(int x, int y, int z);
	}

	private record Step1Summary(@Nullable TouchedSectionBounds bounds, int readyCount, int failures) {
	}

	private record PrefetchSummary(int eligible, int stored, int failures) {
	}

	public record TickStatsSnapshot(
			long gameTime,
			int projectileCount,
			boolean parallelPath,
			int parallelReady,
			int prefetchConsumed,
			int prefetchEligible,
			int prefetchStored,
			int prefetchFailures,
			int standardSequentialFallbacks,
			int preparedSequentialFallbacks,
			int step1Failures,
			int step2Failures,
			int step3Failures,
			int applyFailures,
			int queuedMoves,
			long warmupNanos,
			long prepareNanos,
			long step1Nanos,
			long touchedSectionNanos,
			long step2Nanos,
			long step3Nanos,
			long blockHitNanos,
			long finishNanos,
			long totalNanos
	) {

		private static TickStatsSnapshot empty() {
			return new TickStatsSnapshot(
					-1,
					0,
					false,
					0,
					0,
					0,
					0,
					0,
					0,
					0,
					0,
					0,
					0,
					0,
					0,
					0L,
					0L,
					0L,
					0L,
					0L,
					0L,
					0L,
					0L,
					0L
			);
		}

		public boolean hasData() {
			return gameTime >= 0;
		}
	}

	public record TickSummarySnapshot(
			long sampleCount,
			long parallelSamples,
			long sequentialSamples,
			long projectileSum,
			int maxProjectiles,
			long parallelReadySum,
			int maxParallelReady,
			long prefetchConsumedSum,
			long prefetchEligibleSum,
			long prefetchStoredSum,
			long prefetchFailuresSum,
			long standardSequentialFallbacksSum,
			long preparedSequentialFallbacksSum,
			long step1FailuresSum,
			long step2FailuresSum,
			long step3FailuresSum,
			long applyFailuresSum,
			long queuedMovesSum,
			long warmupNanosSum,
			long prepareNanosSum,
			long step1NanosSum,
			long touchedSectionNanosSum,
			long step2NanosSum,
			long step3NanosSum,
			long blockHitNanosSum,
			long finishNanosSum,
			long totalNanosSum,
			long maxTotalNanos,
			long maxStep1Nanos,
			long maxStep2Nanos,
			long maxStep3Nanos,
			long maxBlockHitNanos,
			long maxFinishNanos
	) {

		public boolean hasData() {
			return sampleCount > 0;
		}
	}

	private static final class TickStatsBuilder {

		private final long gameTime;
		private final int projectileCount;
		private boolean parallelPath;
		private int parallelReady;
		private int prefetchConsumed;
		private int prefetchEligible;
		private int prefetchStored;
		private int prefetchFailures;
		private int standardSequentialFallbacks;
		private int preparedSequentialFallbacks;
		private int step1Failures;
		private int step2Failures;
		private int step3Failures;
		private int applyFailures;
		private int queuedMoves;
		private long warmupNanos;
		private long prepareNanos;
		private long step1Nanos;
		private long touchedSectionNanos;
		private long step2Nanos;
		private long step3Nanos;
		private long blockHitNanos;
		private long finishNanos;
		private long totalNanos;

		private TickStatsBuilder(long gameTime, int projectileCount) {
			this.gameTime = gameTime;
			this.projectileCount = projectileCount;
		}

		private TickStatsSnapshot build() {
			return new TickStatsSnapshot(
					gameTime,
					projectileCount,
					parallelPath,
					parallelReady,
					prefetchConsumed,
					prefetchEligible,
					prefetchStored,
					prefetchFailures,
					standardSequentialFallbacks,
					preparedSequentialFallbacks,
					step1Failures,
					step2Failures,
					step3Failures,
					applyFailures,
					queuedMoves,
					warmupNanos,
					prepareNanos,
					step1Nanos,
					touchedSectionNanos,
					step2Nanos,
					step3Nanos,
					blockHitNanos,
					finishNanos,
					totalNanos
			);
		}
	}

	private static final class TickSummaryAccumulator {

		private long sampleCount;
		private long parallelSamples;
		private long sequentialSamples;
		private long projectileSum;
		private int maxProjectiles;
		private long parallelReadySum;
		private int maxParallelReady;
		private long prefetchConsumedSum;
		private long prefetchEligibleSum;
		private long prefetchStoredSum;
		private long prefetchFailuresSum;
		private long standardSequentialFallbacksSum;
		private long preparedSequentialFallbacksSum;
		private long step1FailuresSum;
		private long step2FailuresSum;
		private long step3FailuresSum;
		private long applyFailuresSum;
		private long queuedMovesSum;
		private long warmupNanosSum;
		private long prepareNanosSum;
		private long step1NanosSum;
		private long touchedSectionNanosSum;
		private long step2NanosSum;
		private long step3NanosSum;
		private long blockHitNanosSum;
		private long finishNanosSum;
		private long totalNanosSum;
		private long maxTotalNanos;
		private long maxStep1Nanos;
		private long maxStep2Nanos;
		private long maxStep3Nanos;
		private long maxBlockHitNanos;
		private long maxFinishNanos;

		private void record(TickStatsSnapshot stats) {
			sampleCount++;
			if (stats.parallelPath()) {
				parallelSamples++;
			} else {
				sequentialSamples++;
			}
			projectileSum += stats.projectileCount();
			maxProjectiles = Math.max(maxProjectiles, stats.projectileCount());
			parallelReadySum += stats.parallelReady();
			maxParallelReady = Math.max(maxParallelReady, stats.parallelReady());
			prefetchConsumedSum += stats.prefetchConsumed();
			prefetchEligibleSum += stats.prefetchEligible();
			prefetchStoredSum += stats.prefetchStored();
			prefetchFailuresSum += stats.prefetchFailures();
			standardSequentialFallbacksSum += stats.standardSequentialFallbacks();
			preparedSequentialFallbacksSum += stats.preparedSequentialFallbacks();
			step1FailuresSum += stats.step1Failures();
			step2FailuresSum += stats.step2Failures();
			step3FailuresSum += stats.step3Failures();
			applyFailuresSum += stats.applyFailures();
			queuedMovesSum += stats.queuedMoves();
			warmupNanosSum += stats.warmupNanos();
			prepareNanosSum += stats.prepareNanos();
			step1NanosSum += stats.step1Nanos();
			touchedSectionNanosSum += stats.touchedSectionNanos();
			step2NanosSum += stats.step2Nanos();
			step3NanosSum += stats.step3Nanos();
			blockHitNanosSum += stats.blockHitNanos();
			finishNanosSum += stats.finishNanos();
			totalNanosSum += stats.totalNanos();
			maxTotalNanos = Math.max(maxTotalNanos, stats.totalNanos());
			maxStep1Nanos = Math.max(maxStep1Nanos, stats.step1Nanos());
			maxStep2Nanos = Math.max(maxStep2Nanos, stats.step2Nanos());
			maxStep3Nanos = Math.max(maxStep3Nanos, stats.step3Nanos());
			maxBlockHitNanos = Math.max(maxBlockHitNanos, stats.blockHitNanos());
			maxFinishNanos = Math.max(maxFinishNanos, stats.finishNanos());
		}

		private TickSummarySnapshot snapshot() {
			return new TickSummarySnapshot(
					sampleCount,
					parallelSamples,
					sequentialSamples,
					projectileSum,
					maxProjectiles,
					parallelReadySum,
					maxParallelReady,
					prefetchConsumedSum,
					prefetchEligibleSum,
					prefetchStoredSum,
					prefetchFailuresSum,
					standardSequentialFallbacksSum,
					preparedSequentialFallbacksSum,
					step1FailuresSum,
					step2FailuresSum,
					step3FailuresSum,
					applyFailuresSum,
					queuedMovesSum,
					warmupNanosSum,
					prepareNanosSum,
					step1NanosSum,
					touchedSectionNanosSum,
					step2NanosSum,
					step3NanosSum,
					blockHitNanosSum,
					finishNanosSum,
					totalNanosSum,
					maxTotalNanos,
					maxStep1Nanos,
					maxStep2Nanos,
					maxStep3Nanos,
					maxBlockHitNanos,
					maxFinishNanos
			);
		}
	}

	private static final class TouchedSectionBounds {

		private final int minX;
		private final int minY;
		private final int minZ;
		private final int maxX;
		private final int maxY;
		private final int maxZ;

		private TouchedSectionBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
			this.minX = minX;
			this.minY = minY;
			this.minZ = minZ;
			this.maxX = maxX;
			this.maxY = maxY;
			this.maxZ = maxZ;
		}
	}

	private static final class TouchedSectionMask {

		private final int minX;
		private final int minY;
		private final int minZ;
		private final int spanY;
		private final int spanZ;
		private final int plane;
		private final AtomicBitSet bits;

		private TouchedSectionMask(TouchedSectionBounds bounds) {
			minX = bounds.minX;
			minY = bounds.minY;
			minZ = bounds.minZ;
			int spanX = bounds.maxX - bounds.minX + 1;
			spanY = bounds.maxY - bounds.minY + 1;
			spanZ = bounds.maxZ - bounds.minZ + 1;
			long volume = 1L * spanX * spanY * spanZ;
			if (volume <= 0 || volume > Integer.MAX_VALUE) {
				throw new IllegalArgumentException("Touched section mask is too large: " + volume);
			}
			plane = spanY * spanZ;
			bits = new AtomicBitSet((int) volume);
		}

		private void mark(DanmakuVirtualTickData data) {
			for (int x = data.sectionX0(); x <= data.sectionX1(); x++) {
				for (int y = data.sectionY0(); y <= data.sectionY1(); y++) {
					for (int z = data.sectionZ0(); z <= data.sectionZ1(); z++) {
						bits.set(index(x, y, z));
					}
				}
			}
		}

		private void forEach(SectionVisitor visitor) {
			for (int bit = bits.nextSetBit(0); bit >= 0; bit = bits.nextSetBit(bit + 1)) {
				int dx = bit / plane;
				int remain = bit % plane;
				int dy = remain / spanZ;
				int dz = remain % spanZ;
				visitor.accept(minX + dx, minY + dy, minZ + dz);
			}
		}

		private int index(int x, int y, int z) {
			return ((x - minX) * spanY + (y - minY)) * spanZ + (z - minZ);
		}
	}

}
