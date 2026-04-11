# 90k 弹幕服务端优化计划

日期：2026-04-10

## 目标

本计划只针对 **90k 虚拟弹幕场景下的服务端 tick 热点**。

本轮已完成**第一阶段代码落地**，并进入自动化流程继续规划下一阶段。上一次优化的问题不是“没有实现”，而是方案边界没先讨论清楚，导致实现只落了一半、而且方向跑偏。

## 当前实现进度（2026-04-10 自动化收口）

### 已完成

1. `ParallelDanmakuTicker` 已去掉 `Step1Result` / `Step2Result` / `Step3Result`、`SectionSnapshot`、`collectActiveDanmakus()` 热路径复制。
2. 新增 `DanmakuVirtualTickData`，并直接挂在 `SimplifiedProjectile` 上，作为 `Step1 -> Step2 -> Step3 -> applyMove` 的统一数据源。
3. `Step1` 已改为并行计算 movement / `src` / `dst` / `searchBox` / section 范围，并使用 `AtomicBitSet` 标记 touched sections。
4. `Step2` 已改为并行消费 `Step1` 结果；section 读取直接复用 `EntityCache` / `SectionCache`，不再额外 snapshot。
5. 虚拟弹幕的 `applyMove` 已从主线程收尾中移出，改为并行提交。
6. `BaseProjectile.computeMove()` 已收敛为纯计算，trail 等每 tick 副作用改到 `beforeMoveTick()`，避免并行路径重复触发。
7. 第二阶段已完成：`SectionCache` 改为缓存候选实体几何快照，`IEntityCache` 提供 visitor 式 section 遍历，`ProjectileHitHelper` / `LaserHitHelper` / `ParallelDanmakuTicker` 共用同一套 section 查询与 cached hit 路径。
8. 第三阶段已完成首版 schedule 覆盖：在当前 tick 结尾为允许预取的 projectile 预做下一 tick 的 Step 1，并在下一 tick 直接消费预取数据。
9. 已执行 `.\gradlew.bat compileJava`，结果通过。
10. 2026-04-11 自动化补充：为 next-tick Step1 预取补上显式 `computeMoveForTick(int)` 路径，并把 `ItemDanmakuEntity` 的安全预取覆盖扩展到纯本地状态 mover。
11. 2026-04-11 自动化补充（二）：修正 `CompositeMover` 在纯计算路径中的内部状态推进问题，并为 server-side `ParallelDanmakuTicker` 增加最近一帧性能统计与 `/danmaku perf` 查询入口。
12. 2026-04-11 自动化补充（三）：为 `ParallelDanmakuTicker` 增加跨 tick 汇总统计，并为 `/danmaku perf` 补充 `summary` / `reset` 子命令，便于按采样窗口观察平均值与峰值。

### 未完成

1. 无

按主程序员 2026-04-10 23:15-23:21 的最新评审，当前仓库里的并行 tick 实现方向有偏差，文档也必须同步改：

- 第一阶段：先修正并行 tick 逻辑本身的 5 个问题
- 第二阶段：再做碰撞层重构，处理 `SectionCache` / `ProjectileHitHelper` / `LaserHitHelper`
- 第三阶段：再做 schedule 满覆盖，例如在 tick 结尾预做下一 tick 的 Step 1

本轮不再沿用“先 snapshot section，再 main-thread materialize，再用 map 查 snapshot”的旧计划。

## 方案前置约束

在开始第一阶段编码前，先把下面几条当成固定约束：

1. 不再额外引入 `HashMap` / `Long2ObjectOpenHashMap` 之类的 tick-local 临时缓存结构来兜数据流。
2. 临时数据优先直接挂在弹幕对象上，而不是做一层“entity -> temp data”的外部 map。
3. 第一阶段只解决并行 tick 逻辑本身，不提前混入第二阶段碰撞层重构。
4. 方案先确认通过，再开始改代码；如果第一阶段的数据流还说不清楚，就继续改文档，不硬写实现。

这里要特别纠正一个倾向：这轮方案里**不能再默认使用 Map 当中间层**。对 90k 弹幕这种热点路径，外部 key-value 容器既增加查找开销，也会让数据流更难验证。

## 当前代码与主程序员意见的冲突点

基于现状代码：

