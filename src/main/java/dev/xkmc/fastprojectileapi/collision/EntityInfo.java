package dev.xkmc.fastprojectileapi.collision;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class EntityInfo {

	private final Entity entity;
	private final AABB boundingBox;
	private final Vec3 deltaMovement;
	private final boolean[] hitTestResults;

	public EntityInfo(LivingEntity owner, Entity entity) {
		this.entity = entity;
		this.boundingBox = entity.getBoundingBox();
		this.deltaMovement = entity.getDeltaMovement();
		this.hitTestResults = new boolean[HitTestType.values().length];
		for (HitTestType type : HitTestType.values()) {
			hitTestResults[type.ordinal()] = type.canHitEntity(owner, entity);
		}
	}

	public AABB boundingBox() {
		return boundingBox;
	}

	public Entity entity() {
		return entity;
	}

	public Vec3 deltaMovement() {
		return deltaMovement;
	}

	public boolean canHit(HitTestType type) {
		return hitTestResults[type.ordinal()];
	}

	public AABB sweepBox() {
		return boundingBox.expandTowards(deltaMovement);
	}

}
