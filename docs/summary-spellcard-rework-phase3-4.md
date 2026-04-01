# 符卡系统重构 Phase 3-4 落地总结

最后更新对应提交：`6d55db25a` `feat: spellcard system rework Phase 3-4`

## 本次落地范围

本次提交完成了重构方案中的以下内容：

1. Phase 3：KubeJS 集成
2. Phase 4：物品注册抽象与动态符卡物品
3. 运行时补丁：修复 legacy bridge 的共享状态问题

本次没有实现的内容：

1. Datapack JSON 加载
2. NBT 覆盖层
3. 编辑器 Phase 5
4. 现有 Boss 符卡的定义式迁移
5. “按所有 `SpellDefinition` 自动批量生成 Forge `ItemEntry`” 的完整机制

## Phase 3：KubeJS 集成现状

### 已提供能力

新增了自定义 KubeJS startup 事件：

```js
YHEvents.registerSpells(event => {
  event.create('kubejs:test_spell')
    .display('Test Spell', 'Defined from startup script')
    .itemForm({
      generate: true,
      cooldown: 80,
      requiresTarget: false,
      iconItem: 'youkaishomecoming:white_laser'
    })
    .phase('main', phase => {
      phase.onTick(YHEvents.actions.addVariable('age', 1))
      phase.transition('end', YHEvents.conditions.tickElapsed(100))
    })
    .phase('end', phase => {
      phase.onEnter(YHEvents.actions.clearScreen())
    })
})
```

### API 结构

`event.create(id)`
- 创建一个新的 `SpellDefinitionBuilder`

`builder.display(name, description)`
- 设置显示名称和描述

`builder.icon(id)`
- 设置 `SpellDisplay.icon`

`builder.model(id)`
- 设置 `SpellDisplay.modelId`

`builder.entryPhase(id)`
- 指定入口阶段

`builder.itemForm({...})`
- 设置 `SpellItemForm`

`builder.phase(id, callback)`
- 定义阶段

`phase.onEnter(action)`
- 添加进入阶段动作

`phase.onTick(action)`
- 添加每 tick 动作

`phase.onExit(action)`
- 添加退出阶段动作

`phase.transition(target, condition, mode?)`
- 添加阶段跳转

### JS 回调桥接

动作和条件支持两种写法：

1. 直接传内置 `SpellAction` / `SpellCondition`
2. 直接传 JS 函数回调

例如：

```js
phase.onTick(ctx => {
  if (ctx.totalTick() % 20 === 0) {
    ctx.setVariable('burst', ctx.getVariable('burst') + 1)
  }
})
```

```js
phase.transition('rage', ctx => ctx.healthRatio() < 0.5)
```

### 内置辅助对象

`YHEvents.actions`
- `setVariable`
- `addVariable`
- `clearScreen`
- `forcePhase`
- `playSound`
- `sequence`
- `conditional`
- `noop`

`YHEvents.conditions`
- `healthBelow`
- `healthAbove`
- `tickElapsed`
- `distanceBelow`
- `distanceAbove`
- `hitCount`
- `variableCheck`
- `always`
- `never`
- `not`
- `and`
- `or`

### 加载时机

KubeJS startup scripts 在 mod 启动时先执行定义注册，随后在 `commonSetup` 内调用：

1. `TouhouSpellCards.registerSpells()`
2. `FairySpellCards.registerSpells()`（若 TLM 已加载）
3. `KubeJSSpellCompat.registerStartupSpells()`

这意味着：

1. KubeJS 定义可以读取并 `copyFrom()` 已存在的 Java 注册符卡
2. KubeJS 定义最终进入当前的 `SpellRegistry`
3. 目前仍是运行时内存注册，不是 Forge 自定义 registry

## Phase 4：物品注册现状

### 已完成部分

新增了 `SpellItemAutoRegister`，把 `YHDanmaku` 中原本手写的预设符卡物品改成表驱动注册。

当前预设物品：

