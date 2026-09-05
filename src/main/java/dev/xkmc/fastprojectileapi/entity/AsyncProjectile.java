package dev.xkmc.fastprojectileapi.entity;

import dev.xkmc.fastprojectileapi.collision.EntityCacheAccess;
import dev.xkmc.fastprojectileapi.collision.EntityStorageCache;
import dev.xkmc.fastprojectileapi.collision.IEntityCache;
import dev.xkmc.fastprojectileapi.collision.IEntityIterator;
import dev.xkmc.fastprojectileapi.collision.UserMatrixCache;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import dev.xkmc.youkaishomecoming.content.spell.mover.MoverInfo;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.CardHolder;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public abstract class AsyncProjectile extends SimplifiedProjectile {

	protected final TickData tickData = new TickData();

	public TickData tickData() {
		return tickData;
	}

	protected AsyncProjectile(EntityType<?> pEntityType, Level pLevel) {
		super(pEntityType, pLevel);
	}

	@Override
	public void tick() {
		prepareParallelTick();
		tickAfterParallelPreparation();
	}

	/**
	 * Captures live state on the game thread before movement may run in parallel.
	 */
	public final void prepareParallelTick() {
		prepareMoveState();
	}

	protected void prepareMoveState() {
	}

	/** Runs one projectile tick after {@link #prepareParallelTick()} has completed. */
	public final void tickAfterParallelPreparation() {
		TickData data = tickData;
		data.reset();
		beginTick(data);
		if (data.stopTick) return;
		planMove(data);
		if (data.stopTick) return;
		trimMove(data);
		if (data.stopTick) return;
		collectCollisionInput(data, getEntityIterator());
		if (data.stopTick) return;
		applyMoveTick(data);
		if (data.stopTick) return;
		resolveCollision(data);
		if (data.stopTick) return;
		finishTick(data);
	}

	protected void beginTick(TickData data) {
		beginTick(data, null);
	}

	protected void beginTick(TickData data, MoverInfo.OwnerInfo sharedOwnerInfo) {
		super.tick();
		data.ownerInfo = sharedOwnerInfo != null ? sharedOwnerInfo : snapshotOwnerInfo(getOwner());
	}

	protected MoverInfo.OwnerInfo snapshotOwnerInfo(@Nullable Entity owner) {
		if (owner instanceof CardHolder holder) {
			return new MoverInfo.OwnerInfo(holder.center(), holder.forward());
		}
		if (owner instanceof LivingEntity living) {
			Vec3 pos = living.position().add(0, living.getBbHeight() / 2, 0);
			Vec3 forward = living.getForward();
			return new MoverInfo.OwnerInfo(pos, forward);
		}
		return new MoverInfo.OwnerInfo(null, null);
	}

	/**
	 * Async stage. Only compute from TickData snapshots captured during beginTick.
	 * Do not read live world/entity state or perform side effects here.
	 */
	protected void planMove(TickData data) {
	}

	/**
	 * Async stage. Only refine the precomputed move using already-safe inputs.
	 * Do not read live world/entity state or perform side effects here.
	 */
	protected void trimMove(TickData data) {
	}

	/**
	 * Async stage. Only mark preheat ranges for later main-thread cache creation.
	 * Do not query world/entity live state here.
	 */
	protected void planPreheatRange(TickData data, UserMatrixCache cache) {
	}

	protected IEntityIterator getEntityIterator() {
		if (level() instanceof net.minecraft.server.level.ServerLevel sl) {
			IEntityCache cache = getOwner() instanceof EntityCachingUser user ? user.entityCache().get(sl, user.self()) : EntityStorageCache.get(sl);
			if (getOwner() instanceof LivingEntity owner) {
				return new EntityCacheAccess(cache, owner)::foreach;
			}
		}
		return (aabb, type) -> java.util.List.of();
	}

	/**
	 * Async stage. Consume only precomputed movement data and preheated entity snapshots.
	 * Do not access live world/entity state or trigger gameplay side effects here.
	 */
	protected void collectCollisionInput(TickData data, IEntityIterator iterator) {
	}

	protected void resolveCollision(TickData data) {
	}

	protected void applyMoveTick(TickData data) {
	}

	protected void finishTick(TickData data) {
		for (Player player : data.grazed) {
			doGraze(player);
		}
	}

	@Override
	public void doGraze(Player player) {
	}

	public static class TickData {

		@Nullable
		public UserMatrixCache preheatCache;
		@Nullable
		public Vec3 moveSrc;
		@Nullable
		public Vec3 inputVelocity;
		@Nullable
		public ProjectileMovement plannedMovement;
		@Nullable
		public Vec3 plannedMovementVec;
		@Nullable
		public Vec3 untrimmedMoveDst;
		@Nullable
		public Vec3 moveDst;
		@Nullable
		public BlockHitResult blockHit;
		@Nullable
		public MoverInfo.OwnerInfo ownerInfo;
		public final List<Entity> hitEntities = new ArrayList<>();
		public final List<Player> grazed = new ArrayList<>();
		public int candidateCount;
		public int grazeCount;
		public boolean removed;
		public boolean stopTick;

		public void reset() {
			preheatCache = null;
			moveSrc = null;
			inputVelocity = null;
			plannedMovement = null;
			plannedMovementVec = null;
			untrimmedMoveDst = null;
			moveDst = null;
			blockHit = null;
			ownerInfo = null;
			hitEntities.clear();
			grazed.clear();
			candidateCount = 0;
			grazeCount = 0;
			removed = false;
			stopTick = false;
		}

		/**
		 * The destination the projectile would have reached without collision trimming.
		 * {@code untrimmedMoveDst} is set by {@code planMove} and never overwritten by
		 * {@code trimMove} (which only modifies {@code moveDst}), so hit actions that
		 * CONTINUE past a face can advance to the original planned endpoint.
		 */
		public Vec3 movementEndOr(Vec3 fallback) {
			if (untrimmedMoveDst != null) return untrimmedMoveDst;
			if (moveDst != null) return moveDst;
			return fallback;
		}

		/**
		 * The untrimmed planned movement vector for this tick. {@code plannedMovementVec}
		 * is set by {@code planMove} and never overwritten by {@code trimMove} (which only
		 * modifies {@code plannedMovement}), so hit actions that need the pre-collision
		 * velocity can recover it even after {@code applyMove} has overwritten
		 * {@code getDeltaMovement()} with the trimmed step.
		 */
		public Vec3 incomingMovementOr(Vec3 fallback) {
			if (plannedMovementVec != null) return plannedMovementVec;
			if (inputVelocity != null) return inputVelocity;
			return fallback;
		}

	}

}
