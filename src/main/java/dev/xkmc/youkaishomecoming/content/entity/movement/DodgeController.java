package dev.xkmc.youkaishomecoming.content.entity.movement;

import dev.xkmc.fastprojectileapi.entity.SimplifiedProjectile;
import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.CardHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 弹幕闪避控制器
 * <p>
 * 检测附近的敌方弹幕并计算最优闪避方向。
 * 这是最高优先级的控制器。
 */
public class DodgeController implements BossMovementController {

    private final YoukaiEntity entity;

    // 配置参数
    private double scanRadius = 8.0; // 扫描半径
    private double dangerRadius = 4.0; // 危险距离 (触发闪避)
    private double speedMultiplier = 1.5; // 闪避速度倍率
    private int cooldownTicks = 10; // 闪避冷却
    private double predictionTicks = 10; // 弹幕轨迹预测tick数

    // 状态
    private int cooldown = 0;
    private Vec3 lastDodgeDirection = Vec3.ZERO;

    public DodgeController(YoukaiEntity entity) {
        this.entity = entity;
    }

    @Override
    public Vec3 getDesiredMovement(CardHolder holder) {
        if (cooldown > 0) {
            cooldown--;
            // 冷却期间继续使用上次的闪避方向
            return lastDodgeDirection.scale(0.5);
        }

        Vec3 center = holder.center();
        Vec3 dangerCenter = Vec3.ZERO;
        int dangerCount = 0;

        // 扫描附近弹幕
        List<Entity> nearbyProjectiles = getNearbyProjectiles(center);

        for (Entity e : nearbyProjectiles) {
            if (!isHostileProjectile(e))
                continue;

            Vec3 projPos = e.position();
            Vec3 projVel = e.getDeltaMovement();

            // 预测弹幕未来位置
            Vec3 futurePos = projPos.add(projVel.scale(predictionTicks));

            // 检查是否会接近
            double currentDist = projPos.distanceTo(center);
            double futureDist = futurePos.distanceTo(center);

            if (futureDist < dangerRadius || currentDist < dangerRadius) {
                // 计算威胁向量 (弹幕指向Boss的方向)
                Vec3 threat = center.subtract(projPos).normalize();
                dangerCenter = dangerCenter.add(projPos);
                dangerCount++;
            }
        }

        if (dangerCount == 0) {
            lastDodgeDirection = Vec3.ZERO;
            return Vec3.ZERO;
        }

        // 计算危险区域中心
        dangerCenter = dangerCenter.scale(1.0 / dangerCount);

        // 闪避方向: 远离危险区域
        Vec3 escapeDir = center.subtract(dangerCenter);
        if (escapeDir.lengthSqr() < 0.01) {
            // 如果在危险中心，随机选择方向
            escapeDir = new Vec3(
                    holder.random().nextDouble() * 2 - 1,
                    holder.random().nextDouble() * 0.5,
                    holder.random().nextDouble() * 2 - 1);
        }

        lastDodgeDirection = escapeDir.normalize();
        cooldown = cooldownTicks;

        return lastDodgeDirection;
    }

    @Override
    public int getPriority() {
        return 100; // 最高优先级
    }

    @Override
    public boolean isActive(CardHolder holder) {
        if (cooldown > 0)
            return true; // 冷却期间保持激活

        Vec3 center = holder.center();
        List<Entity> nearbyProjectiles = getNearbyProjectiles(center);

        for (Entity e : nearbyProjectiles) {
            if (!isHostileProjectile(e))
                continue;

            Vec3 projPos = e.position();
            Vec3 projVel = e.getDeltaMovement();
            Vec3 futurePos = projPos.add(projVel.scale(predictionTicks));

            double futureDist = futurePos.distanceTo(center);
            double currentDist = projPos.distanceTo(center);

            if (futureDist < dangerRadius || currentDist < dangerRadius) {
                return true;
            }
        }

        return false;
    }

    @Override
    public double getSpeedMultiplier() {
        return speedMultiplier;
    }

    private List<Entity> getNearbyProjectiles(Vec3 center) {
        AABB scanBox = new AABB(
                center.x - scanRadius, center.y - scanRadius, center.z - scanRadius,
                center.x + scanRadius, center.y + scanRadius, center.z + scanRadius);
        return entity.level().getEntities(entity, scanBox, this::isProjectile);
    }

    private boolean isProjectile(Entity e) {
        return e instanceof Projectile || e instanceof SimplifiedProjectile;
    }

    private boolean isHostileProjectile(Entity e) {
        if (e instanceof Projectile proj) {
            // 玩家发射的弹幕是敌对的
            return proj.getOwner() instanceof Player;
        }
        if (e instanceof SimplifiedProjectile sp) {
            // 检查是否是玩家弹幕
            return sp.getOwner() instanceof Player;
        }
        return false;
    }

    // Fluent setters
    public DodgeController setScanRadius(double radius) {
        this.scanRadius = radius;
        return this;
    }

    public DodgeController setDangerRadius(double radius) {
        this.dangerRadius = radius;
        return this;
    }

    public DodgeController setSpeedMultiplier(double multiplier) {
        this.speedMultiplier = multiplier;
        return this;
    }

    public DodgeController setCooldownTicks(int ticks) {
        this.cooldownTicks = ticks;
        return this;
    }

    public DodgeController setPredictionTicks(double ticks) {
        this.predictionTicks = ticks;
        return this;
    }
}
