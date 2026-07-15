package dev.xkmc.fastprojectileapi.collision;

import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

public enum HitTestType {

	ENEMY {
		@Override
		public boolean canHitEntity(LivingEntity owner, Entity target) {
			if (!target.canBeHitByProjectile() || owner == target) {
				return false;
			}
			if (owner.isPassenger() || target.isPassenger()) {
				if (owner.isPassengerOfSameVehicle(target)) {
					boolean hostile = false;
					if (target instanceof LivingEntity livingTarget) {
						hostile |= livingTarget.getLastHurtMob() == owner;
						hostile |= owner.getLastHurtByMob() == livingTarget;
						hostile |= livingTarget instanceof Mob tm && tm.getTarget() == owner;
						hostile |= owner instanceof Mob om && om.getTarget() == livingTarget;
					}
					if (!hostile) return false;
				}
			}
			if (owner.isAlliedTo(target)) {
				return false;
			}
			if (owner instanceof YoukaiEntity youkai && target instanceof LivingEntity livingTarget) {
				return youkai.shouldHurt(livingTarget);
			}
			return true;
		}
	};

	public abstract boolean canHitEntity(LivingEntity owner, Entity target);

}
