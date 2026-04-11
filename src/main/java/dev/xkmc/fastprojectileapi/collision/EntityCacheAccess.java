package dev.xkmc.fastprojectileapi.collision;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

public class EntityCacheAccess {

	private final IEntityCache cache;
	private final LivingEntity owner;

	public EntityCacheAccess(IEntityCache cache, LivingEntity owner) {
		this.cache = cache;
		this.owner = owner;
	}

	public List<EntityInfo> foreach(AABB aabb, HitTestType type) {
		List<EntityInfo> list = new ArrayList<>();
		for (var entity : cache.foreach(aabb, target -> type.canHitEntity(owner, target))) {
			list.add(new EntityInfo(owner, entity));
		}
		return list;
	}

}
