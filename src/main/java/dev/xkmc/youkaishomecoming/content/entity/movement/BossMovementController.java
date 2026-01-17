package dev.xkmc.youkaishomecoming.content.entity.movement;

import dev.xkmc.youkaishomecoming.content.spell.spellcard.CardHolder;
import net.minecraft.world.phys.Vec3;

/**
 * Boss 移动控制器接口
 * <p>
 * 每个控制器负责计算一种移动行为的方向向量。
 * 多个控制器通过 {@link CompositeMovementController} 组合使用。
 */
public interface BossMovementController {

    /**
     * 计算期望的移动方向
     *
     * @param holder 符卡持有者接口
     * @return 移动方向向量 (单位向量或零向量)
     */
    Vec3 getDesiredMovement(CardHolder holder);

    /**
     * 获取优先级，数值越高越优先
     * <p>
     * 建议优先级范围：
     * - 闪避: 100 (最高优先)
     * - 追逐/逃跑: 50
     * - 斜向移动: 30
     * - 默认移动: 10
     *
     * @return 优先级数值
     */
    int getPriority();

    /**
     * 检查控制器是否应该激活
     *
     * @param holder 符卡持有者接口
     * @return 如果应该激活返回 true
     */
    boolean isActive(CardHolder holder);

    /**
     * 获取移动速度倍率
     *
     * @return 速度倍率 (1.0 = 正常速度)
     */
    default double getSpeedMultiplier() {
        return 1.0;
    }

    /**
     * 控制器被激活时调用
     */
    default void onActivate(CardHolder holder) {
    }

    /**
     * 控制器被停用时调用
     */
    default void onDeactivate(CardHolder holder) {
    }
}
