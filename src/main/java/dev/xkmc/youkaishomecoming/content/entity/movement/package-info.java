/**
 * Boss 移动控制器包
 * <p>
 * 提供 Boss AI 移动行为的模块化实现：
 * <ul>
 * <li>{@link dev.xkmc.youkaishomecoming.content.entity.movement.BossMovementController}
 * - 控制器接口</li>
 * <li>{@link dev.xkmc.youkaishomecoming.content.entity.movement.CompositeMovementController}
 * - 组合控制器</li>
 * <li>{@link dev.xkmc.youkaishomecoming.content.entity.movement.StrafeController}
 * - 斜向移动</li>
 * <li>{@link dev.xkmc.youkaishomecoming.content.entity.movement.ChaseFleeController}
 * - 追逐/逃跑</li>
 * <li>{@link dev.xkmc.youkaishomecoming.content.entity.movement.DodgeController}
 * - 弹幕闪避</li>
 * <li>{@link dev.xkmc.youkaishomecoming.content.entity.movement.TeleportHelper}
 * - 传送工具</li>
 * </ul>
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
package dev.xkmc.youkaishomecoming.content.entity.movement;

import net.minecraft.MethodsReturnNonnullByDefault;

import javax.annotation.ParametersAreNonnullByDefault;
