# 符卡系统重构方案 (Claude)

## 代码审查总结

### 已审阅文件

| 模块 | 核心文件 | 状态 |
|------|---------|------|
| 符卡基础 | `SpellCard`, `ActualSpellCard`, `Ticker`, `ListSpellCard`, `StagedSpellCard`, `SpellCardWrapper` | 完整阅读 |
| 符卡注册 | `TouhouSpellCards`, `YHDanmaku` | 完整阅读 |
| 符卡实现 | `MarisaSpell`, `KoishiSpell` (典型样本) | 完整阅读 |
| Boss实体 | `YoukaiEntity`, `GeneralYoukaiEntity`, `BossYoukaiEntity` | 完整阅读 |
| 弹幕底层 | `DanmakuCommander`, `IYHDanmaku`, `DanmakuHelper`, `CardHolder`, `LivingCardHolder` | 完整阅读 |
| Mover系统 | `DanmakuMover`, `RectMover`, `PolarMover` | 完整阅读 |
| 玩家符卡 | `ItemSpell`, `SpellContainer`, `PlayerHolder` | 完整阅读 |
| 网络同步 | `CombatProgress`, `CombatToClient` | 完整阅读 |
| 现有文档 | `summary-spellcard-review.md`, `plan-spellcard-rework.md` | 完整阅读 |

### 与GPT方案的分歧

GPT方案的整体方向正确，但有几个问题：

1. **过度设计**: 提出了完整的条件/动作DSL、图编辑器、KJS集成等，一次做完工程量极大，而且DSL的粒度设计还停留在概念层。
2. **忽视了Forge注册时序**: Forge物品注册发生在mod构造阶段，datapack在世界加载后才可用。"启动时遍历定义表自动生成SpellItem"需要仔细处理这个时序问题。
3. **StagedSpellCard定位模糊**: GPT方案把StagedSpellCard当作"新的尝试"一笔带过，但实际上它是一个vibecoding中间产物，代码有明确bug（`isDanmakuCombat`每tick扫描50格内所有玩家，`calculateStage`用的是vanilla `getHealth()/getMaxHealth()` 而不是 `CombatProgress`），应该直接替换而不是兼容。

**本方案原则: 渐进式重构，每一步可编译可测试，不做大爆炸式重写。**

---

## 架构设计

### 核心理念

将现有的"类即符卡"模式改为**"定义+运行时"分离模式**，同时保持对现有Java符卡类的向后兼容。

```
SpellDefinition (定义层 - 可序列化/数据包/KJS)
        |
        v
SpellRuntime (运行时 - 持有状态、驱动tick)
        |
        v
CardHolder + DanmakuMover (底层弹幕能力 - 保留不动)
```

### 一、SpellDefinition - 符卡定义

```java
// 统一的符卡定义，可以从Java、JSON、KJS创建
public class SpellDefinition {
    ResourceLocation id;          // 唯一ID，如 "yh:marisa_master_spark"
    SpellDisplay display;         // 显示信息
    SpellItemForm itemForm;       // 物品注册信息 (null=不生成物品)
    ResourceLocation entryPhase;  // 入口阶段ID
    Map<ResourceLocation, PhaseDefinition> phases;  // 阶段图
    @Nullable DifficultyProfile difficulty;          // 难度配置
}
```

**关键决策**: `SpellDefinition` 使用 Forge Registry (`IForgeRegistry`) 而不是普通Map，这样可以：
- 与Forge注册时序兼容
- 自然支持datapack覆盖
- 为KJS提供标准注册点

### 二、PhaseDefinition - 阶段定义

```java
public class PhaseDefinition {
    ResourceLocation id;          // 阶段ID
    List<SpellAction> onEnter;    // 进入时执行
    List<SpellAction> onTick;     // 每tick执行
    List<SpellAction> onExit;     // 退出时执行
    List<Transition> transitions; // 转移条件列表 (按优先级排序)
}

public record Transition(
    SpellCondition condition,      // 触发条件
    ResourceLocation targetPhase,  // 目标阶段
    TransitionMode mode            // IMMEDIATE | CLEAR_SCREEN | DELAYED
) {}
```

**和GPT方案的区别**: 不做完整DSL。条件和动作用接口+Codec多态，Java侧直接实现，KJS侧通过回调扩展。不引入自定义脚本语言。

### 三、SpellCondition - 条件体系

用Codec多态分发，每种条件一个类：

```java
public interface SpellCondition {
    Codec<SpellCondition> CODEC = /* dispatch codec */;
    boolean test(SpellContext ctx);
}

// 内置条件类型 (每个是一个record + Codec)
public record HealthBelow(float threshold) implements SpellCondition { ... }
public record HealthAbove(float threshold) implements SpellCondition { ... }
public record TickElapsed(int ticks) implements SpellCondition { ... }
public record DistanceAbove(double distance) implements SpellCondition { ... }
public record DistanceBelow(double distance) implements SpellCondition { ... }
public record HitCount(int count) implements SpellCondition { ... }
public record And(List<SpellCondition> conditions) implements SpellCondition { ... }
public record Or(List<SpellCondition> conditions) implements SpellCondition { ... }
public record Not(SpellCondition condition) implements SpellCondition { ... }
public record VariableCheck(String key, Comparison op, double value) implements SpellCondition { ... }
```

### 四、SpellAction - 动作体系

同样用Codec多态：

