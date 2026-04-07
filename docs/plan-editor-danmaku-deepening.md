# 编辑器弹幕系统深化方案

## 现状评估：编辑器能否从零制作弹幕达到 AI-step 符卡效果？

**结论：目前不能。** 当前编辑器能做"从固定位置发射简单弹幕"，但无法覆盖大部分 AI-step 符卡的核心模式。

### 当前编辑器已有的能力

| 能力 | 实现 | 状态 |
|------|------|------|
| 基本环形/直线/随机弹幕 | `FireDanmakuAction` (RING/LINE/RANDOM/AIMED) | ✅ |
| 激光 | `FireLaserAction` | ✅ |
| 加速度运动 | `MoverConfig.AccelerationConfig` → `RectMover` | ✅ |
| 旋转运动 | `MoverConfig.RotateConfig` → `RotateMover` | ✅ |
| 条件发射 (tick间隔/血量/距离) | `ConditionalAction` + `SpellConditions` | ✅ |
| 阶段切换 | `PhaseDefinition` + `Transition` | ✅ |
| 变量系统 | `SetVariable`/`AddVariable`/`NumberProviders.Variable` | ✅ |
| NumberProvider 动态值 | Constant/Random/LerpTime/ByHealth/TickMod/Variable | ✅ |

### 缺失的关键能力（按 AI-step 符卡分析）

---

## 一、弹幕初始位置系统

**问题**: 当前 `FireDanmakuAction.originOffset` 是静态的 `Optional<Vec3>`，只能相对发射者做固定偏移。

**AI-step 符卡中的实际用法**:

| 符卡 | 位置计算方式 | 当前能否实现 |
|------|-------------|-------------|
| CirnoSpell | 发射者位置 + 基于朝向旋转的偏移 | ❌ 需要动态旋转 |
| KoishiSpell | `holder.center()` + 基于 tick 的三角函数偏移 | ❌ 需要表达式 |
| MarisaSpell | 发射者位置 (简单) | ✅ |
| SakuyaSpell | 发射者位置 + 扇形分布 | 部分可以 |

**需要新增的 OriginType（弹幕发射点模式）**:

```java
public interface OriginProvider {
    Vec3 resolve(SpellContext ctx);
}
```

| 模式 | 说明 | 参考 |
|------|------|------|
| `CASTER` | 相对发射者中心偏移（现有） | 大部分符卡 |
| `TARGET` | 相对被瞄准者位置偏移 | 边界弹幕/压制型 |
| `ABSOLUTE` | 绝对世界坐标 | 固定阵型 |
| `CASTER_FACING` | 相对发射者朝向旋转后偏移 | CirnoSpell 的环形出生点 |
| `ORBIT` | 以发射者为圆心，半径+角度+角速度定义 | KoishiSpell 的螺旋出生点 |
| `VARIABLE` | 从变量读取 xyz | 高级组合 |

**LuaSTG 参考**: LuaSTG 中弹幕位置可以是 `self.x + offset`（相对自身）、`player.x`（相对玩家）、任意表达式。等价于我们需要让 originOffset 的每个分量也变成 `NumberProvider`。

**建议实现**:
```java
// 将 Optional<Vec3> originOffset 替换为:
record OriginConfig(
    OriginMode mode,           // CASTER / TARGET / ABSOLUTE
    NumberProvider offsetX,    // 支持变量/表达式
    NumberProvider offsetY,
    NumberProvider offsetZ,
    NumberProvider rotation    // 偏移向量绕Y轴旋转角度 (度)
)
```

---

## 二、弹幕消失后继承位置触发子弹幕（TrailAction）

**问题**: 这是 AI-step 符卡最核心的模式之一，当前编辑器完全不支持。

**现有底层实现**: `ItemDanmakuEntity.afterExpiry` 字段（`TrailAction` 类型），弹幕到期时在其最终位置/方向执行回调，可以发射新弹幕。

**AI-step 中的实际用法**:

| 符卡 | afterExpiry 用法 |
|------|-----------------|
| **CirnoSpell** | Mentos弹幕到期 → 在其位置向玩家发射扇形Ball弹幕 (`IcePopsicle`) |
| **SakuyaSpell** | 刀弹幕停顿 → 在其位置重新发射朝向玩家的弹幕 |
| **ReimuSpell** | 弹幕到达指定位置 → 分裂为多个子弹幕 |
| **SanaeSpell** | 弹幕消失 → 在原位置产生新弹幕 |
| **ClownSpell** | 弹幕到期 → 爆炸产生放射状子弹幕 |

**需要新增**:

```java
// 新 Action: 弹幕到期时的后续动作
record OnExpiryAction(
    // 内嵌的 SpellAction 列表，在弹幕到期时、以弹幕最终位置为 origin 执行
    List<SpellAction> actions
)

// 或者更准确地说，在 FireDanmakuAction 中增加字段:
record FireDanmakuAction(
    ... 现有字段 ...,
    Optional<List<SpellAction>> onExpiry  // 弹幕消失时执行的 action 列表
)
```

