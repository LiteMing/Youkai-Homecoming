# 最新开发计划

日期：2026-04-10  
分支：`DREAM4test`  
流程：自动化流程

## 当前活动目标

当前活动计划切换为 **90k 虚拟弹幕服务端 tick 优化**，对应文档为 `docs/preview-performance-plan.md`。

本轮不再继续沿用旧的 section snapshot / record 流转方案，优先完成并行 tick 第一阶段收口，并自动规划后续碰撞层与 schedule 优化。

## 当前状态

1. 第一阶段：**已完成**
   已完成 `ParallelDanmakuTicker` 数据流收口、`DanmakuVirtualTickData` 落地、`AtomicBitSet` touched section 标记、`applyMove` 异步化，以及 `collectActiveDanmakus()` 热路径移除。
2. 第二阶段：**未开始**
   下一轮处理 `SectionCache` / `ProjectileHitHelper` / `LaserHitHelper` 的碰撞层重构。
3. 第三阶段：**未开始**
   第二阶段完成后再做 schedule 满覆盖，例如在 tick 尾部预做下一 tick 的 Step 1。

## 本轮已完成项

1. 将并行 tick 的临时数据统一收敛到 `projectile -> DanmakuVirtualTickData`，不再保留 `Step1Result` / `Step2Result` / `Step3Result`。
2. 用 `AtomicBitSet` 替代 `LongOpenHashSet` / `Long2ObjectOpenHashMap` 的 touched section 聚合链路，并直接复用 `EntityCache` / `SectionCache`。
3. 将 `Step2` 改为并行消费 `Step1` 结果，不再重算 `src` / `dst` / `searchBox` / section 范围。
4. 将 `BaseProjectile.computeMove()` 保持为纯计算，把 trail 等每 tick 副作用拆到 `beforeMoveTick()`，避免并行路径重复计算或在 worker 线程触发生成副作用。
5. 将虚拟弹幕的 `applyMove` 从主线程收尾中移出，改为并行提交位置更新。
6. 执行 `.\gradlew.bat compileJava`，结果通过。

## 下一轮自动化开发项

1. 重构 `SectionCache` 与 `ProjectileHitHelper` / `LaserHitHelper` 的协作方式，减少碰撞层重复路径，并对齐新的 tick data 流。
2. 评估是否需要为 section 读取增加更明确的只读快照边界或辅助接口，避免第二阶段继续在 ticker 内堆职责。
3. 在第二阶段完成后，设计并实现 schedule 满覆盖，让本 tick 末尾可以为下一 tick 提前准备 Step 1。

## 验收与风险

1. 本轮验收以第一阶段 5 个硬要求全部落地、`compileJava` 通过为准。
2. 第二阶段和第三阶段尚未执行，因此碰撞层结构统一与 schedule 吞吐优化仍是后续工作，不应误判为已完成。
3. 旧的符卡相关计划暂时不作为当前活动计划；如需恢复，应另开新一轮计划并重新切换 `latest-development-plan.md`。
