package dev.xkmc.fastprojectileapi.entity;

import dev.xkmc.fastprojectileapi.collision.LaserHitHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public abstract class BaseLaser extends AsyncProjectile {

	public BaseLaser(EntityType<?> pEntityType, Level pLevel) {
		super(pEntityType, pLevel);
	}

	public abstract double getLength();

	public abstract boolean checkBlockHit();

	public abstract boolean checkEntityHit();

	public abstract float getEffectiveHitRadius();

	@Override
	protected void collectCollisionInput(TickData data) {
		Vec3 pos = data.moveDst == null ? position() : data.moveDst;
		Vec3 rot = data.plannedMovement == null ? rot() : data.plannedMovement.rot();
		data.blockHit = LaserHitHelper.getHitResultOnProjection(this, pos, rot, checkBlockHit(), checkEntityHit(), data.hitEntities);
	}

	@Override
	protected void resolveCollision(TickData data) {
		if (data.blockHit != null || !data.hitEntities.isEmpty()) {
			onHit(data.blockHit, data.hitEntities);
		}
	}

	protected void onHit(BlockHitResult blockHit, Iterable<Entity> hitEntities) {

	}

	@Override
	protected void defineSynchedData() {

	}

}