1. 仍保持原有物品 ID 不变
2. 仍使用原来的 `ItemSpell` 子类
3. 仅把注册代码从重复模板抽成统一 helper

新增了通用动态符卡物品：

`youkaishomecoming:spell_dynamic`

用途：

1. 通过 `NBT spell_id` 指向任意已注册的 `SpellDefinition`
2. 使用 `SpellRuntime` 直接驱动玩家侧施法
3. 读取 `SpellItemForm` 的 `cooldown` / `requiresTarget` / `iconItem`

### 动态符卡物品绑定方式

物品需要写入：

```nbt
{spell_id:"namespace:path"}
```

运行逻辑：

1. `DynamicSpellItem` 从 `spell_id` 查 `SpellRegistry`
2. 创建 `DefinitionItemSpell`
3. `DefinitionItemSpell` 内部持有单独的 `SpellRuntime`
4. 每 tick 用 `PlayerHolder` 驱动定义式阶段逻辑

### 当前限制

虽然 `SpellItemForm.generate` 字段已经存在，但这次并没有实现：

“扫描所有 `SpellDefinition`，自动为每个定义产出一个真正的 Forge 注册物品”

原因是方案里原本提到的时序问题还在：

1. Java 物品注册必须在构造期完成
2. KubeJS / Datapack 定义要到更晚阶段才完整可见

因此本次的实际落地是：

1. 旧预设物品改为表驱动 helper
2. 新增一个通用的 `spell_dynamic`
3. 为未来真正的自动生成机制预留抽象

## 运行时修复

本次顺手修了一个必须先修掉的问题：

`LegacyTickerAction` 原本把 legacy `SpellCard` 实例直接挂在 action 对象上。

这会导致：

1. 同一个 `SpellDefinition` 被多个实体使用时共享旧符卡状态
2. 玩家动态施法与实体施法可能串状态
3. `reset()` 只会重置当前 action 持有的单例，不符合 runtime 隔离要求

现在改为：

1. `LegacyTickerAction` 只保留 factory
2. `SpellRuntime` 增加 `actionState`（`IdentityHashMap`）
3. legacy `SpellCard` 实例改为按 runtime 惰性创建
4. runtime reset 时统一清理并 reset 状态

这个修复是定义式/桥接式符卡能够继续扩展的前置条件。

## 与原计划的偏差

### 已对齐

1. “注册 KJS 事件”
2. “实现 JS builder API”
3. “支持 JS 回调条件和动作”
4. “实现 `SpellItemAutoRegister`”
5. “实现 `DynamicSpellItem`”
6. “从 `YHDanmaku` 迁移手写 SpellItem”
7. “保持旧物品 ID 兼容”

### 尚未完全对齐

1. `SpellItemAutoRegister` 目前是“注册 helper”，不是“全定义自动扫描注册器”
2. `itemForm.generate` 目前未驱动真实的 Forge 物品自动生成
3. KubeJS 侧还没有提供更高层的弹幕 pattern DSL，仅提供 action/condition callback bridge

## 下一步建议

### 优先级 1

完成 Phase 2 剩余部分：

1. Datapack JSON 加载
2. NBT 覆盖层

原因：

1. 这样 `SpellDefinition` 的“数据源”才真正闭环
2. KubeJS 与 JSON 才能共用一套定义模型
3. `spell_dynamic` 才有更实际的内容来源

### 优先级 2

补充 Phase 4 的真正自动注册策略设计：

可以考虑拆成两层：

1. Java 内置定义：构造期生成静态物品
2. KubeJS / Datapack 定义：继续走 `spell_dynamic`

不要强行把晚加载定义塞进 Forge 正常物品注册流程，否则会把时序问题重新引回来。

### 优先级 3

再进入 Phase 5 编辑器：

因为编辑器建立在“定义模型已经稳定”之上，现在直接做 UI 会把未定接口固化下来。

## 验证结果

本次代码提交已通过：

```bash
./gradlew.bat compileJava
```
