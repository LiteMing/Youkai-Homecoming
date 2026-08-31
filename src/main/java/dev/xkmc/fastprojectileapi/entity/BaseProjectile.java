package dev.xkmc.fastprojectileapi.entity;

import dev.xkmc.fastprojectileapi.collision.EntityStorageHelper;
import dev.xkmc.fastprojectileapi.collision.IEntityIterator;
import dev.xkmc.fastprojectileapi.collision.ProjectileHitHelper;
import dev.xkmc.fastprojectileapi.collision.UserMatrixCache;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public abstract class BaseProjectile extends AsyncProjectile {

	protected BaseProjectile(EntityType<? extends BaseProjectile> pEntityType, Level pLevel) {
		super(pEntityType, pLevel);
	}

	@Override
	protected void defineSynchedData() {

	}

	public abstract boolean checkBlockHit();

	public abstract int lifetime();

	@Override
	protected void planMove(TickData data) {
		data.moveSrc = position();
		data.inputVelocity = getDeltaMovement();
		data.plannedMovement = computeMove(data.inputVelocity, data.moveSrc);
		data.moveDst = data.moveSrc.add(data.plannedMovement.vec());
	}

	@Override
	protected void trimMove(TickData data) {
		data.blockHit = null;
		if (!checkBlockHit() || !(level() instanceof ServerLevel)) return;
		Vec3 src = data.moveSrc == null ? position() : data.moveSrc;
		Vec3 dst = data.moveDst == null ? src.add(getDeltaMovement()) : data.moveDst;
		var hit = ProjectileHitHelper.getBlockHitResultOnMoveVector(this, src, dst);
		if (hit != null) {
			data.blockHit = hit;
			data.moveDst = hit.getLocation();
			if (data.plannedMovement != null) {
				// Trim the applied step to the collision point
				Vec3 step = hit.getLocation().subtract(src);
				data.plannedMovement = new ProjectileMovement(step, data.plannedMovement.rot());
			}
		}
	}

	@Override
	protected void collectCollisionInput(TickData data, IEntityIterator iterator) {
		Vec3 src = data.moveSrc == null ? position() : data.moveSrc;
		Vec3 dst = data.moveDst == null ? src.add(getDeltaMovement()) : data.moveDst;
		ProjectileHitHelper.collectEntityHitOnMoveVector(this, src, dst, data.hitEntities, iterator);
	}

	@Override
	protected void planPreheatRange(TickData data, UserMatrixCache cache) {
		if (cache == null) return;
		Vec3 src = data.moveSrc == null ? position() : data.moveSrc;
		Vec3 dst = data.moveDst == null ? src.add(getDeltaMovement()) : data.moveDst;
		var radius = getBbWidth() / 2f;
		var graze = grazeRange();
		var box = getBoundingBox().move(src.subtract(position())).expandTowards(dst.subtract(src));
		cache.preheat(box.inflate(1 + radius + graze));
	}

	@Override
	protected void resolveCollision(TickData data) {
		if (data.blockHit != null) {
			onHitBlock(data.blockHit);
		}
		if (!data.hitEntities.isEmpty()) {
			for (Entity entity : data.hitEntities) {
				onHitEntity(new EntityHitResult(entity));
				if (!isValid() || isRemoved() || data.removed) {
					break;
				}
			}
		}
	}

	@Override
	protected void applyMoveTick(TickData data) {
		// applyMove is performed before resolveCollision in ParallelTicker, but for single-thread fallback:
		if (data.plannedMovement != null) {
			applyMove(data.plannedMovement);
		}
	}

	@Override
	protected void finishTick(TickData data) {
		super.finishTick(data);
		commitPreMoveEffects(data);
		if (tickCount >= lifetime()) {
			if (level() instanceof ServerLevel) {
				terminate();
				data.removed = true;
				markErased(false);
				return;
			}
			var owner = getOwner();
			if (tickCount >= lifetime() + 10 || owner == null || !owner.isAlive()) {
				data.removed = true;
				markErased(false);
			}
			return;
		}
		if (level() instanceof ServerLevel sl) {
			int cx = blockPosition().getX() >> 4, cz = blockPosition().getZ() >> 4;
			boolean loaded = data.preheatCache != null ? data.preheatCache.hasChunk(cx, cz) : sl.hasChunk(cx, cz);
			if (!loaded || isAddedToWorld() && !EntityStorageHelper.isTicking(sl, this)) {
				data.removed = true;
				markErased(false);
			}
		}
	}

	public void checkBelowWorld() {
		if (this.getY() < (double) (this.level().getMinBuildHeight() - 64)) {
			markErased(false);
		}
	}

	protected void terminate() {

	}

	protected void projectileMove() {
		applyMove(computeMove());
	}

	/**
	 * Compute the next movement without modifying entity state.
	 * Thread-safe for different entities called in parallel (each entity has its own mover instance).
	 * Used by ClientDanmakuCache for parallel tick computation.
	 */
	public ProjectileMovement computeMove() {
		return computeMove(getDeltaMovement(), position());
	}

	protected ProjectileMovement computeMove(Vec3 vec, Vec3 pos) {
		return updateVelocity(vec, pos);
	}

	/**
	 * Apply a pre-computed movement to this entity's state.
	 * Must be called on the main thread.
	 */
	public void applyMove(ProjectileMovement movement) {
		setDeltaMovement(movement.vec());
		updateRotation(movement.rot());
		double d2 = getX() + movement.vec().x;
		double d0 = getY() + movement.vec().y;
		double d1 = getZ() + movement.vec().z;
		setPos(d2, d0, d1);
	}

	protected ProjectileMovement updateVelocity(Vec3 vec, Vec3 pos) {
		return ProjectileMovement.of(vec);
	}

	protected void commitPreMoveEffects(TickData data) {
	}

	public boolean shouldRenderAtSqrDistance(double pDistance) {
		double d0 = getBoundingBox().getSize() * 4;
		if (Double.isNaN(d0)) d0 = 4;
		d0 *= 64;
		return pDistance < d0 * d0;
	}

	protected void onHitEntity(EntityHitResult pResult) {
		level().gameEvent(GameEvent.PROJECTILE_LAND, pResult.getLocation(), GameEvent.Context.of(this, null));
	}

	protected void onHitBlock(BlockHitResult pResult) {
		BlockPos pos = pResult.getBlockPos();
		BlockState state = level().getBlockState(pos);
		if (state.is(Blocks.NETHER_PORTAL)) {
			handleInsidePortal(pos);
			return;
		} else if (state.is(Blocks.END_GATEWAY)) {
			BlockEntity be = level().getBlockEntity(pos);
			if (be instanceof TheEndGatewayBlockEntity gate && TheEndGatewayBlockEntity.canEntityTeleport(this)) {
				TheEndGatewayBlockEntity.teleportEntity(level(), pos, state, this, gate);
			}
			return;
		}
		level().gameEvent(GameEvent.PROJECTILE_LAND, pos, GameEvent.Context.of(this, state));
	}

	@Override
	public boolean isValid() {
		return tickCount < lifetime();
	}

}
