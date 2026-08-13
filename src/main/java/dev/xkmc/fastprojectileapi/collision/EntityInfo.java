package dev.xkmc.fastprojectileapi.collision;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import dev.xkmc.youkaishomecoming.content.capability.GrazeHelper;
import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import net.minecraftforge.entity.PartEntity;

import java.util.UUID;

public class EntityInfo {

	private final Entity entity;
	private final AABB boundingBox;
	private final Vec3 deltaMovement;
	private final float hitBoxDelta;
	private final boolean ownerTrackedByYoukai;
	private final UUID rootUuid;
	private final boolean untargetedPlayerSpellTarget;
	private final boolean canReceiveDanmaku;
	private final boolean[] hitTestResults;

	public EntityInfo(LivingEntity owner, Entity entity) {
		this.entity = entity;
		this.boundingBox = entity.getBoundingBox();
		this.deltaMovement = entity.getDeltaMovement();
		this.hitBoxDelta = entity instanceof Player player ? GrazeHelper.getHitBoxDelta(player) : 0;
		this.ownerTrackedByYoukai = owner instanceof Player player && entity instanceof YoukaiEntity youkai && youkai.targets.contains(player);
		Entity root = entity;
		while (root instanceof PartEntity<?> part) root = part.getParent();
		this.rootUuid = root.getUUID();
		this.untargetedPlayerSpellTarget = owner instanceof Player player && root instanceof LivingEntity living
				&& GrazeHelper.isUntargetedPlayerSpellTarget(player, living);
		this.canReceiveDanmaku = !(root instanceof Player player) || GrazeHelper.canReceiveDanmaku(player);
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

	public float hitBoxDelta() {
		return hitBoxDelta;
	}

	public boolean ownerTrackedByYoukai() {
		return ownerTrackedByYoukai;
	}

	public UUID rootUuid() {
		return rootUuid;
	}

	public boolean untargetedPlayerSpellTarget() {
		return untargetedPlayerSpellTarget;
	}

	public boolean canReceiveDanmaku() {
		return canReceiveDanmaku;
	}

	public boolean canHit(HitTestType type) {
		return hitTestResults[type.ordinal()];
	}

	public AABB sweepBox() {
		return boundingBox.expandTowards(deltaMovement);
	}

}
