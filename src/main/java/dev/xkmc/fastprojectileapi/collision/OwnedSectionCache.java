package dev.xkmc.fastprojectileapi.collision;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

public class OwnedSectionCache {

	private final SectionCache section;
	private final LivingEntity owner;
	private final AABB aabb;
	private List<EntityInfo> all;
	private List<EntityInfo> margin;

	public OwnedSectionCache(SectionCache section, LivingEntity owner) {
		this.section = section;
		this.owner = owner;
		this.aabb = section.bounds();
	}

	public SectionCache section() {
		return section;
	}

	public Iterable<EntityInfo> intersect(AABB box) {
		ensureCache();
		return aabb.intersects(box) ? all : margin;
	}

	private void ensureCache() {
		if (all != null) return;
		all = new ArrayList<>();
		margin = new ArrayList<>();
		for (var entity : section.allEntities()) {
			all.add(new EntityInfo(owner, entity));
		}
		for (var entity : section.marginEntities()) {
			margin.add(new EntityInfo(owner, entity));
		}
	}

}
