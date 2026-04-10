# 最新开发计划

日期：2026-04-11  
分支：`DREAM4test`  
流程：自动化流程

## 当前活动目标

上一轮活动计划 **90k 虚拟弹幕服务端 tick 优化** 已完成，详细过程与结果见 `docs/preview-performance-plan.md`。

自动化流程的当前活动目标切换为：**验证优化收益并复核剩余外部状态依赖边界**。

## 当前状态

1. 第一阶段：**已完成**
   已完成 `ParallelDanmakuTicker` 数据流收口、`DanmakuVirtualTickData` 落地、`AtomicBitSet` touched section 标记、`applyMove` 异步化，以及 `collectActiveDanmakus()` 热路径移除。
2. 第二阶段：**已完成**
   已完成 `SectionCache` / `IEntityCache` / `ProjectileHitHelper` / `LaserHitHelper` / `ParallelDanmakuTicker` 的碰撞查询路径收束，统一使用 section 几何快照与 visitor 遍历。
3. 第三阶段：**已完成**
   已完成首版 schedule 覆盖：在 tick 结尾为**确定安全的 projectile** 预做下一 tick 的 Step 1，并在下一 tick 直接消费预取结果；依赖外部状态的 mover 保持现算，不引入语义漂移。

## 本轮已完成项

1. 将并行 tick 的临时数据统一收敛到 `projectile -> DanmakuVirtualTickData`，不再保留 `Step1Result` / `Step2Result` / `Step3Result`。
2. 用 `AtomicBitSet` 替代 `LongOpenHashSet` / `Long2ObjectOpenHashMap` 的 touched section 聚合链路，并直接复用 `EntityCache` / `SectionCache`。
3. 将 `Step2` 改为并行消费 `Step1` 结果，不再重算 `src` / `dst` / `searchBox` / section 范围。
4. 将 `BaseProjectile.computeMove()` 保持为纯计算，把 trail 等每 tick 副作用拆到 `beforeMoveTick()`，避免并行路径重复计算或在 worker 线程触发生成副作用。
5. 将虚拟弹幕的 `applyMove` 从主线程收尾中移出，改为并行提交位置更新。
6. 将 `SectionCache` 从 `Entity` 列表切换为几何快照缓存，并让顺序 projectile、laser、并行 ticker 共用同一套 section 遍历与 hit 检测基础接口。
7. 为 schedule 覆盖引入 next-tick Step1 预取机制：`DanmakuVirtualTickData` 持有预取结果，`ParallelDanmakuTicker` 在当前 tick 结尾并行为下一 tick 预取，`BaseProjectile` 提供显式 opt-in 边界。
8. 执行 `.\gradlew.bat compileJava`，结果通过。
9. 为 next-tick Step1 预取补上显式 `computeMoveForTick(int)` 路径，修正 tick-indexed mover 在预取阶段按“当前 tick”误算 movement 的语义缺口。
10. 为 `DanmakuMover` 增加显式安全声明，并将 `ZeroMover`、`RotateMover`、`RectMover`、`PolarMover`、`BezierMover`、`FixedDirMover`、以及“全部子 mover 都安全”的 `CompositeMover` 纳入 `ItemDanmakuEntity` 的预取覆盖范围。
11. 在完成本轮扩展后再次执行 `.\gradlew.bat compileJava`，结果通过。

## 下一轮自动化开发项

1. 对 90k 场景重新做 profiling，确认三阶段改动叠加本轮 safe mover 扩展后的累计收益。
2. 复核剩余未纳入预取的外部状态路径，例如 `AttachedMover`、`AttachedFreeRotMover`、`TrackingAttachedMover`、`HomingMover` 与 `controlCode > 0`，判断是否存在更窄但仍语义安全的 opt-in 条件。
3. 若 Spark/实测仍显示明显 schedule 空窗，再评估是否需要把 virtual danmaku 调度从实体 `aiStep()` 抽离到更稳定的 world-level 时序。

## 验收与风险

1. 当前该优化计划已完成；验收以三阶段关键提交完成、`compileJava` 通过为准。
2. 第三阶段采用的是**安全 opt-in 预取**，不是对全部 mover 强行开启预取；本轮已扩展到一批纯本地状态 mover，但外部状态敏感路径仍保持现算。
3. 当前后续工作的主要风险不在实现，而在收益验证与剩余覆盖边界判断。
4. 旧的符卡相关计划暂时不作为当前活动计划；如需恢复，应另开新一轮计划并重新切换 `latest-development-plan.md`。
