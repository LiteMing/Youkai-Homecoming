package dev.xkmc.fastprojectileapi.collision;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

public class OwnedSectionCache {

	private final SectionCache section;
	private final AABB aabb;
	private final List<EntityInfo> all;
	private final List<EntityInfo> margin;

	public OwnedSectionCache(SectionCache section, LivingEntity owner) {
		this.section = section;
		this.aabb = section.bounds();
		this.all = new ArrayList<>();
		this.margin = new ArrayList<>();
		for (var entity : section.allEntities()) {
			all.add(new EntityInfo(owner, entity));
		}
		for (var entity : section.marginEntities()) {
			margin.add(new EntityInfo(owner, entity));
		}
	}

	public SectionCache section() {
		return section;
	}

	public Iterable<EntityInfo> intersect(AABB box) {
		return aabb.intersects(box) ? all : margin;
	}

}
