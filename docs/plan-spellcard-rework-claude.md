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

### Phase 2: 指令与数据化 ✅ 部分完成

1. ✅ 实现`/yhspell`指令 (set, phase, variable, reset, debug, list)
2. ✅ 实现条件/动作的Codec序列化
3. 🔲 支持从datapack JSON加载SpellDefinition (Codec已就绪，需要DatapackRegistry加载器)
4. 🔲 实现NBT覆盖层

### Phase 3: KubeJS集成

1. 注册KJS事件
2. 实现JS侧的builder API
3. 支持JS回调条件和动作

### Phase 4: 物品自动注册

1. 实现`SpellItemAutoRegister`
2. 实现`DynamicSpellItem`
3. 从`YHDanmaku`迁移手写SpellItem
4. 保持物品ID向后兼容

### Phase 5: 编辑器

1. Phase 1列表编辑器
2. Phase 2节点图
3. Phase 3调试预览

### Phase 6: 迁移现有符卡

按复杂度排序：
1. 简单符卡(SunnySpell, LunaSpell, StarSpell, LarvaSpell) - 基本是单阶段
2. 中等符卡(CirnoSpell, MystiaSpell, DoremiSpell) - 有简单阶段逻辑
3. 复杂符卡(MarisaSpell, KoishiSpell, ReimuSpell, YukariSpell) - 重度手写分支

---

## 文件结构规划

```
content/spell/
  spellcard/          # 保留: SpellCard, ActualSpellCard, Ticker, CardHolder 等底层
  definition/         # 新增: SpellDefinition, PhaseDefinition, SpellDisplay, SpellItemForm
  runtime/            # 新增: SpellRuntime, SpellContext, SpellRuntimeState
  condition/          # 新增: SpellCondition 及所有条件实现
  action/             # 新增: SpellAction 及所有动作实现
  difficulty/         # 新增: DifficultyProfile, DifficultyModifiers, FloatCurve
  bridge/             # 新增: LegacySpellBridge, LegacyTickerAction
  registry/           # 新增: SpellRegistry, SpellItemAutoRegister
  game/               # 保留: 现有符卡实现，逐步迁移
  mover/              # 保留: 不动
  shooter/            # 保留: 不动
  item/               # 保留: ItemSpell, PlayerHolder 等

content/entity/youkai/
  SpellStateToClient.java  # 新增

init/registrate/
  YHSpells.java        # 新增: DeferredRegister for SpellDefinition

events/
  YHSpellCommands.java # 新增: /yhspell 指令
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
