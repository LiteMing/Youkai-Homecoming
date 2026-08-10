package dev.xkmc.fastprojectileapi.entity;

import dev.xkmc.fastprojectileapi.collision.LaserHitHelper;
import dev.xkmc.fastprojectileapi.collision.IEntityIterator;
import dev.xkmc.fastprojectileapi.collision.UserMatrixCache;
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
	protected void collectCollisionInput(TickData data, IEntityIterator iterator) {
		Vec3 pos = data.moveDst == null ? position() : data.moveDst;
		Vec3 rot = data.plannedMovement == null ? rot() : data.plannedMovement.rot();
		if (checkEntityHit()) {
			Vec3 src = pos.add(0, getBbHeight() / 2f, 0);
			Vec3 direction = Vec3.directionFromRotation((float) Math.toDegrees(rot.x), (float) Math.toDegrees(rot.y)).scale(getLength());
			Vec3 dst = data.blockHit == null ? src.add(direction) : data.blockHit.getLocation();
			LaserHitHelper.collectEntityHitOnProjection(this, pos, src, dst, dst.subtract(src), data.hitEntities, iterator);
		}
	}

	@Override
	protected void trimMove(TickData data) {
		data.blockHit = null;
		if (!checkBlockHit()) return;
		Vec3 pos = data.moveDst == null ? position() : data.moveDst;
		Vec3 rot = data.plannedMovement == null ? rot() : data.plannedMovement.rot();
		Vec3 src = pos.add(0, getBbHeight() / 2f, 0);
		Vec3 dst = src.add(Vec3.directionFromRotation((float) Math.toDegrees(rot.x), (float) Math.toDegrees(rot.y)).scale(getLength()));
		var hit = LaserHitHelper.getBlockHitResultOnProjection(this, src, dst);
		if (hit != null) {
			// moveDst is the laser's next anchor, not a projectile endpoint. Replacing it with
			// the wall hit makes later stages cast from the wall and renders a zero-length beam.
			data.blockHit = hit;
		}
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
