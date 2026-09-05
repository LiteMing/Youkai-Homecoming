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

	/** Scales a world-space box around the living entity's eye anchor. */
	public static AABB scaled(Entity entity, AABB box) {
		return scaled(box, anchor(entity, box), scale(entity));
	}

	public static AABB scaled(AABB box, Vec3 anchor, double multiplier) {
		double factor = Math.max(0, multiplier);
		return new AABB(
				scaledMin(box.minX, box.maxX, anchor.x, factor),
				scaledMin(box.minY, box.maxY, anchor.y, factor),
				scaledMin(box.minZ, box.maxZ, anchor.z, factor),
				scaledMax(box.minX, box.maxX, anchor.x, factor),
				scaledMax(box.minY, box.maxY, anchor.y, factor),
				scaledMax(box.minZ, box.maxZ, anchor.z, factor)
		);
	}

	private static double scaledMin(double min, double max, double anchor, double factor) {
		double value = anchor + (min - anchor) * factor;
		double other = anchor + (max - anchor) * factor;
		double lo = Math.min(value, other);
		double hi = Math.max(value, other);
		return hi - lo >= MIN_EDGE ? lo : anchor - MIN_EDGE * 0.5;
	}

	private static double scaledMax(double min, double max, double anchor, double factor) {
		double value = anchor + (min - anchor) * factor;
		double other = anchor + (max - anchor) * factor;
		double lo = Math.min(value, other);
		double hi = Math.max(value, other);
		if (hi - lo >= MIN_EDGE) return hi;
		return anchor + MIN_EDGE * 0.5;
	}

	private static Vec3 anchor(Entity entity, AABB box) {
		Entity root = entity;
		while (root instanceof PartEntity<?> part) root = part.getParent();
		if (root instanceof LivingEntity) {
			return clamp(new Vec3(root.getX(), root.getEyeY(), root.getZ()), box);
		}
		return box.getCenter();
	}

	private static Vec3 clamp(Vec3 pos, AABB box) {
		return new Vec3(
				clamp(pos.x, box.minX, box.maxX),
				clamp(pos.y, box.minY, box.maxY),
				clamp(pos.z, box.minZ, box.maxZ)
		);
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}
}
