package dev.xkmc.fastprojectileapi.entity;

import dev.xkmc.fastprojectileapi.collision.LaserHitHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
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
		Vec3 pos = data.dst == null ? position() : data.dst;
		Vec3 rot = data.plannedMovement == null ? rot() : data.plannedMovement.rot();
		data.laserHit = LaserHitHelper.getHitResultOnProjection(this, pos, rot, checkBlockHit(), checkEntityHit());
	}

	@Override
	protected void resolveCollision(TickData data) {
		if (data.laserHit != null) {
			onHit(data.laserHit);
		}
	}

	protected void onHit(LaserHitHelper.LaserHitResult hit) {

	}

	@Override
	protected void defineSynchedData() {

	}

}
