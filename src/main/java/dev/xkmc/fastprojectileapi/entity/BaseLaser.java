package dev.xkmc.fastprojectileapi.entity;

import dev.xkmc.fastprojectileapi.collision.LaserHitHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

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
		data.laserHit = LaserHitHelper.getHitResultOnProjection(this, checkBlockHit(), checkEntityHit());
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
