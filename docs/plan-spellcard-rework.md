# 符卡与弹幕系统重构计划

## 目标

围绕以下五个目标重构当前系统：

1. 支持在游戏内通过指令、NBT、KubeJS 脚本自定义符卡衔接、分支与循环逻辑。
2. 自动为符卡注册“物品形式”，避免符卡实现与物品注册重复维护。
3. 提供可视化的符卡逻辑编辑能力，而不只是数值表单编辑。
4. 支持依据血量、阶段、战斗状态动态调整弹幕难度与样式。
5. 明确同步客户端所需的弹幕战斗状态、符卡阶段与当前符卡定义。

## 当前结构概览

### 1. Boss 符卡运行时

- `SpellCard` / `ActualSpellCard` / `Ticker` 构成最小运行时。
- `SpellCardWrapper` 挂在 `YoukaiEntity.spellCard` 上，`modelId` 同时承担“外观 ID”和“符卡 ID”的职责。
- `ListSpellCard` 仅支持按 `hit` 阈值线性切换并循环。
- 大部分符卡仍是各自 `tick()` 内的手写流程，阶段、弹幕量、速度、颜色都散落在具体类中。
- `StagedSpellCard` 是新的尝试，但阶段来源固定为“弹幕战斗中 + 均匀血量分段”。

### 2. Boss 弹幕运行时

- `YoukaiEntity` 在服务端缓存 `SimplifiedProjectile`，不把 Boss 弹幕直接放进世界实体列表。
- `tickDanmaku()` 负责服务端命中逻辑，`DanmakuManager.send()` 负责把新增弹幕同步给客户端。
- 客户端通过 `DanmakuToClientPacket` + `ClientDanmakuCache` 建立纯客户端弹幕副本并渲染。

### 3. 玩家自定义符卡

- `CustomSpellItem` 从物品 NBT `SpellData` 读取 `ISpellFormData`。
- `ISpellFormData#createInstance()` 直接产出 `ItemSpell`。
- `EditorScreen` + `SpellOptionInstances` 只负责对 record 字段做表单编辑。
- 现有“自定义”对象是玩家道具符卡，不是 Boss 符卡编排图。

### 4. 同步与战斗状态

- `CombatProgress` 只同步 `maxProgress/progress`。
- 客户端只知道 Boss 当前“血条进度”，不知道：
  - 当前符卡定义 ID
  - 当前阶段 ID
  - 是否处于弹幕战斗
  - 当前阶段切换原因

### 5. 物品注册

- `TouhouSpellCards` 维护符卡注册表。
- `YHDanmaku` 手动维护每个 `SpellItem` / `CustomSpellItem`。
- 符卡定义与其物品、消耗、图标、文本分散在两处系统里。

## 主要问题

### 1. 编排模型过弱

当前只有三种方式：

- 单类内手写 `tick()`
- `ListSpellCard` 的线性列表循环
- `StagedSpellCard` 的固定血量分段

这不足以表达：

- 条件跳转
- 多分支阶段衔接
- 局部循环和全局循环
- 外部事件触发切阶段
- 指令/NBT/KJS 动态改写当前流程

### 2. 符卡定义没有“元数据层”

缺少统一的 `SpellDefinition` 概念，导致：

- 不能自动生成物品
- 不能给编辑器提供结构信息
- 不能给网络层同步标准化阶段状态
- 不能从数据包或脚本注册

### 3. 编辑器和运行时模型不一致

现有编辑器面向“record 字段 -> 选项表单”，适合调单个发射形态参数，不适合编辑：

- 阶段图
- 条件节点
- 循环节点
- 事件节点
- 预览时间线

### 4. 动态难度逻辑分散

血量判定、距离判定、速度缩放、弹种切换都写在具体符卡类里，缺少统一上下文：

- 无法全局调难度
- 无法在脚本层复用
- 无法用 UI 直接配置
- 无法给客户端稳定展示当前阶段配置

