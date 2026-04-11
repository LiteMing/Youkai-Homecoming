package dev.xkmc.fastprojectileapi.collision;

import dev.xkmc.fastprojectileapi.entity.BaseProjectile;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public class ProjectileHitHelper {

	@Nullable
	public static BlockHitResult getHitResultOnMoveVector(BaseProjectile e, Vec3 src, Vec3 dst, boolean checkBlock, List<Entity> hitEntities, IEntityIterator iterator) {
		BlockHitResult blockHit = checkBlock ? getBlockHitResultOnMoveVector(e, src, dst) : null;
		Vec3 entityDst = blockHit == null ? dst : blockHit.getLocation();
		collectEntityHitOnMoveVector(e, src, entityDst, hitEntities, iterator);
		return blockHit;
	}

	@Nullable
	public static BlockHitResult getBlockHitResultOnMoveVector(BaseProjectile e, Vec3 src, Vec3 dst) {
		Level level = e.level();
		var hit = level.clip(new ClipContext(src, dst, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
		return hit.getType() == HitResult.Type.MISS ? null : hit;
	}

	public static void collectEntityHitOnMoveVector(BaseProjectile e, Vec3 src, Vec3 dst, List<Entity> hitEntities, IEntityIterator iterator) {
		Level level = e.level();
		if (!(level instanceof net.minecraft.server.level.ServerLevel)) return;
		Vec3 v = dst.subtract(src);
		var radius = e.getBbWidth() / 2f;
		var graze = e.grazeRange();
		var box = e.getBoundingBox().move(src.subtract(e.position())).expandTowards(v);
		var list = iterator.foreach(box.inflate(1 + radius + graze), HitTestType.ENEMY);
		e.tickData().candidateCount += list.size();
		double d0 = Double.MAX_VALUE;
		EntityInfo entity = null;
		for (EntityInfo x : list) {
			if (x.entity() == e) continue;
			var hpos = checkHit(x, e.alterHitBox(x.entity(), radius, 0), src, dst);
			if (hpos != null) {
				double d1 = src.distanceToSqr(hpos);
				if (d1 < d0) {
					entity = x;
					d0 = d1;
				}
			} else if (graze > 0 && x.entity() instanceof Player pl) {
				var gr = checkHit(x, e.alterHitBox(x.entity(), radius, graze), src, dst);
				if (gr != null) {
					e.tickData().grazeCount++;
					e.doGraze(pl);
				}
			}
		}
		if (entity != null) {
			hitEntities.add(entity.entity());
		}
	}

	@Nullable
	public static Vec3 checkHit(Entity e, AABB base, Vec3 src, Vec3 dst) {
		Vec3 vel = e.getDeltaMovement();
		double speed = vel.length();
		int n = (int) Math.min(8, Math.floor(speed / 0.5));
		for (int i = 0; i <= n; i++) {
			AABB aabb = n == 0 ? base : base.move(vel.scale(1d * i / n));
			Optional<Vec3> optional = aabb.contains(src) ? Optional.of(src) : aabb.clip(src, dst);
			if (optional.isPresent())
				return optional.get();
		}
		return null;
	}

	@Nullable
	public static Vec3 checkHit(EntityInfo e, AABB base, Vec3 src, Vec3 dst) {
		Vec3 vel = e.deltaMovement();
		double speed = vel.length();
		int n = (int) Math.min(8, Math.floor(speed / 0.5));
		for (int i = 0; i <= n; i++) {
			AABB aabb = n == 0 ? base : base.move(vel.scale(1d * i / n));
			Optional<Vec3> optional = aabb.contains(src) ? Optional.of(src) : aabb.clip(src, dst);
			if (optional.isPresent()) {
				return optional.get();
			}
		}
		return null;
	}

}
