package dev.xkmc.fastprojectileapi.entity;

import com.mojang.logging.LogUtils;
import dev.xkmc.fastprojectileapi.collision.EntityStorageCache;
import dev.xkmc.fastprojectileapi.collision.EntityStorageHelper;
import dev.xkmc.fastprojectileapi.collision.IEntityCache;
import dev.xkmc.fastprojectileapi.collision.ProjectileHitHelper;
import dev.xkmc.fastprojectileapi.collision.SectionCache;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int PARALLEL_THRESHOLD = 500;
	private static final int MAX_THREADS = 4;

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
		long gameTime = warmCaches(sl, cacheHolder);
		if (allDanmakus.isEmpty()) {
			return;
		}
		if (allDanmakus.size() >= PARALLEL_THRESHOLD) {
			tickParallel(sl, allDanmakus, temp, toBeSent, removeFlag, cacheHolder, self, gameTime);
		} else {
			tickSequential(sl, allDanmakus, temp, toBeSent, removeFlag, self);
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
	                                   @Nullable LivingEntity self) {
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

	private static void tickParallel(ServerLevel sl,
	                                 List<SimplifiedProjectile> allDanmakus,
	                                 ArrayList<SimplifiedProjectile> temp,
	                                 ArrayList<SimplifiedProjectile> toBeSent,
	                                 boolean[] removeFlag,
	                                 @Nullable UserCacheHolder cacheHolder,
	                                 @Nullable LivingEntity self,
	                                 long gameTime) {
		prepareParallelTick(allDanmakus);
		TouchedSectionBounds bounds = computeStep1(allDanmakus);
		IEntityCache entityCache = resolveEntityCache(sl, cacheHolder, self, gameTime);
		TouchedSectionMask mask = bounds == null ? null : new TouchedSectionMask(bounds);
		if (mask != null) {
			markTouchedSections(allDanmakus, mask);
			warmTouchedSections(entityCache, mask);
		}
		computeStep2(sl, allDanmakus, entityCache);
		computeStep3(allDanmakus);
		finishParallelTick(sl, allDanmakus, temp, toBeSent, removeFlag, self);
	}

	private static void prepareParallelTick(List<SimplifiedProjectile> allDanmakus) {
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
			data.markPreparedTickState();
		}
	}

	@Nullable
	private static TouchedSectionBounds computeStep1(List<SimplifiedProjectile> allDanmakus) {
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
			return null;
		}
		return new TouchedSectionBounds(
				minX.get(), minY.get(), minZ.get(),
				maxX.get(), maxY.get(), maxZ.get()
		);
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

	private static void computeStep2(ServerLevel sl,
	                                 List<SimplifiedProjectile> allDanmakus,
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
					computeStep2Result(sl, (BaseProjectile) projectile, data, entityCache);
				} catch (Exception ex) {
					data.markPreparedSequentialFallback();
					failures.incrementAndGet();
				}
			}
		});
		if (failures.get() > 0) {
			LOGGER.warn("Parallel danmaku tick Step 2 fell back to prepared sequential for {} projectiles", failures.get());
		}
	}

	private static void computeStep2Result(ServerLevel sl,
	                                       BaseProjectile projectile,
	                                       DanmakuVirtualTickData data,
	                                       @Nullable IEntityCache entityCache) {
		Vec3 src = data.src();
		Vec3 dst = data.dst();
		AABB searchBox = data.searchBox();
		if (src == null || dst == null || searchBox == null) {
			data.markPreparedSequentialFallback();
			return;
		}
		HitResult blockHit = resolveBlockHit(sl, projectile, src, dst, data.checkBlock());
		data.setBlockHit(blockHit, blockHit == null ? dst : blockHit.getLocation());
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
		for (int x = data.sectionX0(); x <= data.sectionX1(); x++) {
			for (int y = data.sectionY0(); y <= data.sectionY1(); y++) {
				for (int z = data.sectionZ0(); z <= data.sectionZ1(); z++) {
					SectionCache section = entityCache.get(x, y, z);
					Iterable<Entity> pool = sectionIntersects(searchBox, x, y, z)
							? section.allEntities()
							: section.marginEntities();
					for (Entity candidate : pool) {
						AABB boundingBox = candidate.getBoundingBox();
						Vec3 deltaMovement = candidate.getDeltaMovement();
						AABB sweepBox = boundingBox.expandTowards(deltaMovement);
						if (!searchBox.intersects(sweepBox) || !projectile.canHitEntity(candidate)) {
							continue;
						}
						AABB hitBox = projectile.alterHitBox(candidate, boundingBox, radius, 0);
						AABB grazeBox = graze > 0
								? projectile.alterHitBox(candidate, boundingBox, radius, graze)
								: null;
						data.addCandidate(candidate, deltaMovement, hitBox, grazeBox);
					}
				}
			}
		}
	}

	private static void computeStep3(List<SimplifiedProjectile> allDanmakus) {
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
			Vec3 hitPos = checkHitCached(candidate.hitBox(), src, effectiveDst, candidate.deltaMovement());
			if (hitPos != null) {
				double distance = src.distanceToSqr(hitPos);
				if (distance < closestDistance) {
					closestDistance = distance;
					hitEntity = target;
				}
				continue;
			}
			if (data.graze() > 0 && target instanceof Player player && candidate.grazeBox() != null) {
				if (checkHitCached(candidate.grazeBox(), src, effectiveDst, candidate.deltaMovement()) != null
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
	                                       @Nullable LivingEntity self) {
		List<SimplifiedProjectile> toRemove = new ArrayList<>();
		for (SimplifiedProjectile projectile : allDanmakus) {
			if (shouldSkipVirtualTick(projectile)) {
				continue;
			}
			DanmakuVirtualTickData data = projectile.virtualTickData();
			if (data.usesStandardSequentialFallback()) {
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
		}
		if (!removeFlag[0]) {
			applyResolvedMoves(allDanmakus);
			finalizeResolvedMoves(sl, allDanmakus, toRemove);
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

	private static void applyResolvedMoves(List<SimplifiedProjectile> allDanmakus) {
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

	private static boolean sectionIntersects(AABB box, int x, int y, int z) {
		double minX = x << 4;
		double minY = y << 4;
		double minZ = z << 4;
		return box.maxX > minX && box.minX < minX + 16 &&
				box.maxY > minY && box.minY < minY + 16 &&
				box.maxZ > minZ && box.minZ < minZ + 16;
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

	private static void runParallel(int size, RangeTask task) {
		int threads = Math.min(MAX_THREADS, Runtime.getRuntime().availableProcessors());
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

	/**
	 * Thread-safe version of {@link ProjectileHitHelper#checkHit(Entity, AABB, Vec3, Vec3)}
	 * that uses pre-cached deltaMovement instead of reading from the entity.
	 */
	@Nullable
	private static Vec3 checkHitCached(AABB base, Vec3 src, Vec3 dst, Vec3 targetVel) {
		double speed = targetVel.length();
		int n = (int) Math.min(8, Math.floor(speed / 0.5));
		for (int i = 0; i <= n; i++) {
			AABB aabb = n == 0 ? base : base.move(targetVel.scale(1d * i / n));
			Optional<Vec3> optional = aabb.contains(src) ? Optional.of(src) : aabb.clip(src, dst);
			if (optional.isPresent()) {
				return optional.get();
			}
		}
		return null;
	}

}