```java
public interface SpellAction {
    Codec<SpellAction> CODEC = /* dispatch codec */;
    void execute(SpellContext ctx);
}

// 核心动作
public record FireDanmaku(DanmakuPattern pattern) implements SpellAction { ... }
public record FireLaser(LaserPattern pattern) implements SpellAction { ... }
public record SetVariable(String key, double value) implements SpellAction { ... }
public record AddVariable(String key, double delta) implements SpellAction { ... }
public record ClearScreen() implements SpellAction { ... }
public record Wait(int ticks) implements SpellAction { ... }
public record PlaySound(SoundEvent sound) implements SpellAction { ... }
public record SetMovement(MovementType type) implements SpellAction { ... }
// 复合动作
public record Repeat(int count, List<SpellAction> actions) implements SpellAction { ... }
public record Conditional(SpellCondition cond, List<SpellAction> ifTrue, List<SpellAction> ifFalse) implements SpellAction { ... }
// 兼容桥接: 直接执行一个旧式Ticker
public record LegacyTicker(Supplier<Ticker<?>> tickerFactory) implements SpellAction { ... }
```

### 五、SpellRuntime - 运行时状态机

```java
public class SpellRuntime {
    // 定义引用
    private SpellDefinition definition;

    // 运行时状态 (全部可序列化)
    private ResourceLocation currentPhaseId;
    private int phaseTick;
    private int totalTick;
    private Map<String, Double> variables = new HashMap<>();
    private List<ActiveTicker> activeTickers = new ArrayList<>();

    // 每tick调用
    public void tick(CardHolder holder) {
        SpellContext ctx = createContext(holder);
        PhaseDefinition phase = definition.phases.get(currentPhaseId);

        // 执行当前阶段的tick动作
        for (SpellAction action : phase.onTick) {
            action.execute(ctx);
        }

        // 驱动活跃的Ticker
        activeTickers.removeIf(t -> t.tick(ctx));

        // 检查转移条件
        for (Transition trans : phase.transitions) {
            if (trans.condition().test(ctx)) {
                transitionTo(ctx, trans);
                break;
            }
        }

        phaseTick++;
        totalTick++;
    }

    private void transitionTo(SpellContext ctx, Transition trans) {
        PhaseDefinition oldPhase = definition.phases.get(currentPhaseId);
        // 退出旧阶段
        for (SpellAction action : oldPhase.onExit) action.execute(ctx);

        // 切换
        currentPhaseId = trans.targetPhase();
        phaseTick = 0;
        if (trans.mode() == TransitionMode.CLEAR_SCREEN) {
            ctx.clearDanmaku();
        }

        // 进入新阶段
        PhaseDefinition newPhase = definition.phases.get(currentPhaseId);
        for (SpellAction action : newPhase.onEnter) action.execute(ctx);

        // 同步客户端
        syncPhaseToClient(ctx);
    }

    public void reset() {
        currentPhaseId = definition.entryPhase;
        phaseTick = 0;
        totalTick = 0;
        variables.clear();
        activeTickers.clear();
    }
}
```

### 六、兼容层 - 现有Java符卡桥接

**不强制迁移现有17个符卡类**。提供桥接：

```java
// 将旧式ActualSpellCard包装为单阶段SpellDefinition
public class LegacySpellBridge {

    public static SpellDefinition fromLegacy(
            ResourceLocation id,
            Supplier<ActualSpellCard> factory,
            SpellDisplay display) {
        var def = new SpellDefinition();
        def.id = id;
        def.display = display;
        def.entryPhase = id.withSuffix("/main");
        def.phases = Map.of(
            def.entryPhase,
            PhaseDefinition.singlePhase(
                new LegacyTickerAction(factory)
            )
        );
        return def;
    }
}
```

这样 `TouhouSpellCards.registerSpells()` 可以逐步迁移：
- 第一步：所有旧卡通过桥接注册，行为不变
- 后续：逐个改写为阶段图定义

### 七、SpellRegistry - 统一注册表

```java
public class SpellRegistry {
    // Forge DeferredRegister
    private static final DeferredRegister<SpellDefinition> SPELLS =
        DeferredRegister.create(SPELL_REGISTRY_KEY, YoukaisHomecoming.MODID);

    // Java内置注册
    public static final RegistryObject<SpellDefinition> MARISA =
        SPELLS.register("marisa", () -> LegacySpellBridge.fromLegacy(
            loc("marisa"), MarisaSpell::new, MarisaDisplay.INSTANCE));

    // 替代TouhouSpellCards的静态Map
    public static void setSpell(GeneralYoukaiEntity e, ResourceLocation id) {
        var def = SPELL_REGISTRY.get().getValue(id);
        if (def == null) return;
        e.spellRuntime = new SpellRuntime(def);
        e.syncSpellState();
    }
}
```

### 八、物品自动注册

**问题**: Forge要求物品在mod构造阶段注册，datapack定义在世界加载后才可用。

**方案**: 分两层：
1. Java内置定义的符卡 → 在mod构造阶段自动生成`SpellItem`
2. Datapack/KJS定义的符卡 → 使用一个通用的`DynamicSpellItem`，通过NBT引用定义ID

```java
// 构造阶段：扫描所有Java注册的SpellDefinition，自动生成物品
public class SpellItemAutoRegister {
    public static void registerItems(IEventBus bus) {
        // 遍历SPELLS中已声明的定义
        for (var entry : SpellRegistry.SPELLS.getEntries()) {
            var def = entry.get();
            if (def.itemForm != null) {
                // 自动注册物品
                YH_ITEMS.register(def.id.getPath() + "_spell",
                    () -> new AutoSpellItem(def));
            }
        }
    }
}

// 运行时：通用动态符卡物品，可以指向任意定义
public class DynamicSpellItem extends Item {
    // NBT中存储 spell_id -> 从注册表查找定义
    public void use(...) {
        ResourceLocation spellId = stack.getTag().getString("spell_id");
        SpellDefinition def = SpellRegistry.get(spellId);
        // ...
    }
}
```

### 九、客户端同步

新增 `SpellStateToClient` 包：

