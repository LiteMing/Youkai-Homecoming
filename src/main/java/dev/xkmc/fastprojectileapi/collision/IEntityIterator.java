package dev.xkmc.fastprojectileapi.collision;

import net.minecraft.world.phys.AABB;

import java.util.List;

@FunctionalInterface
public interface IEntityIterator {

	List<EntityInfo> foreach(AABB aabb, HitTestType type);

}