### 5. 客户端状态认知不足

客户端只知道 Boss 血量和弹幕实体流，不知道“当前在打哪张卡、哪一阶段、是否刚切阶段”，因此：

- HUD 难以准确显示
- 阶段提示和演出难以做
- 编辑器/调试器难以联动
- KJS/指令调试缺乏观测点

## 重构方案

## 一、引入统一的符卡定义层

新增统一定义对象，建议至少拆成以下几层：

- `SpellDefinition`
  - `id`
  - `display`
  - `itemForm`
  - `entryPhase`
  - `phases`
  - `defaultContext`
- `SpellDisplay`
  - 名称、描述、图标、模型、BossBar 文本
- `SpellItemForm`
  - 是否生成物品
  - 物品 ID、贴图、消耗、冷却、是否需要目标
- `SpellPhaseDefinition`
  - `phaseId`
  - `enterActions`
  - `tickActions`
  - `exitActions`
  - `transitions`
- `SpellTransition`
  - 目标阶段
  - 触发条件
  - 是否循环
  - 切换策略（立即、延迟、清场后）

数据来源分三类：

- Java 内置定义
- Datapack/JSON Codec 定义
- KubeJS 注册定义

命令和 NBT 只负责引用/切换定义，不直接承载复杂流程。

## 二、把运行时改成“阶段图状态机”

新增 `SpellRuntimeState`，由实体持有：

- `spellId`
- `phaseId`
- `phaseTick`
- `loopCounters`
- `variables`
- `battleState`

新增统一上下文 `SpellContext`：

- `self`
- `target`
- `combatProgress`
- `danmakuSession`
- `difficulty`
- `localVariables`
- `server/client side`

阶段切换不再写死在 `tick()` 里，而是：

1. 进入阶段，执行 enter actions。
2. 每 tick 执行 phase actions。
3. 评估 transitions。
4. 命中条件则切换到目标阶段。

这样即可自然支持：

- 线性流程
- 条件分支
- 子循环
- 无限循环
- 外部事件跳转
- 脚本强制切阶段

## 三、建立统一条件与动作 DSL

建议把可配置能力拆成：

- `SpellCondition`
  - 血量区间
  - 当前阶段 tick
  - 距离区间
  - 命中次数
  - Graze/玩家状态
  - 变量值
  - KJS 回调条件
- `SpellAction`
  - 发射弹幕
  - 发射激光
  - 设置变量
  - 等待
  - 清屏
  - 切阶段
  - 修改移动器
  - 播放演出/SFX
  - 调整客户端提示

现有可复用组件：

- `DanmakuMover`
- `TrailAction`
- `ShooterData`
- `CardHolder`
- `DanmakuHelper`

建议保留这些底层组件，不重写弹幕数学层，只重写符卡编排层。

## 四、自动化符卡物品注册

让 `SpellDefinition` 作为唯一来源生成物品信息：

- 内置符卡定义时可声明 `itemForm`
- 数据包/KJS 注册时也可声明 `itemForm`
- 启动时统一遍历定义表生成 `SpellItem` 或 `CustomSpellItem` 派生实例

建议引入：

- `SpellItemFactory`
- `SpellRegistry`
- `SpellItemMetadata`

自动注册后，`YHDanmaku` 不再手写每张符卡物品，只保留基础弹幕/激光物品族。

## 五、编辑器升级为“图编辑 + 属性面板”

现有 `SpellOptionInstances` 可以继续复用为“属性面板生成器”，但不能继续充当完整编辑器模型。

建议拆成：

- 图模型
  - 节点：阶段、条件、循环、动作组
  - 边：阶段转移、条件流转
- 属性面板
  - 继续利用注解驱动字段编辑
- 预览面板
  - 当前阶段
  - tick 时间线
  - 弹幕统计
  - 关键事件日志

第一期可以先做“节点列表 + 属性编辑 + 简化连线”，不必一开始做复杂拖拽画布。