```java
@SerialClass
public class SpellStateToClient extends SerialPacketBase {
    @SerialClass.SerialField public int entityId;
    @SerialClass.SerialField public ResourceLocation spellId;
    @SerialClass.SerialField public ResourceLocation phaseId;
    @SerialClass.SerialField public int phaseTick;
    @SerialClass.SerialField public boolean inDanmakuCombat;
    @SerialClass.SerialField public String transitionReason; // 可选，用于UI提示
}
```

发送时机：
- 符卡初始化时
- 阶段切换时
- 战斗状态变更时

客户端存储在实体的附加数据中，供HUD/渲染器读取。

### 十、DifficultyProfile - 难度系统

```java
public class DifficultyProfile {
    // 倍率曲线: 输入 healthRatio (0~1)，输出 multiplier
    FloatCurve speedMultiplier;     // 弹幕速度
    FloatCurve frequencyMultiplier; // 发射频率
    FloatCurve countMultiplier;     // 弹幕数量

    // 颜色/弹种替换规则
    List<StyleOverride> styleOverrides;

    public DifficultyModifiers resolve(SpellContext ctx) {
        float hp = ctx.healthRatio();
        return new DifficultyModifiers(
            speedMultiplier.evaluate(hp),
            frequencyMultiplier.evaluate(hp),
            countMultiplier.evaluate(hp),
            resolveStyle(ctx)
        );
    }
}
```

`SpellContext`中携带`DifficultyModifiers`，所有`FireDanmaku` action在执行时自动应用。

### 十一、指令支持

```
/yhspell set <entity> <spell_id>          - 设置符卡
/yhspell phase <entity> <phase_id>        - 强制切阶段
/yhspell variable <entity> <key> <value>  - 设置运行时变量
/yhspell reset <entity>                   - 重置符卡状态
/yhspell debug <entity>                   - 输出当前状态
/yhspell reload                           - 重载datapack定义
```

### 十二、KubeJS接入

```javascript
// KubeJS 符卡定义
YHEvents.registerSpells(event => {
    event.create('my_pack:custom_boss')
        .display('Custom Boss Card', 'A custom spell card')
        .phase('phase1', phase => {
            phase.onTick(ctx => {
                if (ctx.tick % 20 == 0) {
                    ctx.fireDanmaku('circle', 'red', { count: 12, speed: 0.8 });
                }
            });
            phase.transition('phase2', cond => cond.healthBelow(0.5));
        })
        .phase('phase2', phase => {
            phase.onEnter(ctx => ctx.clearScreen());
            phase.onTick(ctx => {
                ctx.fireDanmaku('star', 'blue', { count: 24, speed: 1.2 });
            });
            phase.transition('phase1', cond => cond.tickElapsed(200));
        })
        .difficulty(diff => {
            diff.speedMultiplier(hp => 1.0 + (1.0 - hp) * 0.5);
        })
        .itemForm({ icon: 'circle_red', cooldown: 100 });
});
```

### 十三、可视化编辑器

**分阶段实现**:

**Phase 1 - 列表编辑器** (在现有EditorScreen基础上扩展)
- 左侧：阶段列表，可添加/删除/排序
- 右侧：选中阶段的属性面板（沿用现有annotation编辑器）
- 底部：转移条件列表

**Phase 2 - 节点图编辑器**
- 阶段节点 + 条件节点 + 连线
- 不需要复杂画布库，用Minecraft GUI自绘
- 参考Minecraft进度树的UI风格（简洁节点+箭头）

**Phase 3 - 调试预览**
- 实时显示当前tick、当前阶段、变量值
- 阶段切换日志
- 弹幕统计面板

---

## 实施路线

### Phase 0: 修复与准备 (无破坏性变更) ✅ 已完成

1. ✅ **修复`TargetTracker.vel()` bug**: `t2.subtract(t2)` -> `t2.subtract(t1)`
2. ✅ **删除`StagedSpellCard`**: SakuyaSpell和KisinSpell已迁移为ActualSpellCard子类
3. ✅ **将`spellId`与`modelId`解耦**: `SpellCardWrapper`增加独立的`spellId`字段
4. ✅ **`ActualSpellCard.reset()`问题**: SakuyaSpell和KisinSpell已添加正确的reset()

### Phase 1: 核心架构 (新增，不修改旧代码) ✅ 已完成

1. ✅ 创建`SpellDefinition`、`PhaseDefinition`、`SpellCondition`、`SpellAction`接口和基础实现
2. ✅ 创建`SpellRuntime`状态机
3. ✅ 创建`SpellRegistry` (ConcurrentHashMap注册表)
4. ✅ 创建`LegacySpellBridge`，让旧符卡通过桥接注册
5. ✅ 新增`SpellStateToClient`包
6. ✅ 在`YoukaiEntity`中添加`SpellRuntime`字段（与旧`spellCard`并存）

### Phase 2: 指令与数据化 ✅ 基本完成

1. ✅ 实现`/yhspell`指令 (set, phase, variable, reset, debug, list, preview, reapply, export, import)
2. ✅ 实现条件/动作的Codec序列化
3. ✅ 支持JSON导出/导入 (`/yhspell export <id>` + `/yhspell import <path>` + 预览Export按钮)
4. 🔲 支持从datapack JSON自动加载SpellDefinition (需 DatapackRegistry 加载器)
5. 🔲 实现NBT覆盖层

### Phase 3: KubeJS集成 ✅ 已完成

1. ✅ 注册KJS事件 (`YHSpellKubeJSPlugin`, `YHSpellKubeJSEvents`, `RegisterSpellsEventJS`)
2. ✅ 实现JS侧的builder API (`SpellDefinitionBuilderJS`, `PhaseBuilderJS`)
3. ✅ 支持JS回调条件和动作 (`KubeJSSpellActions.JSAction`, `KubeJSSpellConditions.JSCondition`)
4. ✅ 重构`SpellActions`/`SpellConditions`的类型分发为`CLASS_TO_TYPE` map，支持动态注册

