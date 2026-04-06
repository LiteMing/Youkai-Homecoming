package dev.xkmc.fastprojectileapi.entity;

import dev.xkmc.fastprojectileapi.collision.EntityStorageCache;
import dev.xkmc.fastprojectileapi.collision.IEntityCache;
import dev.xkmc.fastprojectileapi.collision.UserCacheHolder;
import dev.xkmc.fastprojectileapi.render.virtual.DanmakuManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
 * Designed to replace the identical tickDanmaku() logic in YoukaiEntity and DanmakuProxyEntity.
 * <p>
 * Architecture:
 * <ul>
 *   <li>Step 1 (parallel): computeMove() — pure math per danmaku</li>
 *   <li>Step 2 (single-thread): applyMove + block collision (level.clip) + cache entity collision data</li>
 *   <li>Step 3 (parallel): entity collision detection using cached data (thread-safe)</li>
 *   <li>Step 4 (single-thread): onHit callbacks, list maintenance, network sync</li>
 * </ul>
 */
public class ParallelDanmakuTicker {

	private static final int PARALLEL_THRESHOLD = 500;
	private static final int MAX_THREADS = 4;

	// ==================== Cached collision data (thread-safe) ====================

	/**
	 * Immutable snapshot of a target entity's collision-relevant state.
	 * Captured once in Step 2 (single-thread), used in Step 3 (parallel).
	 */
	public record CachedTarget(Entity entity, AABB boundingBox, Vec3 deltaMovement) {
	}

	// ==================== Per-danmaku data arrays ====================

	/** Step 1 output: computed movement per danmaku */
	private record Step1Result(ProjectileMovement movement, Vec3 src, Vec3 dst,
	                           AABB searchBox, float radius, float graze,
	                           boolean checkBlock, boolean reachedLifetime) {}

	/** Step 2 output: cached collision data per danmaku */
	private record Step2Result(HitResult blockHit, Vec3 effectiveDst,
	                           List<CachedTarget> candidates) {}

	/** Step 3 output: collision result per danmaku */
	private record Step3Result(@Nullable Entity hitEntity) {}

	// ==================== Main entry point ====================

	/**
	 * Tick all virtual danmaku with parallel optimization.
	 *
	 * @param sl          server level
	 * @param allDanmakus list of all virtual danmaku (modified: expired removed)
	 * @param temp        temp list for newly spawned danmaku (set by caller, populated by shoot())
	 * @param toBeSent    list of danmaku to send to clients this tick
	 * @param removeFlag  shared flag: set to true by onHit side effects (eraseAllDanmaku)
	 * @param cacheHolder entity cache holder (for PC optimization)
	 * @param self        the entity that owns the danmaku (YoukaiEntity or DanmakuProxyEntity)
	 */
	public static void tickAll(ServerLevel sl,
	                           List<SimplifiedProjectile> allDanmakus,
	                           ArrayList<SimplifiedProjectile> temp,
	                           ArrayList<SimplifiedProjectile> toBeSent,
	                           boolean[] removeFlag,
	                           @Nullable UserCacheHolder cacheHolder,
	                           @Nullable LivingEntity self) {

		// PC: cache gameTime once per tick
		long gameTime = sl.getGameTime();
		if (cacheHolder != null) {
			cacheHolder.setGameTime(gameTime);
		}
		// Also warm EntityStorageCache for the fallback path
		EntityStorageCache.get(sl, gameTime);

		// Collect valid virtual danmaku (not added to world)
		List<SimplifiedProjectile> active = new ArrayList<>();
		for (var e : allDanmakus) {
			if (e.isAddedToWorld() && !e.isRemoved()) continue;
			active.add(e);
		}

		int size = active.size();

		if (size >= PARALLEL_THRESHOLD) {
			tickParallel(sl, active, size, allDanmakus, temp, toBeSent, removeFlag,
					cacheHolder, self, gameTime);
		} else {
			tickSequential(sl, active, allDanmakus, temp, toBeSent, removeFlag, self);
		}
	}

	// ==================== Sequential fallback ====================

