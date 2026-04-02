# KubeJS 符卡脚本示例

本目录放的是 `Youkai's Homecoming` 当前版本可用的 KubeJS 符卡定义示例。

## 放置位置

实际生效的脚本需要放到：

```text
kubejs/startup_scripts/
```

本目录里的 `.js` 文件只是示例，不会自动加载。

## 当前入口

在 `startup_scripts` 中使用：

```js
YHEvents.registerSpells(event => {
  // ...
})
```

## 可用 Builder

`event.create(id)`
- 创建一个新的 `SpellDefinition`

`builder.display(name, description)`
- 设置显示名称和描述

`builder.icon(id)`
- 设置图标资源 ID

`builder.model(id)`
- 设置 `model_id`

`builder.entryPhase(id)`
- 设置入口阶段
- 不带命名空间时，会自动解析成 `<spell_id>/<phase>`

`builder.itemForm({...})`
- 设置物品表现
- `generate` 目前只保留字段，不会自动生成 Forge 注册物品

`builder.phase(id, callback)`
- 定义阶段

`builder.copyFrom(spellId)`
- 复制当前已注册的符卡定义
- 目前最实用的用法是“复用现有 Java legacy 符卡的发弹逻辑，再在外面包阶段/条件”

## Phase API

`phase.onEnter(action)`

`phase.onTick(action)`

`phase.onExit(action)`

`phase.transition(targetPhase, condition, mode?)`
- `targetPhase` 不带命名空间时，会自动解析成 `<spell_id>/<phase>`
- `mode` 可用值：`immediate`、`clear_screen`、`delayed`

## 内置辅助对象

`YHEvents.actions`
- `setVariable(key, value)`
- `addVariable(key, delta)`
- `clearScreen()`
- `forcePhase(phaseId)`
- `forcePhase(phaseId)` 建议传完整阶段 ID
- `playSound(soundId[, volume, pitch])`
- `sequence(...actions)`
- `conditional(condition, ifTrue, ifFalse)`
- `noop()`

`YHEvents.conditions`
- `healthBelow(value)`
- `healthAbove(value)`
- `tickElapsed(ticks)`
- `distanceBelow(value)`
- `distanceAbove(value)`
- `hitCount(count)`
- `variableCheck(key, op, value)`
- `always()`
- `never()`
- `not(condition)`
- `and(...conditions)`
- `or(...conditions)`

## JS 回调上下文

动作回调和条件回调都可以直接写函数：

```js
ctx => {
  ctx.setVariable('age', ctx.getVariable('age') + 1)
}
```

当前示例里使用过、并且已经确认可用的方法：

- `ctx.phaseTick()`
- `ctx.totalTick()`
- `ctx.healthRatio()`
- `ctx.distanceToTarget()`
- `ctx.hitCount()`
- `ctx.getVariable(key)`
- `ctx.setVariable(key, value)`
- `ctx.clearDanmaku()`

## 当前限制

1. KJS 这套接口目前主要负责“阶段图/条件/变量/跳转”编排。
2. 现在还没有直接从 JS 发射弹幕 pattern 的高层 DSL。
3. 如果你需要复用现有 Boss 的发弹逻辑，建议先 `copyFrom('已有符卡ID')`。
4. `itemForm.generate` 当前不会自动产出真正注册物品。
5. 想测试 KJS/datapack 定义的物品形态，继续使用：

```mcfunction
/give @s youkaishomecoming:spell_dynamic{spell_id:"kubejs:your_spell"}
```

6. 给实体强制设置符卡时，命令建议写成带引号：

```mcfunction
/yhspell set <entity> "kubejs:your_spell"
```

## 示例文件

建议从这个索引开始：

- [00-index.example.js](/D:/IdeaProjects/Youkai-Homecoming/docs/kjs/00-index.example.js)

按专题拆开的可复制示例：

- [01-basic-logic.example.js](/D:/IdeaProjects/Youkai-Homecoming/docs/kjs/01-basic-logic.example.js)
  纯 KJS 状态机、变量、相对阶段 ID、基础跳转
- [02-conditions-and-branches.example.js](/D:/IdeaProjects/Youkai-Homecoming/docs/kjs/02-conditions-and-branches.example.js)
  `and/or/not`、多分支、`conditional`、多种条件组合
- [03-copyfrom-legacy.example.js](/D:/IdeaProjects/Youkai-Homecoming/docs/kjs/03-copyfrom-legacy.example.js)
  复用现有 Java legacy 符卡，再由 KJS 包阶段逻辑
- [04-override-builtin.example.js](/D:/IdeaProjects/Youkai-Homecoming/docs/kjs/04-override-builtin.example.js)
  直接覆盖内置 spell ID
- [05-force-phase-and-absolute-phase.example.js](/D:/IdeaProjects/Youkai-Homecoming/docs/kjs/05-force-phase-and-absolute-phase.example.js)
  绝对阶段 ID、`entryPhase()`、`forcePhase()`
- [06-batch-register.example.js](/D:/IdeaProjects/Youkai-Homecoming/docs/kjs/06-batch-register.example.js)
  用 JS helper 批量注册多张类似符卡
- [07-dynamic-item-workflow.example.js](/D:/IdeaProjects/Youkai-Homecoming/docs/kjs/07-dynamic-item-workflow.example.js)
  以 `spell_dynamic` 和 `requiresTarget` 为中心的测试流程

## 覆盖用途

上面的示例基本覆盖了当前这套 KJS 接口最常见的使用场景：

1. 从零创建一个纯 KJS `SpellDefinition`
2. 用变量驱动阶段切换
3. 用 helper condition/action 组合复杂条件
4. 用 JS 回调桥接动作和条件
5. 复用已有 Java legacy 符卡定义
6. 覆盖内置 spell ID
7. 显式使用绝对 phase ID
8. 在一个文件中批量生成多张符卡
9. 给 `spell_dynamic` 和 `/yhspell set` 提供可直接测试的目标定义