- `ParallelDanmakuTicker` 仍使用 `Step1Result` / `Step2Result` / `Step3Result`
- `Step1` 结果没有成为后续唯一数据源
- `Step2` 仍是主线程串行
- section 路径仍依赖 `LongOpenHashSet` + `Long2ObjectOpenHashMap`
- `collectActiveDanmakus()` 仍在 hot path 上做全量筛选
- `EntityCache` 已有缓存，但当前实现又额外做了一层 `SectionSnapshot`

主程序员的结论很明确：

1. `Step1` 直接用 `AtomicBitSet` 处理 section 列表，不要再走主线程 set/snapshot 聚合
2. 虚拟弹幕的 `applyMove` 可以直接异步执行
3. `EntityCache` 自带缓存，不需要再把结果 snapshot 一遍
4. `Step2` 必须真正消费 `Step1` 已算好的 `src/dst`，而且要并行
5. 不要再做 `collectActiveDanmakus()` 这种热路径全量筛选

此外还有两条额外约束：

- 第一阶段先做上面 1-5，**暂不动 `SectionCache`**
- 不再保留 `Step1Result` 这类 record 临时结构，改为一个统一的 `DanmakuVirtualTickData`
- `DanmakuVirtualTickData` 也不是拿 `Map<Projectile, Data>` 存，而是直接挂在弹幕对象上

## 第一阶段：修正并行 tick 逻辑

状态：已完成（2026-04-10，`compileJava` 通过）

### 阶段目标

把当前 `ParallelDanmakuTicker` 从“结构拆了，但热路径仍不对”修正成真正可继续优化的版本。

### 必做项

#### 1. `Step1` 直接并行写 section 标记

目标：

- 用 `AtomicBitSet` 直接记录 touched sections
- 不再经过主线程 `LongOpenHashSet` 聚合
- 不再保留“先 set，后 snapshot，再 map.get”的链路

要求：

- `Step1` 自己完成 touched section 标记
- 这部分数据成为后续步骤直接消费的结果，而不是仅用于预热

#### 2. 删除额外 `SectionSnapshot` 层

目标：

- 不再把 `EntityCache` 的结果复制到额外 snapshot 结构里
- 直接复用 `EntityCache` / `SectionCache` 既有缓存

要求：

- 第一阶段不改 `SectionCache` 本身
- 只去掉 `ParallelDanmakuTicker` 里重复的一层缓存包装

#### 3. `Step2` 改为真正消费 `Step1` 结果，并且并行执行

目标：

- `Step1` 算好的 `src/dst/searchBox/section` 不能在 `Step2` 里重算后丢掉
- `Step2` 自身必须进入并行路径

要求：

- 不接受 `Step1` 算完、`Step2` 又自己重算 movement/searchBox 的现状
- `Step2` 的工作必须围绕 `DanmakuVirtualTickData` 展开

#### 4. 虚拟弹幕的 `applyMove` 直接异步化

目标：

- 把 `finishMovementStep -> applyMove -> Entity.setPos` 从主线程热点里拿掉

要求：

- 只针对虚拟弹幕
- 不再把“位置提交”留在主线程收尾里
- 第一阶段先把 `applyMove` 异步做起来，再看是否需要后续再细拆

#### 5. 移除 `collectActiveDanmakus()` 热路径筛选

目标：

- 不再在统一 tick 入口对全量弹幕做一次额外筛选复制

要求：

- 统一 tick 入口默认就是虚拟化弹幕路径
- 不接受“每 tick 先复制/过滤一遍 active 列表”这种额外热路径

### 数据结构要求

第一阶段统一改成：

- 新增 `DanmakuVirtualTickData`
- 每发虚拟弹幕都直接持有自己的 `DanmakuVirtualTickData`
- 不再保留 `Step1Result` / `Step2Result` / `Step3Result` 这类 record 流转
- 不接受 `HashMap`、`Long2ObjectOpenHashMap`、`Int2ObjectMap` 之类的“外部临时数据表”

`DanmakuVirtualTickData` 至少负责承载：

- 本 tick 的 `src`
- 本 tick 的 `dst`
- `searchBox`
- section 相关临时索引
- block/entity hit 相关中间结果
- 是否需要后续主线程收尾

### 推荐的数据归属

第一阶段的数据归属建议直接定成：