**关键设计决策**: `onExpiry` 中的 action 的 `SpellContext` 需要特殊处理：
- `holder.center()` → 应为弹幕消失时的位置（而非 boss 位置）
- `holder.forward()` → 应为弹幕消失时的飞行方向
- 需要一个临时的 `TrailSpellContext`，将弹幕位置/方向注入

**LuaSTG 参考**: LuaSTG 的 `del` 回调 + `last` 回调正是这个功能。弹幕消失时触发一段脚本，可以 `New` 新弹幕。

---

## 三、更多 Mover 类型（Codec化）

**问题**: 底层有丰富的 Mover（PolarMover, CompositeMover, AttachedMover 等），但 `MoverConfigs` 只暴露了 Acceleration 和 Rotate 两种。

**需要 Codec 化的 Mover**:

| Mover | 说明 | 典型用途 | 优先级 |
|-------|------|---------|--------|
| `PolarMoverConfig` | 极坐标运动 (半径+角速度+径向加速) | 螺旋弹幕、展开/收缩 | **高** |
| `CompositeMoverConfig` | 分段运动 (先匀速 N tick → 停顿 → 再加速) | SakuyaSpell 的时停弹幕 | **高** |
| `AttachedMoverConfig` | 跟随发射者移动 | 防御圈 | 中 |
| `HomingMoverConfig` | 追踪目标 (转向强度+延迟+持续) | 追踪弹 | **高** ✓ 已实现 (2026-04-07) |
| `FixedDirMoverConfig` | 固定方向运动 | - | 低 |

**PolarMover 尤其重要**，它是实现螺旋弹幕、花瓣弹幕的基础：

```java
record PolarMoverConfig(
    NumberProvider radius,           // 初始半径
    NumberProvider radialSpeed,      // 径向速度
    NumberProvider radialAccel,      // 径向加速度
    NumberProvider initialAngle,     // 初始角度
    NumberProvider angularSpeed,     // 角速度 (度/tick)
    NumberProvider angularAccel      // 角加速度
) implements MoverConfig
```

**CompositeMover** 对于实现"发射→停顿→再移动"模式至关重要：

```java
record CompositeMoverConfig(
    List<MoverSegment> segments      // 各段 (duration + moverConfig)
) implements MoverConfig

record MoverSegment(
    int duration,                    // 持续 tick 数
    MoverConfig mover                // 该段的运动方式
)
```

---

## 四、朝向系统 (AimMode)

**问题**: 当前 `aimAtTarget` 是 boolean，只有"朝目标"和"朝 +Z 方向"两种。

**AI-step 中的实际用法**:

| 符卡 | 朝向方式 |
|------|---------|
| KoishiSpell | `tick * PI / 20` → 基于时间旋转的方向 |
| MarisaSpell | 始终朝向目标 (aimAtTarget) |
| CirnoSpell | 基于 `getOrientation(dir)` 旋转 → 相对朝向 |
| DoremiSpell | 螺旋角度 |

**需要新增的 AimMode**:

```java
public interface AimMode {
    Vec3 getBaseDirection(SpellContext ctx);
}
```

| 模式 | 说明 |
|------|------|
| `TARGET` | 朝向目标（现有 aimAtTarget=true） |
| `FORWARD` | 发射者朝向 |
| `FIXED` | 固定方向 (+Z 或指定向量) |
| `ANGLE_OFFSET` | 以发射者朝向为基础，加固定角度 |
| `VARIABLE_ANGLE` | 从变量读取角度 (配合 AddVariable 实现旋转) |
| `RANDOM` | 随机方向 |

---

## 五、SpawnShooter — 子发射器

**状态**: 计划中但未实现 (Phase 6.4)。

**为什么重要**: 很多复杂符卡的核心模式是"主体发射一些弹幕/发射器 → 发射器到达指定位置后开始独立发射弹幕"。

```java
record SpawnShooterAction(
    NumberProvider health,           // 子发射器血量 (0=无敌)
    NumberProvider lifetime,         // 存活时间
    NumberProvider damage,           // 接触伤害
    Optional<OriginConfig> origin,   // 出生位置
    Optional<MoverConfig> mover,     // 运动方式
    ResourceLocation subSpellId,     // 子发射器执行的符卡定义 ID
    boolean showSpellCircle          // 是否显示法阵
) implements SpellAction
```

**LuaSTG 参考**: LuaSTG 的 `boss.spell` 和 `enemy` 体系——子发射器本质上就是一个执行自己 `task` 的敌人实体。

---

## 六、缺失的辅助系统

### 6.1 表达式/数学 NumberProvider

