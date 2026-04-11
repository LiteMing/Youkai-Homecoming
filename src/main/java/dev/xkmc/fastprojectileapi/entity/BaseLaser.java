package dev.xkmc.fastprojectileapi.entity;

import dev.xkmc.fastprojectileapi.collision.LaserHitHelper;
import dev.xkmc.fastprojectileapi.collision.EntityStorageCache;
import dev.xkmc.fastprojectileapi.collision.HitTestType;
import dev.xkmc.fastprojectileapi.collision.IEntityCache;
import dev.xkmc.fastprojectileapi.collision.IEntityIterator;
import dev.xkmc.fastprojectileapi.collision.UserMatrixCache;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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
		data.blockHit = LaserHitHelper.getHitResultOnProjection(this, pos, rot, checkBlockHit(), checkEntityHit(), data.hitEntities, getEntityIterator());
	}

	@Override
	protected void trimMove(TickData data) {
		if (!checkBlockHit()) return;
		Vec3 pos = data.moveDst == null ? position() : data.moveDst;
		Vec3 rot = data.plannedMovement == null ? rot() : data.plannedMovement.rot();
		Vec3 src = pos.add(0, getBbHeight() / 2f, 0);
		Vec3 dst = src.add(Vec3.directionFromRotation((float) Math.toDegrees(rot.x), (float) Math.toDegrees(rot.y)).scale(getLength()));
		var hit = LaserHitHelper.getBlockHitResultOnProjection(this, src, dst);
		if (hit != null) {
			Vec3 delta = hit.getLocation().subtract(src);
			data.moveDst = pos.add(delta);
		}
	}

	protected IEntityIterator getEntityIterator() {
		if (level() instanceof net.minecraft.server.level.ServerLevel sl) {
			IEntityCache cache = getOwner() instanceof EntityCachingUser user ? user.entityCache().get(sl, user.self()) : EntityStorageCache.get(sl);
			if (getOwner() instanceof LivingEntity owner) {
				return (aabb, filter) -> cache.foreach(aabb, target -> HitTestType.ENEMY.canHitEntity(owner, target) && filter.test(target));
			}
			return cache::foreach;
		}
		return (aabb, filter) -> java.util.List.of();
	}

	@Override
	protected void planPreheatRange(TickData data, UserMatrixCache cache) {
		if (cache == null || !checkEntityHit()) return;
		Vec3 pos = data.moveDst == null ? position() : data.moveDst;
		Vec3 rot = data.plannedMovement == null ? rot() : data.plannedMovement.rot();
		Vec3 v = Vec3.directionFromRotation((float) Math.toDegrees(rot.x), (float) Math.toDegrees(rot.y)).scale(getLength());
		var radius = getEffectiveHitRadius();
		var graze = grazeRange();
		var box = getBoundingBox().move(pos.subtract(position())).expandTowards(v);
		cache.preheat(box.inflate(1 + radius + graze));
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
