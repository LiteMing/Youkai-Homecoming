package dev.xkmc.youkaishomecoming.content.entity.movement;

import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.CardHolder;
import net.minecraft.world.phys.Vec3;

/**
 * 斜向移动控制器
 * <p>
 * 实现围绕目标的侧向移动，模拟东方 Boss 的环绕走位。
 */
public class StrafeController implements BossMovementController {

    private final YoukaiEntity entity;

    // 配置参数
    private double speedMultiplier = 0.8;
    private int directionChangePeriod = 60; // 多少tick后改变方向
    private double verticalWaveAmplitude = 0.3; // 上下波动幅度
    private double verticalWaveFrequency = 0.1; // 上下波动频率

    // 状态
    private int strafeDirection = 1; // 1 = 顺时针, -1 = 逆时针
    private int ticksSinceChange = 0;

    public StrafeController(YoukaiEntity entity) {
        this.entity = entity;
    }

    @Override
    public Vec3 getDesiredMovement(CardHolder holder) {
        Vec3 target = holder.target();
        if (target == null)
            return Vec3.ZERO;

        ticksSinceChange++;

        // 周期性改变方向
        if (ticksSinceChange >= directionChangePeriod) {
            if (holder.random().nextDouble() < 0.3) { // 30% 概率改变方向
                strafeDirection = -strafeDirection;
            }
            ticksSinceChange = 0;
        }

        Vec3 center = holder.center();
        Vec3 toTarget = target.subtract(center);

        // 计算水平面上的侧向移动
        Vec3 horizontal = toTarget.multiply(1, 0, 1);
        if (horizontal.lengthSqr() < 0.01) {
            horizontal = new Vec3(1, 0, 0); // 防止零向量
        }
        horizontal = horizontal.normalize();

        // 计算垂直于目标方向的侧向向量
        Vec3 strafe = new Vec3(-horizontal.z, 0, horizontal.x).scale(strafeDirection);

        // 添加上下波动
        double verticalOffset = Math.sin(entity.tickCount * verticalWaveFrequency) * verticalWaveAmplitude;

        return strafe.add(0, verticalOffset, 0).normalize();
    }

    @Override
    public int getPriority() {
        return 30; // 斜向移动优先级
    }

    @Override
    public boolean isActive(CardHolder holder) {
        Vec3 target = holder.target();
        if (target == null)
            return false;

        double dist = holder.center().distanceTo(target);
        // 在中等距离时激活斜向移动
        return dist >= 10 && dist <= 40;
    }

    @Override
    public double getSpeedMultiplier() {
        return speedMultiplier;
    }

    // Fluent setters for configuration
    public StrafeController setSpeedMultiplier(double multiplier) {
        this.speedMultiplier = multiplier;
        return this;
    }

    public StrafeController setDirectionChangePeriod(int ticks) {
        this.directionChangePeriod = ticks;
        return this;
    }

    public StrafeController setVerticalWave(double amplitude, double frequency) {
        this.verticalWaveAmplitude = amplitude;
        this.verticalWaveFrequency = frequency;
        return this;
    }

    /**
     * 强制改变方向
     */
    public void flipDirection() {
        strafeDirection = -strafeDirection;
        ticksSinceChange = 0;
    }
}
