package dev.xkmc.fastprojectileapi.entity;

import dev.xkmc.fastprojectileapi.collision.EntityStorageCache;
import dev.xkmc.fastprojectileapi.collision.EntityStorageHelper;
import dev.xkmc.fastprojectileapi.collision.IEntityCache;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;

/**
 * Parallel danmaku tick utility implementing the PG optimization (4-step split).
 * <p>
 * Architecture:
 * <ul>
 *   <li>Step 1 (parallel): compute collision range from the current move vector</li>
 *   <li>Step 2 (single-thread): advance tick state, block clip, and cache entity collision data</li>
 *   <li>Step 3 (parallel): entity collision detection using cached snapshots</li>
 *   <li>Step 4 (single-thread): graze callbacks, onHit callbacks, and list maintenance</li>
 * </ul>
 */
public class ParallelDanmakuTicker {

	private static final int PARALLEL_THRESHOLD = 500;
	private static final int MAX_THREADS = 4;

	/**
	 * Immutable snapshot of a target entity's collision-relevant state.
	 * Captured in Step 2 and read in Step 3 without touching live entity state.
	 */
	public record CachedTarget(Entity entity, AABB boundingBox, Vec3 deltaMovement) {
	}

	/** Step 1 output: computed movement and the collision search range. */
	private record Step1Result(
			Vec3 src,
			Vec3 dst,
			AABB searchBox,
			float radius,
			float graze,
			boolean checkBlock
	) {
	}

	/** Step 2 output: resolved hit range and cached collision candidates. */
	private record Step2Result(
			@Nullable HitResult blockHit,
			Vec3 effectiveDst,
			List<CachedTarget> candidates
	) {
	}

	/** Step 3 output: collision result and graze callbacks to apply on the main thread. */
	private record Step3Result(@Nullable Entity hitEntity, List<Player> grazedPlayers) {
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
		long gameTime = warmCaches(sl, cacheHolder);
		List<SimplifiedProjectile> active = collectActiveDanmakus(allDanmakus);
		if (active.isEmpty()) {
			return;
		}