- `SimplifiedProjectile` 或其虚拟弹幕路径可访问的对象上，新增 `DanmakuVirtualTickData`
- `Step1` 并行写入该弹幕自己的 tick data
- `Step2` 直接继续消费同一个 tick data
- `Step3/Step4` 也继续沿用同一份数据

也就是说，第一阶段的临时数据流应该是：

`projectile -> projectile.tickData`

而不是：

- `projectile -> index -> array of record`
- `projectile -> map lookup -> temp data`
- `section key -> map lookup -> snapshot`

这样做的好处有三个：

1. 热路径上少一次外部索引跳转
2. `Step1 -> Step2 -> Step3 -> Step4` 的数据来源唯一，便于验证
3. 后续第三阶段如果要做 schedule 满覆盖，也更容易把“下一 tick 的预计算结果”直接留在弹幕对象上

### 第一阶段涉及范围

优先改以下路径：

- `ParallelDanmakuTicker`
- 虚拟弹幕 tick 入口调用方
- `applyMove` 所在的虚拟弹幕移动路径
- 新增 `DanmakuVirtualTickData`

第一阶段明确 **不** 做：

- 不改 `SectionCache`
- 不改 `ProjectileHitHelper`
- 不改 `LaserHitHelper`
- 不做 schedule 满覆盖
- 不引入新的 Map 型临时缓存层

### 第一阶段验收标准

- `Step1` 不再依赖主线程 set/snapshot 聚合
- `Step2` 不再重算 `Step1` 已有结果
- `Step2` 进入并行路径
- `applyMove` 不再是主线程热点
- `collectActiveDanmakus()` 从 hot path 移除

### 第一阶段完成说明

已按验收标准完成，当前仓库状态满足第一阶段的 5 个硬要求。

## 第二阶段：碰撞层重构

状态：已完成（2026-04-11，`compileJava` 通过）

### 阶段目标

在并行 tick 逻辑修正完之后，再处理碰撞层的结构性问题。

### 范围

- `SectionCache`
- `ProjectileHitHelper`
- `LaserHitHelper`

### 主要工作

- 重构 section 读取与碰撞查询协作方式
- 收敛弹幕与激光的碰撞查询路径
- 让碰撞层结构和第一阶段的新 tick 数据流匹配

### 说明

主程序员已明确要求：这部分放到第一阶段之后，不要提前夹带进去。

### 第二阶段完成说明

已完成以下收口：

1. `SectionCache` 不再只缓存 `Entity` 引用，而是直接缓存 `boundingBox` / `deltaMovement` / `sweepBox`
2. `IEntityCache` 新增 visitor 式遍历，并支持显式 section 范围输入
3. `ProjectileHitHelper` / `LaserHitHelper` 不再各自分配候选列表，而是直接消费 section 几何快照
4. `ParallelDanmakuTicker` 的 Step2 不再自己重建 section 候选几何，而是复用碰撞层统一接口

## 第三阶段：schedule 满覆盖

状态：已完成（2026-04-11，`compileJava` 通过）

### 阶段目标

进一步做时间片覆盖，让本 tick 的尾部能为下一 tick 提前准备工作。

### 范围示例

- 在 tick 结尾预做下一 tick 的 Step 1
- 评估并填满当前并行 schedule 的空窗

### 说明

这属于后续吞吐优化，不是第一阶段的前置条件。

### 第三阶段完成说明

当前 virtual danmaku 的 tick 入口仍在：

- `YoukaiEntity.aiStep()`
- `DanmakuProxyEntity.aiStep()`

因此第三阶段没有直接对**全部** projectile 强行开启预取，而是实现了安全 opt-in 方案：

1. `BaseProjectile` 新增 `allowNextTickStep1Prefetch()` 边界
2. `DanmakuVirtualTickData` 新增 next-tick Step1 预取存储
3. `ParallelDanmakuTicker` 在当前 tick 结尾并行为下一 tick 预取 Step1
4. 下一 tick 若预取仍匹配当前 tickCount，则直接消费；否则自动丢弃并回退到现算
5. 当前只对 movement 依赖 projectile 本地状态、不会因 owner / target / commander 外部状态变化而失真的 projectile 启用预取

### 2026-04-11 自动化补充

为继续扩展第三阶段的 schedule 覆盖，本轮又补了两点：

