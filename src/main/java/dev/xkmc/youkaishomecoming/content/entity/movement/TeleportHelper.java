package dev.xkmc.youkaishomecoming.content.entity.movement;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 传送工具类
 * <p>
 * 统一的传送 API，支持多种传送特效。
 * 整合自 ReimuSpell.teleport() 和 YukariSpell.teleport()。
 */
public class TeleportHelper {

    /**
     * 传送类型枚举
     */
    public enum TeleportType {
        /**
         * 安静传送 (无声音)
         */
        SILENT,
        /**
         * 末影人式传送
         */
        ENDERMAN,
        /**
         * 闪光传送 (带粒子效果)
         */
        FLASH,
        /**
         * 残影传送 (留下残影)
         */
        AFTERIMAGE
    }

    /**
     * 执行传送
     *
     * @param entity    要传送的实体
     * @param target    目标位置
     * @param type      传送类型
     * @param onSuccess 传送成功后的回调
     * @return 是否传送成功
     */
    public static boolean teleport(LivingEntity entity, Vec3 target, TeleportType type, @Nullable Runnable onSuccess) {
        Vec3 old = entity.position();

        // 执行传送
        entity.teleportTo(target.x(), target.y(), target.z());

        // 碰撞检测
        if (!entity.level().noCollision(entity)) {
            entity.teleportTo(old.x(), old.y(), old.z());
            return false;
        }

        // 播放特效
        playTeleportEffect(entity, old, target, type);

        // 触发成功回调
        if (onSuccess != null) {
            onSuccess.run();
        }

        return true;
    }

    /**
     * 简化版传送 (使用末影人特效)
     */
    public static boolean teleport(LivingEntity entity, Vec3 target) {
        return teleport(entity, target, TeleportType.ENDERMAN, null);
    }

    /**
     * 随机传送到目标附近
     *
     * @param entity    要传送的实体
     * @param target    目标位置
     * @param minDist   最小距离
     * @param maxDist   最大距离
     * @param attempts  尝试次数
     * @param type      传送类型
     * @param onSuccess 传送成功后的回调
     * @return 是否传送成功
     */
    public static boolean teleportRandom(
            LivingEntity entity,
            Vec3 target,
            double minDist,
            double maxDist,
            int attempts,
            TeleportType type,
            @Nullable Runnable onSuccess) {
        var random = entity.getRandom();

        for (int i = 0; i < attempts; i++) {
            // 生成随机方向
            Vec3 dir = new Vec3(
                    random.nextGaussian(),
                    Math.abs(random.nextGaussian()) * 0.5, // 偏向上方
                    random.nextGaussian()).normalize();

            // 生成随机距离
            double dist = minDist + random.nextDouble() * (maxDist - minDist);

            Vec3 pos = target.add(dir.scale(dist));

            if (teleport(entity, pos, type, onSuccess)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 战术传送 (传送到目标后方)
     */
    public static boolean teleportBehind(
            LivingEntity entity,
            LivingEntity target,
            double distance,
            TeleportType type,
            @Nullable Runnable onSuccess) {
        Vec3 targetPos = target.position();
        Vec3 lookDir = target.getLookAngle().multiply(1, 0, 1).normalize();

        // 传送到目标背后
        Vec3 behind = targetPos.subtract(lookDir.scale(distance));

        return teleport(entity, behind, type, onSuccess);
    }

    /**
     * 拦截传送 (传送到目标前进方向前方)
     */
    public static boolean teleportIntercept(
            LivingEntity entity,
            LivingEntity target,
            double distance,
            TeleportType type,
            @Nullable Runnable onSuccess) {
        Vec3 targetPos = target.position();
        Vec3 velocity = target.getDeltaMovement().multiply(1, 0, 1);

        if (velocity.lengthSqr() < 0.01) {
            // 目标静止，传送到面前
            Vec3 lookDir = target.getLookAngle().multiply(1, 0, 1).normalize();
            Vec3 front = targetPos.add(lookDir.scale(distance));
            return teleport(entity, front, type, onSuccess);
        }

        // 传送到目标移动方向前方
        Vec3 moveDir = velocity.normalize();
        double speed = velocity.length();
        double interceptDist = Math.max(distance, speed * 20);

        Vec3 interceptPos = targetPos.add(moveDir.scale(interceptDist));

        return teleport(entity, interceptPos, type, onSuccess);
    }

    private static void playTeleportEffect(LivingEntity entity, Vec3 from, Vec3 to, TeleportType type) {
        switch (type) {
            case SILENT:
                // 无特效
                entity.level().broadcastEntityEvent(entity, EntityEvent.TELEPORT);
                break;

            case ENDERMAN:
                entity.level().broadcastEntityEvent(entity, EntityEvent.TELEPORT);
                entity.level().gameEvent(GameEvent.TELEPORT, entity.position(), GameEvent.Context.of(entity));
                if (!entity.isSilent()) {
                    entity.level().playSound(null, from.x, from.y, from.z,
                            SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 1.0F);
                    entity.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.0F);
                }
                break;

            case FLASH:
                entity.level().broadcastEntityEvent(entity, EntityEvent.TELEPORT);
                entity.level().gameEvent(GameEvent.TELEPORT, entity.position(), GameEvent.Context.of(entity));
                // TODO: 添加闪光粒子效果
                if (!entity.isSilent()) {
                    entity.level().playSound(null, from.x, from.y, from.z,
                            SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.HOSTILE, 0.5F, 1.5F);
                }
                break;

            case AFTERIMAGE:
                entity.level().broadcastEntityEvent(entity, EntityEvent.TELEPORT);
                entity.level().gameEvent(GameEvent.TELEPORT, entity.position(), GameEvent.Context.of(entity));
                // TODO: 生成残影实体
                if (!entity.isSilent()) {
                    entity.level().playSound(null, from.x, from.y, from.z,
                            SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 1.0F, 1.0F);
                }
                break;
        }
    }
}
