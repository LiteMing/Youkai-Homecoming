package dev.xkmc.fastprojectileapi.collision;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public interface IEntityCache {

	SectionCache get(int x, int y, int z);

	@FunctionalInterface
	interface CachedEntityVisitor {
		void accept(SectionCache.CachedEntity candidate);
	}

	default void visit(AABB aabb, Predicate<Entity> filter, CachedEntityVisitor visitor) {
		visit(aabb,
				(((int) aabb.minX) >> 4) - 1,
				(((int) aabb.minY) >> 4) - 1,
				(((int) aabb.minZ) >> 4) - 1,
				(((int) aabb.maxX) >> 4) + 1,
				(((int) aabb.maxY) >> 4) + 1,
				(((int) aabb.maxZ) >> 4) + 1,
				filter,
				visitor);
	}

	default void visit(AABB aabb,
	                   int x0,
	                   int y0,
	                   int z0,
	                   int x1,
	                   int y1,
	                   int z1,
	                   Predicate<Entity> filter,
	                   CachedEntityVisitor visitor) {
		for (int x = x0; x <= x1; x++) {
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) {
					for (var candidate : get(x, y, z).intersect(aabb)) {
						if (aabb.intersects(candidate.sweepBox()) && filter.test(candidate.entity())) {
							visitor.accept(candidate);
						}
					}
				}
			}
		}
	}

	default List<Entity> foreach(AABB aabb, Predicate<Entity> filter) {
		List<Entity> list = new ArrayList<>();
		visit(aabb, filter, candidate -> list.add(candidate.entity()));
		return list;
	}

}
