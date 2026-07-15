package dev.xkmc.youkaishomecoming.content.entity.movement;

import dev.xkmc.youkaishomecoming.content.spell.spellcard.CardHolder;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 组合移动控制器
 * <p>
 * 管理多个 {@link BossMovementController}，根据优先级选择激活的控制器。
 * 支持混合多个控制器的输出。
 */
public class CompositeMovementController {

    private final List<BossMovementController> controllers = new ArrayList<>();
    private BossMovementController activeController = null;
    private int tickCounter = 0;

    /**
     * 添加控制器
     */
    public void add(BossMovementController controller) {
        controllers.add(controller);
        controllers.sort(Comparator.comparingInt(BossMovementController::getPriority).reversed());
    }

    /**
     * 移除控制器
     */
    public void remove(BossMovementController controller) {
        controllers.remove(controller);
    }

    /**
     * 清空所有控制器
     */
    public void clear() {
        controllers.clear();
        activeController = null;
    }

    /**
     * 计算最终移动向量
     *
     * @param holder 符卡持有者
     * @return 移动方向和速度
     */
    public MovementResult compute(CardHolder holder) {
        tickCounter++;

        // 找到最高优先级的激活控制器
        BossMovementController newActive = null;
        for (BossMovementController controller : controllers) {
            if (controller.isActive(holder)) {
                newActive = controller;
                break; // 已按优先级排序
            }
        }

        // 处理控制器切换
        if (newActive != activeController) {
            if (activeController != null) {
                activeController.onDeactivate(holder);
            }
            if (newActive != null) {
                newActive.onActivate(holder);
            }
            activeController = newActive;
        }

        // 计算移动
        if (activeController == null) {
            return MovementResult.ZERO;
        }

        Vec3 movement = activeController.getDesiredMovement(holder);
        double speed = activeController.getSpeedMultiplier();

        return new MovementResult(movement, speed, activeController);
    }

    /**
     * 获取当前激活的控制器
     */
    public BossMovementController getActiveController() {
        return activeController;
    }

    /**
     * 获取所有控制器
     */
    public List<BossMovementController> getControllers() {
        return controllers;
    }

    /**
     * 移动计算结果
     */
    public record MovementResult(Vec3 direction, double speedMultiplier, BossMovementController source) {
        public static final MovementResult ZERO = new MovementResult(Vec3.ZERO, 0, null);

        public boolean hasMovement() {
            return direction.lengthSqr() > 0.001;
        }

        public Vec3 getScaledMovement(double baseSpeed) {
            return direction.normalize().scale(baseSpeed * speedMultiplier);
        }
    }
}