## 六、难度与样式动态调整统一化

新增 `DifficultyProfile` / `StyleResolver`：

- 输入
  - Boss 当前血量比例
  - 当前阶段
  - 玩家表现
  - Graze 战斗状态
  - 世界难度/配置
- 输出
  - 发射频率倍率
  - 速度倍率
  - 数量倍率
  - 弹种替换
  - 颜色策略

所有 `SpellAction` 在执行发射前统一经过 resolver，而不是在每个符卡类里到处手写 `if (hp < 0.5)`。

## 七、客户端同步扩展

新增 `SpellStateToClient`，同步：

- `entityId`
- `spellId`
- `phaseId`
- `phaseTick`
- `battleState`
- `transitionReason`

这样客户端可稳定实现：

- HUD 当前符卡名/阶段名
- 阶段切换演出
- 调试面板
- 编辑器联机预览

`CombatToClient` 保留为基础血条同步，但不再承担完整战斗状态表达。

## 八、指令 / NBT / KubeJS 接入面

### 指令

新增建议：

- `/danmaku spell set <entity> <spell_id>`
- `/danmaku spell phase <entity> <phase_id>`
- `/danmaku spell reload`
- `/danmaku spell debug <entity>`

### NBT

通过实体或物品 NBT 挂接：

- `SpellId`
- `SpellVariables`
- `SpellOverrides`

NBT 只作为覆盖层，不直接塞复杂流程树。

### KubeJS

暴露：

- 注册符卡定义
- 注册条件
- 注册动作
- 监听阶段切换
- 动态覆盖难度 profile

## 实施阶段

### Phase 0：现状稳固

- 修复 `TargetTracker.vel()` 恒为零的问题。
- 把 `spellId` 与 `modelId` 解耦。
- 给现有阶段化实现补充可重置的运行时状态。
- 为客户端补一条“当前符卡/阶段”同步通路。

### Phase 1：新运行时骨架

- 引入 `SpellDefinition`、`SpellPhaseDefinition`、`SpellRuntimeState`。
- 做 Java 内置定义适配层。
- 让旧 `ActualSpellCard` 可以包成新定义的兼容节点。

### Phase 2：数据化与脚本化

- 做 Codec + datapack registry。
- 接入 KubeJS API。
- 提供命令层控制。

### Phase 3：物品注册自动化

- 基于 `SpellDefinition.itemForm` 自动生成物品。
- 旧 `YHDanmaku` 里的手写 `SpellItem` 迁移到定义表。

### Phase 4：编辑器升级

- 先做阶段列表 + 属性编辑。
- 再做图连接与时间线预览。
- 最后做联机调试和导入导出。

### Phase 5：迁移现有符卡

- 先迁移 `SakuyaSpell`、`KisinSpell` 这类已经阶段化的卡。
- 再迁移 `MarisaSpell`、`ReimuSpell` 这类手写分支逻辑较重的卡。
- 最后迁移玩家可编辑符卡，统一到底层 action/condition 模型。

## 并行拆分建议

### planner

- 固化 `SpellDefinition`、`SpellPhaseDefinition`、`SpellRuntimeState` 数据模型。

### implementer-A

- 搭建新运行时与兼容层。

### implementer-B

- 扩展网络同步与 HUD/调试接口。

### implementer-C

- 处理物品注册自动化与 datapack/KJS 接入。

### verifier

- 回归测试 Boss 弹幕命中、清屏、阶段切换、客户端一致性。

### doc-writer

- 维护迁移清单、定义格式说明和编辑器交互说明。

## 当前建议结论

这次重构不建议从“给现有 `ActualSpellCard` 多加几个 if”开始，而应先补一个统一定义层和阶段图运行时。  
底层弹幕实体、移动器、虚拟弹幕同步机制可以保留；真正需要重写的是“符卡定义、阶段编排、状态同步、编辑器模型、物品注册来源”这五层。
