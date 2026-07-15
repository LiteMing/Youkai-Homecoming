package dev.xkmc.fastprojectileapi.collision;

import dev.xkmc.fastprojectileapi.entity.BaseLaser;
import net.minecraft.util.Mth;
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
	public static BlockHitResult getHitResultOnProjection(BaseLaser e, Vec3 pos, Vec3 rot, boolean checkBlock, boolean checkEntity, List<Entity> hitEntities, IEntityIterator iterator) {
		Vec3 src = pos.add(0, e.getBbHeight() / 2f, 0);
		Vec3 v = Vec3.directionFromRotation((float) (rot.x * Mth.RAD_TO_DEG), (float) (rot.y * Mth.RAD_TO_DEG)).scale(e.getLength());
		Vec3 dst = src.add(v);
		BlockHitResult bhit = checkBlock ? getBlockHitResultOnProjection(e, src, dst) : null;
		Vec3 entityDst = bhit == null ? dst : bhit.getLocation();
		if (checkEntity) {
			collectEntityHitOnProjection(e, pos, src, entityDst, v, hitEntities, iterator);
		}
		return bhit;
	}

	@Nullable
	public static BlockHitResult getBlockHitResultOnProjection(BaseLaser e, Vec3 src, Vec3 dst) {
		Level level = e.level();
		var hit = level.clip(new ClipContext(src, dst, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
		return hit.getType() == HitResult.Type.MISS ? null : hit;
	}

	public static void collectEntityHitOnProjection(BaseLaser e, Vec3 pos, Vec3 src, Vec3 dst, Vec3 direction, List<Entity> hitEntities, IEntityIterator iterator) {
		Level level = e.level();
		if (!(level instanceof net.minecraft.server.level.ServerLevel)) return;
		var radius = e.getEffectiveHitRadius();
		var graze = e.grazeRange();
		var box = e.getBoundingBox().move(pos.subtract(e.position())).expandTowards(direction);
		var list = iterator.foreach(box.inflate(1 + radius + graze), HitTestType.ENEMY);
		e.tickData().candidateCount += list.size();
		for (EntityInfo x : list) {
			if (x.entity() == e || e.ignoresEntity(x.entity())) continue;
			Vec3 hit = ProjectileHitHelper.checkHit(x, e.alterHitBox(x, radius, 0), src, dst);
			if (hit != null) hitEntities.add(x.entity());
			if (graze > 0 && x.entity() instanceof Player pl) {
				Vec3 gr = ProjectileHitHelper.checkHit(x, e.alterHitBox(x, radius, graze), src, dst);
				if (gr != null) {
					e.tickData().grazeCount++;
					e.tickData().grazed.add(pl);
				}
			}
		}
	}


}