### Phase 4: 物品自动注册 ✅ 已完成

1. ✅ 实现`DynamicSpellItem` — 通用符卡物品，从NBT读取spell_id
2. ✅ 实现`RuntimeItemSpell` — 驱动SpellRuntime的ItemSpell子类
3. ✅ 实现`SpellItemAutoRegister` — 创意模式标签页自动填充
4. ✅ 在`YHDanmaku`中注册`DYNAMIC_SPELL`物品
5. 🔲 从`YHDanmaku`迁移手写SpellItem (保留向后兼容，后续按需迁移)

### Phase 5: 编辑器与预览

#### 5.1 正交视图预览系统 ✅ 已完成 (P1-P4)

1. ✅ `ViewAngle` — 视角枚举 (FRONT/SIDE/TOP) + 自由旋转 (右键拖拽)
2. ✅ `ProjectileRenderHelper` — 添加 `cameraOrientationOverride` 和 `flushPreviewQueue()`
3. ✅ `ItemDanmakuRenderer` / `ItemLaserRenderer` — 支持 camera orientation override + 预览模式跳过 fading
4. ✅ `PreviewCardHolder` — 实现 `CardHolder` 接口，弹幕进入本地池而非真实世界；目标可拖拽移动
5. ✅ `VirtualSpellScene` — 虚拟场景管理 (SpellRuntime驱动 + 播放控制)
6. ✅ `OrthographicViewport` — GUI PoseStack 变换渲染 (Scissor + 网格/坐标轴 + 可配置裁切距离)
7. ✅ `SpellPreviewScreen` — 独立预览Screen (视角切换/自由旋转/播放控制/速度/距离/HP/Phase/Range)
8. ✅ `YHCommands` — `/yhspell preview <spell_id>` 指令 (ResourceLocationArgument)
9. ✅ 修复 billboard 弹幕渲染 — `Math.cbrt(Math.abs(...))` 替代 `Math.pow(..., 1/3d)` 处理负行列式
10. ✅ 修复弹幕生命周期 — 使用 `isValid()` 而非 `isRemoved()` 匹配 ClientDanmakuCache 行为

#### 5.2 预览已知问题

1. ✅ 追踪型弹幕/子弹幕不显示 — FakeCasterEntity 实现 CardHolder，ShooterEntity 匿名子类 override aiStep() 在客户端强制调用 serverAiStep()，使子发射器符卡在预览中正常 tick
2. 🔲 billboard 弹幕不随视角旋转 — `set3x3()` 剥离了所有旋转，仅保留平移+缩放（设计如此，非 bug）

#### 5.3 编辑器框架 (E3-E5)

1. 🔲 `SpellEditorScreen` — 主编辑器Screen
2. 🔲 `EditorState` — 编辑状态 + undo/redo
3. 🔲 `PropertyPanel` — 属性面板
4. 🔲 `PhaseGraphCanvas` — 阶段图画布 (节点+连线)
5. 🔲 预览嵌入编辑器底部 (P5)

### Phase 6: 数据驱动弹幕 Action + 预览内编辑

Phase 6 的核心目标：让符卡的弹幕发射逻辑从硬编码 Java 类变为可序列化、可编辑的数据。这是编辑器可行性的关键前置条件。

#### 6.0 前置：NumberProvider 参数系统 ✅ 已完成

所有弹幕数值参数使用 `NumberProvider` 接口而非裸 `double`/`int`，支持动态值源。

```java
public interface NumberProvider {
    Codec<NumberProvider> CODEC = /* dispatch codec */;
    double get(SpellContext ctx);
}
```

内置实现（5种）：

| 类型 | 说明 | JSON 示例 |
|------|------|-----------|
| `Constant` | 固定值 | `{"type": "constant", "value": 12}` |
| `RandomRange` | 随机范围 | `{"type": "random", "min": 0.6, "max": 1.0}` |
| `LerpOverTime` | 随 phaseTick 线性插值 | `{"type": "lerp_time", "start": 0.5, "end": 1.5, "duration": 200}` |
| `ByHealthRatio` | 随血量比例插值 | `{"type": "by_health", "at_full": 0.8, "at_empty": 1.6}` |
| `PhaseTickMod` | phaseTick 取模（用于周期性发射） | `{"type": "tick_mod", "period": 20}` |

文件：`content/spell/definition/NumberProvider.java`, `NumberProviders.java`

#### 6.1 核心弹幕 Action（3个） ✅ FireDanmaku/FireLaser 已完成, 🔲 SpawnShooter 待定

**`FireDanmaku`** — 发射弹幕

```java
record FireDanmaku(
    YHDanmaku.Bullet bulletType,     // 弹幕类型 (CIRCLE/BALL/BUBBLE/...)
    DyeColor color,                  // 颜色 (16种)
    NumberProvider count,             // 数量 (每次发射几发)
    NumberProvider speed,             // 飞行速度
    NumberProvider lifetime,          // 存活时间 (tick)
    NumberProvider angleOffset,       // 初始角度偏移 (度)
    NumberProvider spread,            // 扩散角度 (度) — 0=平行, 360=全方向
    PatternType pattern,             // 排列模式: RING(环形), LINE(直线), RANDOM(随机)
    @Nullable Vec3 originOffset,     // 发射点偏移 (相对caster)
    boolean aimAtTarget,             // 是否朝向目标
    @Nullable HomingConfig homing,   // 追踪配置 (null=不追踪)
    @Nullable MoverConfig mover      // 自定义运动 (null=直线飞行)
) implements SpellAction
```

**`FireLaser`** — 发射激光

