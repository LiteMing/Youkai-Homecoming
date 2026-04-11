package dev.xkmc.fastprojectileapi.entity;

import dev.xkmc.fastprojectileapi.collision.EntityInfo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

public interface GrazingEntity {


	default float grazeRange() {
		return 0;
	}

	default void doGraze(Player entity) {

	}

	default AABB alterHitBox(EntityInfo x, float radius, float graze) {
		return x.boundingBox().inflate(radius + graze);
	}

}
