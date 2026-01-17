package dev.xkmc.youkaishomecoming.content.entity.youkai;

import dev.xkmc.youkaishomecoming.content.entity.movement.CompositeMovementController;
import dev.xkmc.youkaishomecoming.content.entity.movement.MovementControlled;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

public class YoukaiAttackGoal<T extends YoukaiEntity> extends Goal {

	protected final T youkai;
	private int meleeTime;
	private int shootTime;

	public YoukaiAttackGoal(T youkai) {
		this.youkai = youkai;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	public boolean canUse() {
		LivingEntity livingentity = youkai.getTarget();
		return livingentity != null && livingentity.isAlive() && youkai.canAttack(livingentity);
	}

	public void start() {
		meleeTime = 10;
		shootTime = 20;
		youkai.setAggressive(true);
		youkai.setFlying();
	}

	public void stop() {
		youkai.setAggressive(false);
		youkai.setWalking();
	}

	public boolean requiresUpdateEveryTick() {
		return true;
	}

	public void tick() {
		youkai.setAggressive(true);
		if (shootTime > 0) {
			shootTime--;
		}
		if (meleeTime > 0) {
			meleeTime--;
		}
		if (specialAction()) {
			return;
		}
		LivingEntity target = youkai.getTarget();
		if (target == null)
			return;
		boolean sight = youkai.getSensing().hasLineOfSight(target);
		double dist = youkai.distanceToSqr(target);
		double follow = getShootRange();
		double range = Math.min(follow, youkai.getStopRange());

		// 应用移动控制器
		if (applyMovementController(target, sight, dist)) {
			// 控制器处理了移动，只需要看向目标
			if (dist < follow * follow) {
				youkai.getLookControl().setLookAt(target, 10.0F, 10.0F);
			}
		} else {
			// 默认移动逻辑
			if (!sight) {
				if (dist < follow * follow && youkai.getNavigation().isDone()) {
					youkai.getNavigation().moveTo(target.getX(), target.getY(), target.getZ(), 1.0D);
				}
			}
			if (sight && dist * 2 < range * range) {
				youkai.getNavigation().stop();
			}
			if (dist > range * range && youkai.getNavigation().isDone()) {
				youkai.getNavigation().moveTo(target.getX(), target.getY(), target.getZ(), 2.0D);
			}
		}

		if (dist < follow * follow) {
			if (youkai.spellCard == null)
				attack(target, dist, sight);
			youkai.getLookControl().setLookAt(target, 10.0F, 10.0F);
		}
	}

	/**
	 * 应用移动控制器
	 *
	 * @return 如果控制器处理了移动返回 true
	 */
	protected boolean applyMovementController(LivingEntity target, boolean sight, double distSqr) {
		if (!(youkai instanceof MovementControlled mc)) {
			return false;
		}

		CompositeMovementController controller = mc.getMovementController();
		if (controller == null) {
			return false;
		}

		// 使用 youkai 作为 CardHolder (YoukaiEntity 实现了 LivingCardHolder)
		var result = controller.compute(youkai);

		if (!result.hasMovement()) {
			return false;
		}

		// 计算基础速度
		double baseSpeed = youkai.getAttributeValue(Attributes.FLYING_SPEED);
		Vec3 movement = result.getScaledMovement(baseSpeed);

		// 应用移动
		Vec3 currentVelocity = youkai.getDeltaMovement();
		Vec3 newVelocity = currentVelocity.scale(0.8).add(movement.scale(0.2));

		// 限制最大速度
		double maxSpeed = 0.6;
		if (newVelocity.length() > maxSpeed) {
			newVelocity = newVelocity.normalize().scale(maxSpeed);
		}

		youkai.setDeltaMovement(newVelocity);
		youkai.hasImpulse = true;

		// 停止导航系统（由控制器接管移动）
		youkai.getNavigation().stop();

		return true;
	}

	protected void attack(LivingEntity target, double dist, boolean sight) {
		double melee = getMeleeRange();
		if (sight && dist < melee * melee) {
			if (meleeTime <= 0) {
				meleeTime = 20;
				meleeAttack(target);
			}
		}
		if (shootTime <= 0) {
			shootTime = shoot(target, youkai.targets.getTargets());
		}
	}

	protected void meleeAttack(LivingEntity target) {
		youkai.doHurtTarget(target);
	}

	protected boolean specialAction() {
		return false;
	}

	protected int shoot(LivingEntity target, List<LivingEntity> all) {
		return 20;
	}

	protected double getMeleeRange() {
		return 2;
	}

	public double getShootRange() {
		return youkai.getAttributeValue(Attributes.FOLLOW_RANGE);
	}
}