```java
record FireLaser(
    YHDanmaku.Laser laserType,       // LASER / PENCIL
    DyeColor color,
    NumberProvider lifetime,
    NumberProvider length,
    NumberProvider angleOffset,
    boolean aimAtTarget,
    @Nullable Vec3 originOffset,
    @Nullable MoverConfig mover
) implements SpellAction
```

**`SpawnShooter`** — 生成子发射器

```java
record SpawnShooter(
    ShooterData data,                // health, damage, lifetime, spell circle
    SpellCard subSpell,              // 子发射器执行的符卡（或引用SpellDefinition）
    @Nullable Vec3 spawnOffset,
    @Nullable MoverConfig mover
) implements SpellAction
```

#### 6.2 辅助数据结构

**`PatternType`** — 弹幕排列模式

```java
enum PatternType {
    RING,      // 环形均匀分布
    LINE,      // 直线（前方扇形）
    RANDOM,    // 随机方向
    AIMED      // 全部朝向目标
}
```

**`HomingConfig`** — 追踪配置

```java
record HomingConfig(
    double strength,      // 追踪强度 (0=不追踪, 1=强追踪)
    int delay,            // 开始追踪的延迟 tick
    int duration          // 追踪持续时间 (-1=永久)
)
```

**`MoverConfig`** — 运动配置（Codec 化的 DanmakuMover 参数）

```java
// 将现有 DanmakuMover 子类包装为可序列化配置
// RectMover -> MoverConfig.rect(acceleration)
// PolarMover -> MoverConfig.polar(angularVelocity, radialAcceleration)
// RotateMover -> MoverConfig.rotate(degreesPerTick)
```

#### 6.3 FireDanmaku 执行逻辑 ✅ 已完成

```java
public void execute(SpellContext ctx) {
    CardHolder holder = ctx.holder();
    DifficultyModifiers diff = ctx.difficulty();
    int n = diff.adjustCount((int) count.get(ctx));
    double spd = diff.adjustSpeed(speed.get(ctx));
    int life = (int) lifetime.get(ctx);
    double angle = angleOffset.get(ctx);
    double spreadDeg = spread.get(ctx);

    Vec3 origin = holder.center();
    if (originOffset != null) origin = origin.add(originOffset);
    Vec3 baseDir = aimAtTarget ? holder.forward() : new Vec3(0, 0, 1);
    Orientation ori = DanmakuHelper.getOrientation(baseDir);

    for (int i = 0; i < n; i++) {
        double a = angle;
        switch (pattern) {
            case RING -> a += (360.0 / n) * i;
            case LINE -> a += spreadDeg * (i - (n - 1) / 2.0) / Math.max(n - 1, 1);
            case RANDOM -> a += holder.random().nextDouble() * spreadDeg - spreadDeg / 2;
            case AIMED -> {} // all same direction
        }
        Vec3 dir = ori.rotateDegrees(a).scale(spd);
        var danmaku = holder.prepareDanmaku(life, dir, bulletType, color);
        if (homing != null) {
            danmaku.mover = createHomingMover(holder, danmaku, homing);
        } else if (mover != null) {
            danmaku.mover = mover.create(origin, dir);
        }
        holder.shoot(danmaku);
    }
}
```

#### 6.4 PropertyPanel — 实时编辑面板 ✅ 已完成 (ActionEditorPanel + ActionListPanel)

嵌入 `SpellPreviewScreen` 右侧的属性编辑面板。选中一个 Phase 的 Action 后，显示对应字段的编辑 Widget。

**编辑项与 Widget 类型映射**：

| 字段 | Widget | 行为 |
|------|--------|------|
| `bulletType` | `DropdownWidget<Bullet>` + 图标预览 | 14种弹幕类型 |
| `color` | 16色网格选择器 | DyeColor 枚举 |
| `count` | `NumberProviderWidget` | 常量/随机/曲线切换 |
| `speed` | `NumberProviderWidget` | 同上 |
| `lifetime` | `NumberProviderWidget` | 同上 |
| `angleOffset` | `NumberProviderWidget` + 角度可视化 | 角度盘 |
| `spread` | `NumberProviderWidget` | 扩散角度 |
| `pattern` | `DropdownWidget<PatternType>` | RING/LINE/RANDOM/AIMED |
| `originOffset` | 3x `NumberField` 或视口拖拽 | Vec3 坐标 |
| `aimAtTarget` | `BooleanToggle` | 开关 |
| `homing` | `BooleanToggle` + 展开子面板 | strength/delay/duration |

**交互流程**：
1. 修改任意字段 → 自动更新 `workingDefinition` 中对应 Action
2. 点击 Preview 区的 Reset → `scene.reset()` + 重新播放
3. 可选：`Auto-Reset` 模式 — 每次参数变更自动 reset + play

**NumberProviderWidget** 交互：
```
[▼ Constant ▼] [12.0    ]     ← 固定值模式
[▼ Random   ▼] [0.6] ~ [1.0]  ← 随机范围模式
[▼ By HP    ▼] [0.8] → [1.6]  ← 血量插值模式
[▼ Lerp     ▼] [0.5] → [1.5] / [200]t  ← 时间插值
```

#### 6.5 实施步骤

