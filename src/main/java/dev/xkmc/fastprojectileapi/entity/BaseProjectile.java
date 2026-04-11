package dev.xkmc.fastprojectileapi.entity;

import dev.xkmc.fastprojectileapi.collision.EntityStorageHelper;
import dev.xkmc.fastprojectileapi.collision.ProjectileHitHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
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
		data.src = position();
		data.originalVelocity = getDeltaMovement();
		data.plannedMovement = computeMove(data.originalVelocity, data.src);
		data.dst = data.src.add(data.plannedMovement.vec());
	}

	@Override
	protected void collectCollisionInput(TickData data) {
		Vec3 src = data.src == null ? position() : data.src;
		Vec3 dst = data.dst == null ? src.add(getDeltaMovement()) : data.dst;
		data.projectileHit = ProjectileHitHelper.getHitResultOnMoveVector(this, src, dst, checkBlockHit());
	}

	@Override
	protected void resolveCollision(TickData data) {
		if (data.projectileHit != null) {
			onHit(data.projectileHit);
		}
	}

	@Override
	protected void finishTick(TickData data) {
		if (tickCount >= lifetime()) {
			if (level() instanceof ServerLevel) {
				commitPreMoveEffects(data);
				applyPlannedMove(data);
				terminate();
				markErased(false);
				return;
			}
			var owner = getOwner();
			if (tickCount >= lifetime() + 10 || owner == null || !owner.isAlive()) {
				markErased(false);
			}
			return;
		}
		commitPreMoveEffects(data);
		applyPlannedMove(data);
		if (level() instanceof ServerLevel sl) {
			if (!level().hasChunk(blockPosition().getX() >> 4, blockPosition().getZ() >> 4) ||
					isAddedToWorld() && !EntityStorageHelper.isTicking(sl, this)) {
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

	protected void applyPlannedMove(TickData data) {
		if (data.plannedMovement != null) {
			applyMove(data.plannedMovement);
		} else {
			projectileMove();
		}
	}

	protected void commitPreMoveEffects(TickData data) {
	}

	public boolean shouldRenderAtSqrDistance(double pDistance) {
		double d0 = getBoundingBox().getSize() * 4;
		if (Double.isNaN(d0)) d0 = 4;
		d0 *= 64;
		return pDistance < d0 * d0;
	}

	protected void onHit(HitResult hitresult) {
		if (hitresult.getType() == HitResult.Type.MISS) return;
		if (hitresult instanceof EntityHitResult ehit) {
			onHitEntity(ehit);
			level().gameEvent(GameEvent.PROJECTILE_LAND, hitresult.getLocation(), GameEvent.Context.of(this, null));
		} else if (hitresult instanceof BlockHitResult bhit) {
			BlockPos pos = bhit.getBlockPos();
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
			onHitBlock(bhit);
			level().gameEvent(GameEvent.PROJECTILE_LAND, pos, GameEvent.Context.of(this, level().getBlockState(pos)));
		}
	}

	protected void onHitEntity(EntityHitResult pResult) {
	}

	protected void onHitBlock(BlockHitResult pResult) {
	}

	@Override
	public boolean isValid() {
		return tickCount < lifetime();
	}

}
