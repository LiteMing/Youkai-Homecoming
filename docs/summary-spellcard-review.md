# 符卡与弹幕系统审查总结

## 审查范围

- Boss 符卡运行时：`content/spell/spellcard`、`content/spell/game`
- Boss 战斗与同步：`content/entity/youkai`、`content/entity/boss`
- 弹幕实体与客户端同步：`content/entity/danmaku`、`fastprojectileapi/render/virtual`
- 玩家自定义符卡：`content/spell/custom`、`content/spell/item`
- 符卡物品注册：`init/registrate/YHDanmaku`

## 结论

当前系统已经具备不错的底层弹幕能力：

- 虚拟弹幕服务端判定 + 客户端副本渲染
- 可复用的 mover / trail / shooter 组件
- 玩家自定义符卡的基础 record 编辑器

但“符卡编排层”还很弱，距离你要的五个目标还有明显结构缺口。

## 主要发现

### 1. 现有衔接与循环能力非常有限

- `ListSpellCard` 只支持按 `hit` 阈值在线性列表中切换，并在末尾回到开头。
- `StagedSpellCard` 只支持“弹幕战斗中 + 均匀血量分段”。
- 其他大多数符卡仍是各自类里手写 `tick()` 分支。

这意味着当前无法自然支持：

- 条件分支
- 任意循环
- 脚本/指令强制跳转
- 外部事件驱动的阶段切换

### 2. 符卡定义和物品定义是分裂的

- `TouhouSpellCards` 只保存 `id -> Supplier<SpellCard>`。
- `YHDanmaku` 另起一套手写 `SpellItem` 注册。

结果是：

- 无法自动根据符卡定义生成物品
- 增加一张卡需要重复维护多处
- 编辑器和脚本层也拿不到统一元数据

### 3. 现有可视化编辑器不是“符卡逻辑编辑器”

- `EditorScreen` + `SpellOptionInstances` 的本质是 record 字段表单。
- `ISpellFormData#createInstance()` 直接产出 `ItemSpell`。
- `ServerCustomSpellHandler` 只是把表单结果写回物品 NBT 的 `SpellData`。

它适合调一个发射模板，不适合编辑完整 Boss 符卡流程图。

### 4. 客户端并不知道当前阶段

- `CombatProgress` 只同步血量进度。
- 客户端只更新 `e.combatProgress`。
- `GeneralYoukaiEntity` 同步的是 `modelId`，不是完整阶段状态。

因此客户端无法可靠确认：

- 当前是否处于弹幕战斗
- 当前符卡 ID
- 当前阶段 ID
- 是否刚发生阶段切换

### 5. 动态难度能力没有统一入口

- 不少卡已经会按血量、距离、速度写出不同弹幕。
- 但这些判断都散落在具体类中，例如 `MarisaSpell` 直接在 `tick()` 里手写 phase1/2/3。

这会导致：

- 无法统一调难度
- 无法脚本层复用
- 无法在编辑器里稳定表达

### 6. 已存在的具体缺陷

- `TargetTracker.vel()` 现在返回的是 `t2.subtract(t2)`，永远是零向量。
- `ActualSpellCard.reset()` 只清 `tick/hit/tickers`，不会重置子类自己的阶段/冷却字段。
- `StagedSpellCard` 没有覆盖 `reset()`，`currentStage` 会跨重置保留。

这些问题会直接影响阶段行为和目标预测。

## 可保留的部分

本次重构不需要推倒以下部分：

- `DanmakuMover` 及其数学/轨迹层
- `TrailAction`
- `CardHolder`
- Boss 虚拟弹幕服务端判定 + 客户端缓存渲染
- 现有注解驱动的属性编辑机制

更合理的做法是保留这些底层能力，在其上方重建“统一符卡定义层 + 阶段图运行时 + 网络状态层 + 编辑器模型”。

## 下一步

已补充重构方案文档：`docs/plan-spellcard-rework.md`。  
建议按“先定义层、再状态同步、再自动物品、最后可视化编辑器”的顺序推进，不要直接在现有每张符卡类上继续堆条件分支。