| 步骤 | 内容 | 状态 | 产出 |
|------|------|------|------|
| **6.0** | NumberProvider 接口 + 6种实现 + Codec | ✅ | `NumberProvider`, `NumberProviders` (Constant, RandomRange, LerpOverTime, ByHealthRatio, PhaseTickMod, Variable) |
| **6.1** | FireDanmaku Action | ✅ | 环形/直线/随机/瞄准发射, 可序列化 |
| **6.2** | FireLaser Action | ✅ | 激光发射 |
| **6.3** | MoverConfig (Acceleration, Rotate) | ✅ | Mover 可序列化 |
| **6.4** | SpawnShooter Action | 🔲 | 子发射器 (需 sub-spell 序列化) |
| **6.5** | PreviewCardHolder 追踪/子弹幕修复 | ✅ | FakeCasterEntity 实现 CardHolder, ShooterEntity aiStep() override |
| **6.6** | ActionEditorPanel 基础框架 | ✅ | 编辑面板, 支持 FireDanmaku/FireLaser/Conditional/Variable/Sound/Phase |
| **6.7** | NumberProviderWidget (简化为 EditBox) | ✅ | 常量值编辑, 非常量显示 `*` 标记 |
| **6.8** | ActionListPanel 嵌入预览 | ✅ | 递归树形显示, 支持任意深度 ConditionalAction 嵌套 |
| **6.9** | Preview + Editor 联动 | ✅ | 编辑参数 → reset → 即时预览; Apply 按钮更新实体 |
| **6.10** | 测试符卡 test_fire_danmaku | ✅ | TickInterval 条件, 双环弹幕验证 |
| **6.11** | JSON 导出/导入 | ✅ | Export 按钮 + `/yhspell export` + `/yhspell import` 命令 |

#### 6.6-6.11 Bug修复记录 (2026-04-03)

- **ActionListPanel 条件嵌套**: ActionPath 从 `(section, index, branch, childIndex)` 改为 `List<PathEntry>` 列表式路径, 支持任意深度递归
- **ActionEditorPanel 滚动修复**: `layoutWidgets()` 不再每次滚动重复添加 widget, 使用 `widgetsRegistered` 标记
- **ShooterEntity 预览修复**: 匿名子类 override `aiStep()` 在客户端强制调用 `serverAiStep()`; 移除 `tickShooter()` 中多余的手动位置更新
- **Apply 按钮修复**: 同时匹配 `spellRuntime.id` 和 `spellCard.modelId`; 调用 `SpellRegistry.register()` 更新内存注册表
- **reapply 命令修复**: 同 Apply 按钮逻辑

#### 6.6 文件结构

```
content/spell/
  definition/
    NumberProvider.java          # 新增: 接口
    NumberProviders.java         # 新增: 5种实现
    PatternType.java             # 新增: 枚举
    HomingConfig.java            # 新增: 追踪配置
    MoverConfig.java             # 新增: 运动配置 (Codec化)
  action/
    FireDanmakuAction.java       # 新增: 发射弹幕
    FireLaserAction.java         # 新增: 发射激光
    SpawnShooterAction.java      # 新增: 生成子发射器
  editor/                        # 新增: 编辑器
    PropertyPanel.java           # 属性面板
    widget/
      NumberField.java           # 数字输入
      NumberProviderWidget.java  # 数值源编辑器
      DropdownWidget.java        # 枚举下拉
      BooleanToggle.java         # 开关
      ColorPickerWidget.java     # 16色选择器
      ActionListEditor.java      # Action列表编辑
```

### Phase 6-fix: 代码审查修复 + 深化准备 ✅ 已完成 (2026-04-03)

审查文档见 `code-review-phase0-6.md`，深化计划见 `plan-editor-danmaku-deepening.md`。

**已修复问题 (7项)**:
1. ✅ DYE_COLOR_CODEC / VEC3_CODEC 重复定义 → 统一使用 SpellCodecs
2. ✅ SpellPreviewScreen 硬编码键码 → GLFW 常量
3. ✅ FireLaserAction 缺少 originOffset/mover → 已添加，与 FireDanmakuAction 对齐
4. ✅ ActionEditorPanel 11参数复制粘贴 → withXxx 辅助方法
5. ✅ ActionListPanel.buildRows() 每帧重建 → dirty-flag 触发
6. ✅ instanceof 硬编码链 → SpellActions.getTypeId() / SpellConditions.getTypeId()
7. ✅ actionTypeName/getConditionType 手写 → 查表

**遗留低优先 (2项)**:
- 🔲 LegacyTickerAction 反序列化静默失败 (设计如此，加 warning 即可)
- 🔲 NumberProvider 编辑降级为 Constant (Phase 6.5 解决)

### Phase 6.5: 弹幕深化核心 (P0) ✅ 已完成

目标：让编辑器能从零制作接近 AI-step 符卡效果的弹幕。详细设计见 `plan-editor-danmaku-deepening.md`。

**已完成 (2026-04-03):**
- 6.5.0 数学 NumberProvider: PhaseTick, TotalTick, Sin, Cos, Add, Mul
- 6.5.1 OriginConfig: 替换 Optional<Vec3> originOffset → OriginConfig (CASTER/TARGET/ABSOLUTE/CASTER_FACING + NumberProvider xyz + rotation)
- 6.5.2 AimMode: 替换 boolean aimAtTarget → AimMode 接口 (Target/FixedDirection/CasterFacing/AngleOffset/VariableAngle)
- 6.5.3 MoverConfig 扩展: PolarMoverConfig, CompositeMoverConfig, ZeroMoverConfig
- 6.5.4 RepeatAction: 单 tick 内循环 (count + indexVariable + body)
- 6.5.5 onExpiry: DataDrivenTrailAction + TrailCardHolder 桥接底层 TrailAction
- 6.5.6 FireDanmakuAction/FireLaserAction 签名更新: OriginConfig, AimMode, onExpiry 字段
- 6.5.7 `/yhspell new <id>`: 从零创建空白符卡并打开编辑器

### Phase 6.5-fix: Import Bug + 编辑器可用性 + 缺失能力 ✅ 已完成

#### A. Import NPE 修复

**根因**: `SpellDisplay`/`SpellItemForm` 的 `@Nullable ResourceLocation` 字段通过 `xmap(o -> o.orElse(null), Optional::ofNullable)` 注入 null 到 DFU 的 RecordCodecBuilder 管线，触发 `Optional.of(null)` → NPE。

**修复**: 改 `@Nullable ResourceLocation` 为 `Optional<ResourceLocation>`，移除 xmap null 转换。