1. `BaseProjectile` 新增 `computeMoveForTick(int)`，并让 `ParallelDanmakuTicker` 在预取下一 tick Step 1 时按**下一 tick 的逻辑 tickCount**计算 movement，避免 tick-indexed mover 误拿当前 tick 的 movement 参与预取。
2. `DanmakuMover` 新增显式安全声明；`ItemDanmakuEntity` 现在允许以下 mover 进入 next-tick Step1 预取：
   - `ZeroMover`
   - `RotateMover`
   - `RectMover`
   - `PolarMover`
   - `BezierMover`
   - `FixedDirMover`（仅当其子 mover 安全）
   - `CompositeMover`（仅当其全部子 mover 安全）
3. 仍明确排除依赖外部状态的 mover / 路径，例如 `AttachedMover`、`AttachedFreeRotMover`、`TrackingAttachedMover`、`HomingMover` 与 `controlCode > 0` 的 commander 路径。
4. 已在补充实现后再次执行 `.\gradlew.bat compileJava`，结果通过。

### 2026-04-11 自动化补充（二）

在继续做收益验证与边界复核时，又补了两项：

1. `CompositeMover.move()` 改为按 tick 直接选择当前 segment，不再在 `move()` / `computeMove()` 过程中推进内部 `index`。这样 next-tick Step1 预取、并行 Step1、以及 prepared sequential fallback 都不会因为“同一逻辑 tick 多次 computeMove”而推进 segment 状态两次。
2. `HomingMover` 新增更窄的安全边界：仅当 `targetEntityId < 0`、不依赖动态实体查找时，才允许 next-tick Step1 预取；带实时 target entity 的 homing 继续保持现算。
3. `ParallelDanmakuTicker` 新增最近一帧统计快照，记录：
   - 当前 tick 是顺序还是并行路径
   - 各阶段耗时（warm/prepare/step1/section warm/step2/step3/finish/total）
   - prefetch consume / eligible / stored / failures
   - standard/prepared fallback 与 apply failure 计数
4. 新增 `/danmaku perf` 命令，可在服务端直接查看最近一次 virtual danmaku tick 的统计结果，为 90k 场景 profiling 提供第一手阶段数据。
5. 已在补充实现后再次执行 `.\gradlew.bat compileJava`，结果通过。

### 2026-04-11 自动化补充（三）

为继续收口 profiling 观测入口，本轮又补了两点：

1. `ParallelDanmakuTicker` 新增跨 tick 的汇总统计累加器，可记录 sample 数、并行/顺序占比、projectile / ready 平均值与峰值，以及各阶段耗时和失败计数的汇总信息。
2. `/danmaku perf` 新增：
   - `summary`：输出采样窗口内的平均耗时、峰值耗时、平均 projectile 数、prefetch 命中率与 fallback/failure 汇总
   - `reset`：重置汇总采样窗口，便于 90k 场景重新采样
3. 已在补充实现后再次执行 `.\gradlew.bat compileJava`，结果通过。

也就是说，第三阶段的“schedule 满覆盖”在当前版本实现为：**对确定安全的 projectile 填满 schedule 空窗，对外部状态敏感 projectile 保持语义优先**。

## 实施顺序

1. 第一阶段：并行 tick 逻辑 5 点修正
2. 第二阶段：`SectionCache` / `ProjectileHitHelper` / `LaserHitHelper` 重构
3. 第三阶段：schedule 满覆盖处理

## 当前文档结论

需要改，而且三阶段实现与文档同步均已完成。

本版文档相对上一版做了这几件事：

- 删除了“主线程 materialize touched sections”的计划
- 删除了“额外 `SectionSnapshot` 缓存层”的计划
- 删除了“`collectActiveDanmakus()` 只是后续小优化”的判断
- 把 `applyMove` 异步化提升为第一阶段硬要求
- 把 `DanmakuVirtualTickData` 定为新的统一临时数据结构
- 明确 `DanmakuVirtualTickData` 直接挂在弹幕对象上，不通过外部 Map 管理
- 把 `SectionCache` 重构明确后移到第二阶段
- 把“先确认方案再编码”补成了显式前置约束
- 补充了 2026-04-10 自动化流程收口后的实现进度，明确第一阶段已完成、第二/第三阶段待继续推进
- 补充了 2026-04-11 的第二阶段实现结果，并明确第三阶段当前的真实时序边界与继续推进条件
- 补充了 2026-04-11 的第三阶段实现结果，明确本版采用安全 opt-in 的 next-tick Step1 预取策略
