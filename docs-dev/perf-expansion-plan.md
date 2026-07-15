# perf-expansion 扩展优化计划

日期：2026-04-12  
分支：`perf-async` → 后续 `perfdev-expansion`

---

## 1. 背景（第一阶段完成状态）

`perf-async` 分支在本轮开工前已完成：

| 改造项 | 提交 |
|--------|------|
| plan / collectCollisionInput 异步调度 | `b671a4edd` |
| 按 CPU 核数批量分发 task | `88708b05c` |
| 代理实体弹幕免疫修正 + log 增强 | `ff2413c14` |
| 共享 OwnerInfo 快照 + setPosRaw + baseTick 精简 | `33f8b649b` |

---

## 2. 第二阶段完成状态（perf-async 分支）

本轮在 `perf-async` 分支完成了以下改造，均已推送：

### 2.1 asyncPrepareTick — prepare 并入 plan 阶段

`9664f514b`

`setOldPosAndRot` / `++tickCount` / `tickData.reset()` 从主线程预收集循环移入 plan stage，随 `planMove` / `planPreheatRange` 并行执行。

### 2.2 + 2.3 allDanmakus 类型收窄 + 消除收集循环

`10b2124f3`

`YoukaiEntity` / `DanmakuProxyEntity` 的 `allDanmakus` 由 `LinkedList<SimplifiedProjectile>` 改为 `ArrayList<AsyncProjectile>`；`shoot()` 入口收窄为 `AsyncProjectile`；`tickAll` 签名同步收窄，预收集循环替换为浅拷贝（防 `eraseAllDanmaku` 并发修改）。

### 2.4 applyMoveTick — applyMove 前移至 collectCollisionInput 之后

`1806f2f06` → 后续整合进 `be5510f0f`

新增 `AsyncProjectile.applyMoveTick` 阶段，在 `collectCollisionInput` 完成后、`resolveCollision` 之前执行移动。`finishTick` 剥离移动职责，只保留 doGraze / commitPreMoveEffects / 寿命判定 / 区块越界检查。

实际最终阶段顺序：

```
[异步]   plan（setOldPosAndRot + tickCount + reset + beginTick + planMove + planPreheatRange）
         ↓
[主线程] trimMove
         ↓
[主线程] flushPreheat
         ↓
[异步]   collectCollisionInput
[主线程] applyMoveTick          ← collision+move 合并计时桶
         ↓
[主线程] resolveCollision
         ↓
[主线程] finishTick（doGraze + commitPreMoveEffects + 寿命 + 区块）
```

### 2.5 hasChunk 缓存

`8b3144522`

`UserMatrixCache` 新增懒加载 `boolean[][]` 缓存，同 tick 内重复查询同一 chunk 直接返回缓存结果；`TickData` 新增 `preheatCache` 字段，plan stage 赋值，`finishTick` 使用缓存查询。

### 其他整理

`c963d84d8` / `be5510f0f`

- `runStageAsync` 合并 `boolean async` 参数，消除 `tickAll` 内三元运算符
- `tickAll` 要求 `UserMatrixCache` 非空，调用方加 server guard，内部 null check 全部消除
- `applyMoveTick` 简化：`plannedMovement` 为 null 时不执行任何动作（假设 planMove 已执行）
- `applyPlannedMove` 方法删除，逻辑内联至 `applyMoveTick`
- `StageTrace` 删除死字段 `beginNanos` / `applyMoveNanos`，`trimNanos` 独立计时桶
- log 格式：`plan={} trim={} preheat={} collision+move={} resolve={} finish={}`

---

## 3. 第三阶段计划（perfdev-expansion 分支）

以下步骤改动幅度大、风险较高，从 `perf-async` 切出新分支 `perfdev-expansion` 推进。

### 3.1 finishTick 结束后立即预热下一 tick

- `finishTick` 全部完成后，立即构造下一 tick 的 `UserMatrixCache`
- 将 plan stage 任务提交到线程池，主线程不等待，继续执行本 tick 剩余逻辑
- 下一次进入 `tickAll` 时，plan 结果已就绪，直接进入 trimMove
- 实现要点：
  - `YoukaiEntity` / `DanmakuProxyEntity` 持有"预热中"的 future 引用
  - 进入 `tickAll` 时先 `future.get()` 取结果，再走后续 stage
  - 需严格保证当前 tick 所有副作用（discard / markErased / send）在 plan 开始前提交完毕

### 3.2 preheat + collision 前置至 ServerLevel tickTime 后

- 在 `ServerLevel.tickTime` 结束后通过 Mixin 注入执行点
- 该点执行：`flushPreheat` + `collectCollisionInput`（含 `applyMoveTick`）
- 主线程随后继续正常 tick，进入 `resolveCollision` 时碰撞输入已就绪
- **收益**：collision 阶段完全脱离弹幕 tick 关键路径

### 3.3 resolve 和 finish 保留原位

- `resolveCollision` 和 `finishTick` 仍在弹幕 tick 原有时序中执行
- 副作用提交顺序不变

---

## 4. 风险评估

| 优化项 | 风险等级 | 状态 |
|--------|----------|------|
| asyncPrepareTick + 消除 active 循环 | 低-中 | ✅ 完成 |
| allDanmakus 类型收窄 | 中 | ✅ 完成 |
| applyMoveTick 前移至 collision 后 | 低-中 | ✅ 完成 |
| hasChunk 缓存 | 低 | ✅ 完成 |
| tickAll 接口整理 / log 精简 | 低 | ✅ 完成 |
| finishTick 后立即启动下一 tick plan | 高 | 待做（perfdev-expansion）|
| preheat + collision 前置 Mixin 注入点 | 高 | 待做（perfdev-expansion）|

---

## 5. 不做的事

- `resolveCollision` / `finishTick` 不异步化（含游戏副作用）
- 不在 worker 线程中访问 `level.clip()` / capability / live entity 状态
- `perfdev-expansion` 不向 `perf-async` 合并，直到经过充分测试