#### B. 编辑器 Mover/Origin/onExpiry 配置

| 功能 | 说明 | 状态 |
|------|------|------|
| Mover 类型选择 | cycle: none/acceleration/rotate/polar/zero + 类型特定参数行 | ✅ |
| OriginConfig 偏移编辑 | offsetX/Y/Z + rotation 的 NumberProvider 编辑行 | ✅ |
| onExpiry 指示器 | 只读显示 "[onExpiry: N actions]"，完整编辑留后续阶段 | ✅ |
| **Mover 参数 EditBox 焦点** | **修复: 参数值变更时不再触发 rebuild，保留 EditBox 焦点** | ✅ |
| **Mover/Origin 参数闭包过期** | **修复: 闭包从 currentAction 读取最新值，而非构建时快照** | ✅ |

#### C. 编辑器 Ctrl+C/X/V 和拖拽移动

- ✅ Ctrl+C: 通过 Codec 序列化/反序列化深拷贝选中 action
- ✅ Ctrl+X: 拷贝 + 删除
- ✅ Ctrl+V: 在选中位置后粘贴
- ✅ Ctrl+Up/Down: 在列表内上下移动 action
- ✅ **鼠标拖拽移动**: 拖拽 action 行可在同一 section 内重排序 (带黄色指示线)
- ✅ **拖拽进入 Conditional/Repeat**: 拖拽到 `+ if_true` / `+ if_false` / `+ body` 行可插入子分支 (绿色高亮)
- ✅ **嵌套 action 拖出**: 嵌套 action 可拖到顶层间隙提升为顶层 action

#### D. FireLaserAction setupTime + Mover

- 添加 setupStart/setupPeak/setupSustain/setupEnd 参数 (激光膨胀/收缩时间)
- 在 execute() 中应用 mover

#### 6.5.1 OriginConfig — 动态发射位置

替换 `FireDanmakuAction.originOffset: Optional<Vec3>` 和 `FireLaserAction.originOffset: Optional<Vec3>` 为：

```java
record OriginConfig(
    OriginMode mode,           // CASTER / TARGET / ABSOLUTE / CASTER_FACING / ORBIT
    NumberProvider offsetX,
    NumberProvider offsetY,
    NumberProvider offsetZ,
    NumberProvider rotation     // 偏移向量绕 Y 轴旋转角度 (度)
)
```

**改动文件**: `FireDanmakuAction.java`, `FireLaserAction.java`, `ActionEditorPanel.java`, 新增 `OriginConfig.java`

#### 6.5.2 onExpiry — 弹幕到期触发子弹幕

`FireDanmakuAction` 新增 `Optional<List<SpellAction>> onExpiry` 字段。桥接到底层 `ItemDanmakuEntity.afterExpiry` (TrailAction)。

关键设计：onExpiry 中的 action 需要 `TrailSpellContext`，将弹幕消失位置/方向注入为 `holder.center()`/`holder.forward()`。

**改动文件**: `FireDanmakuAction.java`, 新增 `TrailSpellContext.java`, `ActionEditorPanel.java`

#### 6.5.3 PolarMoverConfig — 极坐标运动

Codec 化已存在的 `PolarMover`:

```java
record PolarMoverConfig(
    NumberProvider radius, NumberProvider radialSpeed, NumberProvider radialAccel,
    NumberProvider initialAngle, NumberProvider angularSpeed, NumberProvider angularAccel
) implements MoverConfig
```

**改动文件**: `MoverConfigs.java`

#### 6.5.4 编辑器 UI 适配

- `ActionEditorPanel`: 添加 OriginConfig 编辑行、onExpiry 子面板入口
- `NumberProviderWidget` 升级: 类型下拉 (Constant/Random/LerpTime/ByHealth/Variable) + 对应参数

### Phase 6.6: 弹幕深化扩展 (P1) ✅ 大部分已完成

1. ✅ CompositeMoverConfig (分段运动: 匀速 → 停顿 → 再加速)
2. 🔲 HomingMoverConfig (追踪弹) — 暂不需要
3. ✅ AimMode 替换 `boolean aimAtTarget` (TARGET/FIXED/CASTER_FACING/ANGLE_OFFSET/VARIABLE_ANGLE/DIRECTION_TO_TARGET/RANDOM_ANGLE)
4. ✅ 数学 NumberProvider (Sin/Cos/Mul/Add/PhaseTick/TotalTick) + 表达式解析器 NumberExprParser
5. ✅ RepeatAction (同一 tick 内循环, 支持嵌套环形发射)
6. ✅ 编辑器面板适配

### Phase 6.7: 迁移准备功能 ✅ 已完成 (2026-04-04)

以下功能为迁移 legacy 符卡到可视化编辑器所需的补充：

1. ✅ **DelayAction**: 延迟执行动作，在 SpellRuntime 中加入调度队列
   - `{"type": "delay", "delay_ticks": 20, "body": [...]}`
   - SpellRuntime 新增 `scheduledActions` 队列 + `scheduleDelayed()` 方法
2. ✅ **TeleportAction**: 传送施法者到指定位置 (碰撞检测 + 事件广播 + 可选音效)
   - `{"type": "teleport", "destination": {...}, "play_sound": true}`
   - 复用 OriginConfig 作为目标位置系统
3. ✅ **on_damage Phase Hook**: PhaseDefinition 新增 `on_damage` 动作列表
   - SpellRuntime.hurt() 中触发 on_damage 动作
   - ActionListPanel 显示 onDamage section
   - KubeJS PhaseBuilderJS 同步更新
4. ✅ **GRID/SPHERE/SPIRAL PatternType**: 2D/3D 排列模式
   - GRID: rows × cols 网格排列
   - SPHERE: latitude × longitude 球面分布
   - SPIRAL: 螺旋排列
