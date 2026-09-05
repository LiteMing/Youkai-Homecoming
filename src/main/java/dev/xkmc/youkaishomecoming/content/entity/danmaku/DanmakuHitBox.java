package dev.xkmc.youkaishomecoming.content.entity.danmaku;

import dev.xkmc.youkaishomecoming.init.registrate.YHAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;

/** Shared danmaku hit-volume scaling for collision and pilot prediction. */
public final class DanmakuHitBox {

	/** One Minecraft pixel; a zero multiplier still retains this much volume. */
	public static final double MIN_EDGE = 1d / 16d;

	private DanmakuHitBox() {
	}

	public static double scale(Entity entity) {
		Entity root = entity;
		while (root instanceof PartEntity<?> part) root = part.getParent();
		if (root instanceof LivingEntity living && living.getAttributes().hasAttribute(YHAttributes.HITBOX.get())) {
			return Math.max(0, living.getAttributeValue(YHAttributes.HITBOX.get()));
		}
		return 1;
	}

	/** Centers an equal-sided danmaku volume on the eye anchor. */
	public static AABB scaled(Entity entity, AABB box) {
		return scaled(box, anchor(entity, box), scale(entity));
	}

	public static AABB scaled(AABB box, Vec3 anchor, double multiplier) {
		double factor = Math.max(0, multiplier);
		double edge = (box.getXsize() + box.getYsize() + box.getZsize()) / 3.0 * factor;
		return new AABB(
				centeredMin(anchor.x, edge),
				centeredMin(anchor.y, edge),
				centeredMin(anchor.z, edge),
				centeredMax(anchor.x, edge),
				centeredMax(anchor.y, edge),
				centeredMax(anchor.z, edge)
		);
	}

	private static double centeredMin(double center, double size) {
		return center - Math.max(MIN_EDGE, size) * 0.5;
	}

	private static double centeredMax(double center, double size) {
		return center + Math.max(MIN_EDGE, size) * 0.5;
	}

	private static Vec3 anchor(Entity entity, AABB box) {
		Entity root = entity;
		while (root instanceof PartEntity<?> part) root = part.getParent();
		if (root instanceof LivingEntity) {
			return new Vec3(root.getX(), root.getEyeY(), root.getZ());
		}
		return box.getCenter();
	}
}
