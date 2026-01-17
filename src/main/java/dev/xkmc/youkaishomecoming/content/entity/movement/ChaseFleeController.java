package dev.xkmc.youkaishomecoming.content.entity.movement;

import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.CardHolder;
import net.minecraft.world.phys.Vec3;

/**
 * 追逐/逃跑控制器
 * <p>
 * 根据与目标的距离决定追逐或后撤行为。
 * 支持斜向接近/撤离以增加移动的变化性。
 */
public class ChaseFleeController implements BossMovementController {

    private final YoukaiEntity entity;

    // 配置参数
    private double idealDistance = 20.0; // 理想战斗距离
    private double chaseThreshold = 35.0; // 超过此距离开始追逐
    private double fleeThreshold = 8.0; // 低于此距离开始后撤
    private double chaseSpeedMultiplier = 1.2;
    private double fleeSpeedMultiplier = 1.0;
    private double diagonalFactor = 0.3; // 斜向移动因子

    // 状态
    private int diagonalDirection = 1;
    private int ticksSinceDiagonalChange = 0;

    public ChaseFleeController(YoukaiEntity entity) {
        this.entity = entity;
    }

    @Override
    public Vec3 getDesiredMovement(CardHolder holder) {
        Vec3 target = holder.target();
        if (target == null)
            return Vec3.ZERO;

        ticksSinceDiagonalChange++;
        if (ticksSinceDiagonalChange > 40 && holder.random().nextDouble() < 0.02) {
            diagonalDirection = -diagonalDirection;
            ticksSinceDiagonalChange = 0;
        }

        Vec3 center = holder.center();
        double dist = center.distanceTo(target);

        Vec3 toTarget = target.subtract(center);
        Vec3 horizontal = toTarget.multiply(1, 0, 1);
        if (horizontal.lengthSqr() < 0.01) {
            return Vec3.ZERO;
        }
        horizontal = horizontal.normalize();

        // 计算侧向分量
        Vec3 side = new Vec3(-horizontal.z, 0, horizontal.x).scale(diagonalDirection * diagonalFactor);

        if (dist > chaseThreshold) {
            // 追逐: 向目标移动，带斜向分量
            return horizontal.add(side).normalize();
        } else if (dist < fleeThreshold) {
            // 后撤: 远离目标，带斜向分量
            return horizontal.scale(-1).add(side).normalize();
        }

        return Vec3.ZERO; // 在理想距离范围内不做追逐/逃跑
    }

    @Override
    public int getPriority() {
        return 50; // 追逐/逃跑优先级
    }

    @Override
    public boolean isActive(CardHolder holder) {
        Vec3 target = holder.target();
        if (target == null)
            return false;

        double dist = holder.center().distanceTo(target);
        return dist > chaseThreshold || dist < fleeThreshold;
    }

    @Override
    public double getSpeedMultiplier() {
        // 简化: 使用追逐速度作为默认
        return chaseSpeedMultiplier;
    }

    /**
     * 动态获取速度倍率
     */
    public double getSpeedMultiplier(CardHolder holder) {
        if (holder.target() == null)
            return 1.0;
        double dist = holder.center().distanceTo(holder.target());
        if (dist > chaseThreshold) {
            return chaseSpeedMultiplier;
        } else if (dist < fleeThreshold) {
            return fleeSpeedMultiplier;
        }
        return 1.0;
    }

    // Fluent setters
    public ChaseFleeController setIdealDistance(double distance) {
        this.idealDistance = distance;
        return this;
    }

    public ChaseFleeController setChaseThreshold(double threshold) {
        this.chaseThreshold = threshold;
        return this;
    }

    public ChaseFleeController setFleeThreshold(double threshold) {
        this.fleeThreshold = threshold;
        return this;
    }

    public ChaseFleeController setChaseSpeed(double multiplier) {
        this.chaseSpeedMultiplier = multiplier;
        return this;
    }

    public ChaseFleeController setFleeSpeed(double multiplier) {
        this.fleeSpeedMultiplier = multiplier;
        return this;
    }

    public ChaseFleeController setDiagonalFactor(double factor) {
        this.diagonalFactor = factor;
        return this;
    }
}
