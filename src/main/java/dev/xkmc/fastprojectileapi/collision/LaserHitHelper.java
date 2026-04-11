package dev.xkmc.fastprojectileapi.collision;

import dev.xkmc.fastprojectileapi.entity.BaseLaser;
import dev.xkmc.fastprojectileapi.entity.EntityCachingUser;
import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class LaserHitHelper {

	@Nullable
	public static BlockHitResult getHitResultOnProjection(BaseLaser e, boolean checkBlock, boolean checkEntity, List<Entity> hitEntities) {
		return getHitResultOnProjection(e, e.position(), e.rot(), checkBlock, checkEntity, hitEntities);
	}

	@Nullable
	public static BlockHitResult getHitResultOnProjection(BaseLaser e, Vec3 pos, Vec3 rot, boolean checkBlock, boolean checkEntity, List<Entity> hitEntities) {
		Vec3 src = pos.add(0, e.getBbHeight() / 2f, 0);
		Vec3 v = Vec3.directionFromRotation((float) (rot.x * Mth.RAD_TO_DEG), (float) (rot.y * Mth.RAD_TO_DEG)).scale(e.getLength());
		Level level = e.level();
		Vec3 dst = src.add(v);
		BlockHitResult bhit = null;
		if (checkBlock) {
			bhit = level.clip(new ClipContext(src, dst, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
			if (bhit.getType() != HitResult.Type.MISS) {
				dst = bhit.getLocation();
			} else bhit = null;
		}
		if (checkEntity && level instanceof ServerLevel sl) {
			var radius = e.getEffectiveHitRadius();
			var graze = e.grazeRange();
			var box = e.getBoundingBox().move(pos.subtract(e.position())).expandTowards(v);
			IEntityCache cache = e.getOwner() instanceof EntityCachingUser user ? user.entityCache().get(sl, user.self()) : EntityStorageCache.get(sl);
			var list = cache.foreach(box.inflate(1 + radius + graze), e::canHitEntity);
			e.tickData().candidateCount += list.size();
			for (Entity x : list) {
				if (x == e) continue;
				Vec3 hit = ProjectileHitHelper.checkHit(x, e.alterHitBox(x, radius, 0), src, dst);
				if (hit != null) hitEntities.add(x);
				if (graze > 0 && x instanceof Player pl) {
					Vec3 gr = ProjectileHitHelper.checkHit(x, e.alterHitBox(x, radius, graze), src, dst);
					if (gr != null) {
						e.tickData().grazeCount++;
						e.doGraze(pl);
					}
				}
			}
		}
		return bhit;
	}


}
