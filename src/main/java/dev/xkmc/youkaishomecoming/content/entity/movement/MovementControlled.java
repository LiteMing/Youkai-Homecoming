package dev.xkmc.youkaishomecoming.content.entity.movement;

/**
 * 标记接口：表示实体支持移动控制器
 */
public interface MovementControlled {

    /**
     * 获取移动控制器
     */
    CompositeMovementController getMovementController();

    /**
     * 初始化移动控制器
     * <p>
     * 子类应该覆盖此方法添加自定义控制器
     */
    default void initMovementControllers() {
    }
}