	private static void tickSequential(ServerLevel sl,
	                                   List<SimplifiedProjectile> active,
	                                   List<SimplifiedProjectile> allDanmakus,
	                                   ArrayList<SimplifiedProjectile> temp,
	                                   ArrayList<SimplifiedProjectile> toBeSent,
	                                   boolean[] removeFlag,
	                                   @Nullable LivingEntity self) {
		List<SimplifiedProjectile> toRemove = new ArrayList<>();
		for (var e : active) {
			if (e.isValid()) {
				e.setOldPosAndRot();
				++e.tickCount;
				e.tick();
			}
			if (removeFlag[0]) break;
			if (!e.isValid()) {
				toRemove.add(e);
			}
		}
		if (!toRemove.isEmpty()) allDanmakus.removeAll(toRemove);
		if (!removeFlag[0]) {
			allDanmakus.addAll(temp);
			DanmakuManager.send(self, toBeSent);
		}
	}

	// ==================== Parallel 4-step tick ====================

	private static void tickParallel(ServerLevel sl,
	                                  List<SimplifiedProjectile> active, int size,
	                                  List<SimplifiedProjectile> allDanmakus,
	                                  ArrayList<SimplifiedProjectile> temp,
	                                  ArrayList<SimplifiedProjectile> toBeSent,
	                                  boolean[] removeFlag,
	                                  @Nullable UserCacheHolder cacheHolder,
	                                  @Nullable LivingEntity self,
	                                  long gameTime) {

		Step1Result[] s1 = new Step1Result[size];

		// ---- Step 1 (parallel): computeMove + collision range ----
		try {
			int threads = Math.min(MAX_THREADS, Runtime.getRuntime().availableProcessors());
			if (threads <= 1) threads = 2;
			int chunkSize = (size + threads - 1) / threads;
			ForkJoinTask<?>[] tasks = new ForkJoinTask<?>[threads];
			for (int t = 0; t < threads; t++) {
				int from = t * chunkSize;
				int to = Math.min(from + chunkSize, size);
				if (from >= to) continue;
				tasks[t] = ForkJoinPool.commonPool().submit(() -> {
					for (int i = from; i < to; i++) {
						var e = active.get(i);
						if (!(e instanceof BaseProjectile bp)) {
							s1[i] = null;
							continue;
						}
						var movement = bp.computeMove();
						Vec3 src = e.position();
						Vec3 dst = src.add(movement.vec());
						float radius = e.getBbWidth() / 2f;
						float graze = e.grazeRange();
						AABB box = e.getBoundingBox().expandTowards(movement.vec());
						AABB searchBox = box.inflate(1 + radius + graze);
						s1[i] = new Step1Result(movement, src, dst, searchBox,
								radius, graze, bp.checkBlockHit(), e.tickCount >= bp.lifetime());
					}
				});
			}
			for (var task : tasks) {
				if (task != null) task.join();
			}
		} catch (Exception ex) {
			com.mojang.logging.LogUtils.getLogger().warn("Parallel danmaku tick Step 1 failed, falling back", ex);
			tickSequential(sl, active, allDanmakus, temp, toBeSent, removeFlag, self);
			return;
		}

		Step2Result[] s2 = new Step2Result[size];

		// ---- Step 2 (single-thread): applyMove + block clip + cache entity data ----
		IEntityCache entityCache = null;
		if (cacheHolder != null && self != null) {
			entityCache = cacheHolder.get(sl, self);
		}

		for (int i = 0; i < size; i++) {
			var e = active.get(i);
			var d = s1[i];
			if (d == null) {
				// Non-BaseProjectile: fall back to sequential tick
				e.setOldPosAndRot();
				++e.tickCount;
				e.tick();
				s2[i] = null;
				continue;
			}

			// Apply movement (must be single-thread); skip if already past lifetime
			if (e instanceof BaseProjectile bp && !d.reachedLifetime) {
				e.setOldPosAndRot();
				++e.tickCount;
				bp.applyMove(d.movement);
				// Minimal baseTick: erase if fallen below world
				if (e.getY() < sl.getMinBuildHeight() - 64) {
					e.markErased(false);
				}
			}

			// Block collision (needs Level access); skip if past lifetime
			Vec3 effectiveDst = d.dst;
			HitResult blockHit = null;
			if (d.checkBlock && !d.reachedLifetime) {
				blockHit = sl.clip(new ClipContext(d.src, d.dst,
						ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
				if (blockHit.getType() != HitResult.Type.MISS) {
					effectiveDst = blockHit.getLocation();
				}
			}

			// Entity collision data caching (already guards !reachedLifetime)
			List<CachedTarget> candidates = List.of();
			if (entityCache != null && !d.reachedLifetime) {
				List<Entity> raw = entityCache.foreach(d.searchBox, e::canHitEntity);
				if (!raw.isEmpty()) {
					candidates = new ArrayList<>(raw.size());
					for (Entity x : raw) {
						candidates.add(new CachedTarget(x, x.getBoundingBox(), x.getDeltaMovement()));
					}
				}
			}

			s2[i] = new Step2Result(blockHit, effectiveDst, candidates);
		}

		Step3Result[] s3 = new Step3Result[size];

		// ---- Step 3 (parallel): entity collision detection using cached data ----
		try {
			int threads = Math.min(MAX_THREADS, Runtime.getRuntime().availableProcessors());
			if (threads <= 1) threads = 2;
			int chunkSize = (size + threads - 1) / threads;
			ForkJoinTask<?>[] tasks = new ForkJoinTask<?>[threads];
			for (int t = 0; t < threads; t++) {
				int from = t * chunkSize;
				int to = Math.min(from + chunkSize, size);
				if (from >= to) continue;
				tasks[t] = ForkJoinPool.commonPool().submit(() -> {
					for (int i = from; i < to; i++) {
						var d1 = s1[i];
						var d2 = s2[i];
						if (d1 == null || d2 == null || d1.reachedLifetime || d2.candidates.isEmpty()) {
							s3[i] = new Step3Result(null);
							continue;
						}
						double d0 = Double.MAX_VALUE;
						Entity hitEntity = null;
						for (var ct : d2.candidates) {
							AABB base = ct.boundingBox().inflate(d1.radius);
							Vec3 hpos = checkHitCached(base, d1.src, d2.effectiveDst, ct.deltaMovement());
							if (hpos != null) {
								double d1sq = d1.src.distanceToSqr(hpos);
								if (d1sq < d0) {
									hitEntity = ct.entity();
									d0 = d1sq;
								}
							}
						}
						s3[i] = new Step3Result(hitEntity);
					}
				});
			}
			for (var task : tasks) {
				if (task != null) task.join();
			}
		} catch (Exception ex) {
			com.mojang.logging.LogUtils.getLogger().warn("Parallel danmaku tick Step 3 failed, skipping collision", ex);
			for (int i = 0; i < size; i++) {
				s3[i] = new Step3Result(null);
			}
		}

		// ---- Step 4 (single-thread): onHit callbacks + list maintenance ----
		List<SimplifiedProjectile> toRemove = new ArrayList<>();
		for (int i = 0; i < size; i++) {
			if (removeFlag[0]) break;
			var e = active.get(i);
			var d1 = s1[i];
			var d2 = s2[i];
			var d3 = s3[i];

			// Non-BaseProjectile entries were already ticked in Step 2
			if (d2 == null) {
				if (!e.isValid()) toRemove.add(e);
				continue;
			}

			// Determine hit result
			HitResult finalHit = d2.blockHit;
			if (d3 != null && d3.hitEntity != null) {
				finalHit = new EntityHitResult(d3.hitEntity);
			}

			// onHit (may trigger eraseAllDanmaku → sets removeFlag via the caller's flag field)
			if (finalHit != null && e instanceof BaseProjectile bp) {
				bp.onHit(finalHit);
			}
			// Break immediately if eraseAllDanmaku was called from within onHit
			if (removeFlag[0]) break;

			// Check lifetime expiry on the transition tick (tickCount just incremented to reach lifetime)
			if (!d1.reachedLifetime && e instanceof BaseProjectile bp
					&& e.tickCount >= bp.lifetime() && sl == e.level()) {
				bp.terminate();
				e.markErased(false);
			}

			if (!e.isValid()) toRemove.add(e);
		}
		if (!toRemove.isEmpty()) {
			allDanmakus.removeAll(new java.util.HashSet<>(toRemove));
		}

		if (!removeFlag[0]) {
			allDanmakus.addAll(temp);
			DanmakuManager.send(self, toBeSent);
		}
	}

	// ==================== Cached collision check ====================

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
			var optional = aabb.contains(src) ? java.util.Optional.of(src) : aabb.clip(src, dst);
			if (optional.isPresent())
				return optional.get();
		}
		return null;
	}

}