当前 NumberProvider 只有 6 种固定类型。要实现复杂弹幕模式（如基于 sin/cos 的螺旋），需要:

```java
// 三角函数
record SinProvider(NumberProvider input, NumberProvider amplitude, NumberProvider phase)
record CosProvider(NumberProvider input, NumberProvider amplitude, NumberProvider phase)
// 基本运算
record MulProvider(NumberProvider a, NumberProvider b)
record AddProvider(NumberProvider a, NumberProvider b)
// phaseTick 直接读取
record PhaseTickProvider() // 返回当前 phaseTick 值
record TotalTickProvider() // 返回总 tick 值
```

**LuaSTG 参考**: LuaSTG 直接用 Lua 表达式，我们不需要做完整脚本引擎，但需要组合式 NumberProvider 来覆盖常见数学模式。

### 6.2 Repeat/Loop Action

```java
// 在同一 tick 内重复执行 action（用于嵌套环形发射）
record RepeatAction(
    NumberProvider count,            // 重复次数
    String indexVariable,            // 迭代变量名（写入 ctx.variable）
    List<SpellAction> body           // 循环体
) implements SpellAction
```

这允许在一个 tick 内做"外环 8 发 × 内环 3 发"这种复合弹幕。

---

## 总结：优先级排序

| 优先级 | 功能 | 原因 | 工作量估计 |
|--------|------|------|-----------|
| **P0** | onExpiry (弹幕到期触发子弹幕) | 大部分非trivial符卡都用 | 中 — 需改 FireDanmakuAction + TrailAction 桥接 |
| **P0** | PolarMoverConfig | 螺旋/花瓣弹幕的基础 | 小 — PolarMover 已存在，只需 Codec 化 |
| **P0** | OriginConfig (动态发射位置) | 几乎所有符卡都需要非固定出生点 | 中 |
| **P1** | CompositeMoverConfig (分段运动) | 时停弹幕/多段运动 | 小 |
| **P1** | HomingMoverConfig (追踪弹) | 追踪型弹幕 | 小 ✓ 已完成 |
| **P1** | AimMode 扩展 | 旋转弹幕/螺旋发射 | 小 |
| **P1** | 数学 NumberProvider (sin/cos/mul/add) | 复杂弹幕模式 | 中 |
| **P1** | RepeatAction (循环) | 复合弹幕 | 小 |
| **P2** | SpawnShooterAction (子发射器) | 复杂多体符卡 | 大 — 需处理子 SpellRuntime |
| **P2** | 编辑器 UI 适配以上新字段 | 可编辑性 | 大 |

---

## 参照 LuaSTG 的编辑器设计原则

主程序员建议参照 LuaSTG。LuaSTG 弹幕编辑器的核心概念：

1. **task（任务）** = 我们的 Phase onTick action 列表
2. **move** = 我们的 MoverConfig（但 LuaSTG 有更丰富的插值模式）
3. **del/kill 回调** = 我们需要的 onExpiry
4. **enemy/shooter** = 我们的 SpawnShooterAction
5. **坐标系** = LuaSTG 统一使用 (x,y) 平面坐标 + 角度，我们是 3D 但弹幕主要在 XZ 平面

**LuaSTG 编辑器（如 LuaSTG Sub Editor / BHCreator）的核心 UI 组件**:
- 弹幕行为树（task tree）— 对应我们的 ActionListPanel
- 参数面板（属性编辑器）— 对应我们的 ActionEditorPanel
- 实时预览 — 对应我们的 SpellPreviewScreen
- 弹幕脚本热重载 — 对应我们的 Apply 按钮

**与 LuaSTG 的关键差异**:
- LuaSTG 是 2D，我们是 3D（但大部分弹幕逻辑在 XZ 平面）
- LuaSTG 用 Lua 脚本，我们用 Codec 序列化 + 编辑器 UI
- LuaSTG 帧率固定 60fps，我们是 20 tps

---

## 实施建议

### Phase 6.5: 弹幕深化核心 (P0 项目)

1. **OriginConfig** — 替换 `Optional<Vec3> originOffset`，支持 CASTER/TARGET/CASTER_FACING 模式 + NumberProvider xyz
2. **onExpiry** — FireDanmakuAction 新增 `Optional<List<SpellAction>> onExpiry`，桥接到底层 `TrailAction`
3. **PolarMoverConfig** — Codec 化 PolarMover
4. 更新编辑器面板支持新字段

### Phase 6.6: 弹幕深化扩展 (P1 项目)

5. **CompositeMoverConfig** + **HomingMoverConfig** ✅
6. **AimMode** 替换 `boolean aimAtTarget`
7. **数学 NumberProvider** (sin/cos/add/mul/tick)
8. **RepeatAction**
9. 更新编辑器面板

### Phase 6.7: 子发射器 (P2)

10. **SpawnShooterAction** 完整实现
11. 子符卡定义的嵌套引用/序列化