		if (active.size() >= PARALLEL_THRESHOLD) {
			tickParallel(sl, active, allDanmakus, temp, toBeSent, removeFlag, cacheHolder, self, gameTime);
		} else {
			tickSequential(sl, active, allDanmakus, temp, toBeSent, removeFlag, self);
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

	private static List<SimplifiedProjectile> collectActiveDanmakus(List<SimplifiedProjectile> allDanmakus) {
		List<SimplifiedProjectile> active = new ArrayList<>(allDanmakus.size());
		for (var e : allDanmakus) {
			if (e.isAddedToWorld() && !e.isRemoved()) {
				continue;
			}
			active.add(e);
		}
		return active;
	}

	private static void tickSequential(ServerLevel sl,
	                                   List<SimplifiedProjectile> active,
	                                   List<SimplifiedProjectile> allDanmakus,
	                                   ArrayList<SimplifiedProjectile> temp,
	                                   ArrayList<SimplifiedProjectile> toBeSent,
	                                   boolean[] removeFlag,
	                                   @Nullable LivingEntity self) {
		List<SimplifiedProjectile> toRemove = new ArrayList<>();
		for (var e : active) {
			if (e.isRemoved()) {
				toRemove.add(e);
				continue;
			}
			if (e.isValid()) {
				e.setOldPosAndRot();
				++e.tickCount;
				e.tick();
			}
			if (removeFlag[0]) {
				break;
			}
			if (!e.isValid()) {
				toRemove.add(e);
			}
		}
		finalizeTick(allDanmakus, temp, toBeSent, removeFlag, self, toRemove);
	}

	private static void tickParallel(ServerLevel sl,
	                                 List<SimplifiedProjectile> active,
	                                 List<SimplifiedProjectile> allDanmakus,
	                                 ArrayList<SimplifiedProjectile> temp,
	                                 ArrayList<SimplifiedProjectile> toBeSent,
	                                 boolean[] removeFlag,
	                                 @Nullable UserCacheHolder cacheHolder,
	                                 @Nullable LivingEntity self,
	                                 long gameTime) {
		int size = active.size();
		Step1Result[] step1 = computeStep1(active);
		if (step1 == null) {
			tickSequential(sl, active, allDanmakus, temp, toBeSent, removeFlag, self);
			return;
		}

		IEntityCache entityCache = resolveEntityCache(sl, cacheHolder, self, gameTime);
		Step2Result[] step2 = computeStep2(sl, active, step1, entityCache);
		Step3Result[] step3 = computeStep3(active, size, step1, step2);
		finishParallelTick(sl, active, allDanmakus, temp, toBeSent, removeFlag, self, step1, step2, step3);
	}

	@Nullable
	private static Step1Result[] computeStep1(List<SimplifiedProjectile> active) {
		int size = active.size();
		Step1Result[] results = new Step1Result[size];
		try {
			runParallel(size, (from, to) -> {
				for (int i = from; i < to; i++) {
					results[i] = computeStep1Result(active.get(i));
				}
			});
			return results;
		} catch (Exception ex) {
			com.mojang.logging.LogUtils.getLogger().warn("Parallel danmaku tick Step 1 failed, falling back", ex);
			return null;
		}
	}

	@Nullable
	private static Step1Result computeStep1Result(SimplifiedProjectile projectile) {
		if (!(projectile instanceof BaseProjectile bp)) {
			return null;
		}
		Vec3 movement = projectile.getDeltaMovement();
		Vec3 src = projectile.position();
		Vec3 dst = src.add(movement);
		float radius = projectile.getBbWidth() / 2f;
		float graze = projectile.grazeRange();
		AABB box = projectile.getBoundingBox().expandTowards(movement);
		AABB searchBox = box.inflate(1 + radius + graze);
		return new Step1Result(
				src,
				dst,
				searchBox,
				radius,
				graze,
				bp.checkBlockHit()
		);
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

	private static Step2Result[] computeStep2(ServerLevel sl,
	                                          List<SimplifiedProjectile> active,
	                                          Step1Result[] step1,
	                                          @Nullable IEntityCache entityCache) {
		int size = active.size();
		Step2Result[] results = new Step2Result[size];
		for (int i = 0; i < size; i++) {
			results[i] = computeStep2Result(sl, active.get(i), step1[i], entityCache);
		}
		return results;
	}

	@Nullable
	private static Step2Result computeStep2Result(ServerLevel sl,
	                                              SimplifiedProjectile projectile,
	                                              @Nullable Step1Result data,
	                                              @Nullable IEntityCache entityCache) {
		if (projectile.isRemoved() || !projectile.isValid()) {
			return null;
		}
		if (data == null) {
			sequentialTickOne(projectile);
			return null;
		}

		prepareTickState(projectile);

		Vec3 effectiveDst = data.dst;
		HitResult blockHit = resolveBlockHit(sl, projectile, data);
		if (blockHit != null) {
			effectiveDst = blockHit.getLocation();
		}

		List<CachedTarget> candidates = entityCache == null
				? List.of()
				: snapshotCandidates(entityCache, data.searchBox, projectile);
		return new Step2Result(blockHit, effectiveDst, candidates);
	}

	private static void sequentialTickOne(SimplifiedProjectile projectile) {
		projectile.setOldPosAndRot();
		++projectile.tickCount;
		projectile.tick();
	}

	private static void prepareTickState(SimplifiedProjectile projectile) {
		projectile.setOldPosAndRot();
		++projectile.tickCount;
		projectile.baseTick();
	}

	private static boolean shouldEraseAfterMove(ServerLevel sl, SimplifiedProjectile projectile) {
		if (!projectile.level().hasChunk(projectile.blockPosition().getX() >> 4, projectile.blockPosition().getZ() >> 4)) {
			return true;
		}
		return projectile.isAddedToWorld() && !EntityStorageHelper.isTicking(sl, projectile);
	}

	@Nullable
	private static HitResult resolveBlockHit(ServerLevel sl, SimplifiedProjectile projectile, Step1Result data) {
		if (!data.checkBlock) {
			return null;
		}
		HitResult blockHit = sl.clip(new ClipContext(
				data.src,
				data.dst,
				ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE,
				projectile
		));
		return blockHit.getType() == HitResult.Type.MISS ? null : blockHit;
	}

	private static List<CachedTarget> snapshotCandidates(IEntityCache entityCache,
	                                                     AABB searchBox,
	                                                     SimplifiedProjectile projectile) {
		List<Entity> raw = entityCache.foreach(searchBox, projectile::canHitEntity);
		if (raw.isEmpty()) {
			return List.of();
		}
		List<CachedTarget> snapshots = new ArrayList<>(raw.size());
		for (Entity target : raw) {
			snapshots.add(new CachedTarget(target, target.getBoundingBox(), target.getDeltaMovement()));
		}
		return snapshots;
	}

	private static Step3Result[] computeStep3(List<SimplifiedProjectile> active,
	                                          int size,
	                                          Step1Result[] step1,
	                                          Step2Result[] step2) {
		Step3Result[] results = new Step3Result[size];
		try {
			runParallel(size, (from, to) -> {
				for (int i = from; i < to; i++) {
					results[i] = computeStep3Result(active.get(i), step1[i], step2[i]);
				}
			});
		} catch (Exception ex) {
			com.mojang.logging.LogUtils.getLogger().warn("Parallel danmaku tick Step 3 failed, skipping collision", ex);
			for (int i = 0; i < size; i++) {
				results[i] = new Step3Result(null, List.of());
			}
		}
		return results;
	}

	private static Step3Result computeStep3Result(SimplifiedProjectile projectile,
	                                              @Nullable Step1Result step1,
	                                              @Nullable Step2Result step2) {
		if (step1 == null || step2 == null || step2.candidates.isEmpty()) {
			return new Step3Result(null, List.of());
		}

		double closestDistance = Double.MAX_VALUE;
		Entity hitEntity = null;
		List<Player> grazedPlayers = step1.graze > 0 ? new ArrayList<>() : List.of();
		for (CachedTarget candidate : step2.candidates) {
			Entity target = candidate.entity();
			AABB hitBox = projectile.alterHitBox(target, step1.radius, 0);
			Vec3 hitPos = checkHitCached(hitBox, step1.src, step2.effectiveDst, candidate.deltaMovement());
			if (hitPos != null) {
				double distance = step1.src.distanceToSqr(hitPos);
				if (distance < closestDistance) {
					closestDistance = distance;
					hitEntity = target;
				}
				continue;
			}
			if (step1.graze > 0 && target instanceof Player player) {
				AABB grazeBox = projectile.alterHitBox(target, step1.radius, step1.graze);
				if (checkHitCached(grazeBox, step1.src, step2.effectiveDst, candidate.deltaMovement()) != null
						&& !grazedPlayers.contains(player)) {
					grazedPlayers.add(player);
				}
			}
		}
		return new Step3Result(hitEntity, grazedPlayers);
	}

	private static void finishParallelTick(ServerLevel sl,
	                                       List<SimplifiedProjectile> active,
	                                       List<SimplifiedProjectile> allDanmakus,
	                                       ArrayList<SimplifiedProjectile> temp,
	                                       ArrayList<SimplifiedProjectile> toBeSent,
	                                       boolean[] removeFlag,
	                                       @Nullable LivingEntity self,
	                                       Step1Result[] step1,
	                                       Step2Result[] step2,
	                                       Step3Result[] step3) {
		List<SimplifiedProjectile> toRemove = new ArrayList<>();
		for (int i = 0; i < active.size(); i++) {
			if (removeFlag[0]) {
				break;
			}

			SimplifiedProjectile projectile = active.get(i);
			Step1Result d1 = step1[i];
			Step2Result d2 = step2[i];
			Step3Result d3 = step3[i];

			if (d2 == null) {
				if (shouldRemoveFromVirtualList(projectile)) {
					toRemove.add(projectile);
				}
				continue;
			}

			for (Player player : d3.grazedPlayers) {
				projectile.doGraze(player);
			}

			HitResult finalHit = d2.blockHit;
			if (d3.hitEntity != null) {
				finalHit = new EntityHitResult(d3.hitEntity);
			}
			if (finalHit != null && projectile instanceof BaseProjectile bp) {
				bp.onHit(finalHit);
			}
			if (removeFlag[0]) {
				break;
			}

			if (projectile instanceof BaseProjectile bp) {
				finishMovementStep(sl, projectile, bp);
			}

			if (shouldRemoveFromVirtualList(projectile)) {
				toRemove.add(projectile);
			}
		}
		finalizeTick(allDanmakus, temp, toBeSent, removeFlag, self, toRemove);
	}

	private static void finishMovementStep(ServerLevel sl,
	                                       SimplifiedProjectile projectile,
	                                       BaseProjectile bp) {
		ProjectileMovement movement = bp.computeMove();
		bp.applyMove(movement);
		if (projectile.tickCount >= bp.lifetime() && sl == projectile.level()) {
			bp.terminate();
			projectile.markErased(false);
			return;
		}
		if (shouldEraseAfterMove(sl, projectile)) {
			projectile.markErased(false);
		}
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

	/**
	 * Thread-safe version of {@link dev.xkmc.fastprojectileapi.collision.ProjectileHitHelper#checkHit(Entity, AABB, Vec3, Vec3)}
	 * that uses pre-cached deltaMovement instead of reading from the entity.
	 */
	@Nullable
	private static Vec3 checkHitCached(AABB base, Vec3 src, Vec3 dst, Vec3 targetVel) {
		double speed = targetVel.length();
		int n = (int) Math.min(8, Math.floor(speed / 0.5));
		for (int i = 0; i <= n; i++) {
			AABB aabb = n == 0 ? base : base.move(targetVel.scale(1d * i / n));
			var optional = aabb.contains(src) ? java.util.Optional.of(src) : aabb.clip(src, dst);
			if (optional.isPresent()) {
				return optional.get();
			}
		}
		return null;
	}

}