5. ✅ **RandomAngle AimMode**: 每次调用随机角度 `{"type": "random_angle", "spread": 360}`
6. ✅ **编辑器面板全面更新**: 类型选择器、属性编辑、分支管理、显示标签

**未实现 (P2，仅少数符卡需要)**:
- 🔲 Per-Action DamageSource: fire_danmaku/fire_laser 单独指定弹幕伤害类型
- 🔲 SpawnShooterAction: 子发射器/子符卡嵌套
- 🔲 HomingMoverConfig: 追踪弹

### Phase 6.8: onTrail — 弹幕飞行中持续生成子弹幕 ✅ 已完成 (2026-04-04)

1. ✅ `ItemDanmakuEntity.onTrail`: 新增 per-tick trail hook (在 updateVelocity 中触发)
2. ✅ `FireDanmakuAction.onTrail` + `trailInterval`: 数据驱动的 onTrail 子弹幕
3. ✅ `FireDanmakuAction.elevation`: 仰角参数，RANDOM 模式支持锥形扩散
4. ✅ 编辑器面板: onTrail 分支完整支持 (显示/插入/删除/替换/拖拽)

### Phase 7: 迁移现有符卡 — 部分完成

**已迁移** (通过 `MigratedSpellCards.java`):

| 符卡 | 复杂度 | 关键功能 |
|------|--------|---------|
| SunnySpell | 简单 | conditional + tick_interval 三色循环 |
| LunaSpell | 简单 | AND/NOT 条件组合 4/6 间歇 |
| StarSpell | 简单 | onTrail + elevation 锥形扩散 |
| CirnoSpell | 中等 | onExpiry + direction_to_target 分裂追踪 |

**保留 legacy** (需要更多底层支持):

| 符卡 | 阻塞原因 |
|------|---------|
| LarvaSpell | Ticker 曲线动画 + 目标状态判断 |
| MystiaSpell | ShooterData 子发射器 |
| DoremiSpell | 有状态 Ticker + CompositeMover + Laser |
| RemiliaSpell | Ticker + teleport + ray casting |
| 其余 Boss 符卡 | 类似: Ticker / CompositeMover / 自适应逻辑 |

---

## 文件结构规划

```
content/spell/
  spellcard/          # 保留: SpellCard, ActualSpellCard, Ticker, CardHolder 等底层
  definition/         # 新增: SpellDefinition, PhaseDefinition, SpellDisplay, SpellItemForm
                      #       NumberProvider, NumberProviders, PatternType, HomingConfig, MoverConfig
  runtime/            # 新增: SpellRuntime, SpellContext, SpellRegistry
  condition/          # 新增: SpellCondition 及所有条件实现
  action/             # 新增: SpellAction 及所有动作实现
                      #       FireDanmakuAction, FireLaserAction, SpawnShooterAction
  difficulty/         # 新增: DifficultyProfile, DifficultyModifiers
  bridge/             # 新增: LegacySpellBridge, LegacyTickerAction
  registry/           # 新增: SpellItemAutoRegister
  preview/            # 新增(Phase5): 正交预览系统
                      #   ViewAngle, PreviewCardHolder, VirtualSpellScene
                      #   OrthographicViewport, SpellPreviewScreen
  editor/             # 新增(Phase6): 编辑器框架
                      #   PropertyPanel, widget/NumberField, NumberProviderWidget
                      #   DropdownWidget, BooleanToggle, ColorPickerWidget, ActionListEditor
  game/               # 保留: 现有符卡实现，逐步迁移
  mover/              # 保留: 不动
  shooter/            # 保留: 不动
  item/               # 保留+新增: ItemSpell, PlayerHolder, RuntimeItemSpell

content/item/danmaku/
  DynamicSpellItem.java  # 新增: 通用符卡物品

content/entity/youkai/
  SpellStateToClient.java  # 新增

compat/kubejs/spell/       # 新增: KubeJS集成
  YHSpellKubeJSPlugin.java
  YHSpellKubeJSEvents.java
  RegisterSpellsEventJS.java
  SpellDefinitionBuilderJS.java
  PhaseBuilderJS.java
  KubeJSSpellActions.java
  KubeJSSpellConditions.java

events/
  YHCommands.java      # 新增: /yhspell 指令
```

---

## 与GPT方案的关键差异

| 问题 | GPT方案 | 本方案 |
|------|---------|--------|
| 整体策略 | 大爆炸式重写五层 | 渐进式，每Phase可编译可测试 |
| 注册机制 | 自定义Map + 启动遍历 | Forge DeferredRegister + Codec |
| 物品注册时序 | 未解决 | 分Java静态注册/运行时动态物品两层 |
| StagedSpellCard | 保留兼容 | 直接删除(vibecoding产物，有bug) |
| 条件/动作 | 完整DSL语言 | 接口+Codec多态，简单直接 |
| 编辑器 | 一步到位图编辑器 | 三阶段渐进(列表->节点图->调试) |
| 旧符卡迁移 | 全量迁移 | 桥接兼容，按需迁移 |
| KJS | 注册定义/条件/动作/监听/覆盖 | 聚焦注册定义+回调，不做完整SDK |
| 并行拆分 | 5角色并行 | 线性Phase，一人也能推进 |

---

## 风险与缓解

1. **Forge Registry自定义类型**: 需要确认1.20.1 Forge的自定义Registry API。如果过于复杂，可降级为`HashMap<ResourceLocation, SpellDefinition>` + 手动Codec。
2. **序列化兼容性**: `SpellRuntime`存储在实体NBT中，需要版本号和迁移逻辑。
3. **性能**: 条件每tick评估。内置条件都是O(1)操作，但需注意KJS回调条件的开销。
4. **客户端同步频率**: `SpellStateToClient`只在阶段切换时发送，不是每tick，避免带宽问题。
