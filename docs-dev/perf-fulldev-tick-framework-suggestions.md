# perf-fulldev 服务端弹幕 Tick 框架建议

日期：2026-04-11  
分支：`perf-fulldev`  
范围：服务端虚拟弹幕路径

## 1. 当前代码现状

当前 `perf-fulldev` 并没有 `perf-dev` 文档里提到的 `ParallelDanmakuTicker / TickData / StepResult` 框架，服务端虚拟弹幕仍然走老的串行链路：

1. `YoukaiEntity.tickDanmaku()`
2. `DanmakuProxyEntity.tickDanmaku()`
3. 每发弹幕直接 `setOldPosAndRot()`、`++tickCount`、`e.tick()`
4. `BaseProjectile.tick()` / `BaseLaser.tick()` 内部自行处理碰撞、移动、寿命和收尾

当前几个关键耦合点如下：

- `BaseProjectile.tick()` 把 `baseTick`、碰撞查询、`onHit`、寿命判定、移动、区块卸载判定揉在一起。
- `BaseLaser.tick()` + `YHBaseLaserEntity.tick()` 还有一套和普通弹幕不同的顺序，不能简单强行共用一份实现。
- `ProjectileHitHelper.getHitResultOnMoveVector()` 和 `LaserHitHelper.getHitResultOnProjection()` 同时做了：
  - live world 访问
  - cache 访问
  - 候选实体收集
  - 命中判定
- `IEntityCache.foreach()`、`EntityStorageCache.get()`、`UserMatrixCache.get()` 都是懒加载结构，当前实现不适合直接在 worker 线程里调用。
- `ItemDanmakuEntity.updateVelocity()` 不只是“算移动”：
  - 会执行 `onTrail`
  - 会访问 `getOwner()`
  - 会走 `DanmakuCommander.move()`
- `IYHDanmaku.alterHitBox()`、`IYHDanmaku.hurtTarget()`、`GrazeHelper.getHitBoxDelta()` 仍然依赖 live entity / capability 状态。

结论很直接：当前代码不能直接把 `e.tick()` 整体搬到异步线程。必须先做“统一框架 + 快照边界 + 主线程 commit”。

## 2. 需要先认清的约束

### 2.1 客户端并行 tick 不能直接照搬到服务端

`ClientDanmakuCache.tick()` 之所以可以在阈值以上直接并行 `e.tick()`，本质上是因为客户端虚拟弹幕没有服务端那套 live world 碰撞和实体缓存访问。  
服务端不满足这个条件，所以不能复用同一思路。

### 2.2 当前 `computeMove()` 对服务端来说还不够“纯”

`BaseProjectile.computeMove()` 的注释已经写成了“可并行”，但在当前代码里这只对“不同实体各自 mover 独立”这个层面成立；对服务端总链路还不够。

原因是：

- `updateVelocity()` 里已经混入回调和 owner 访问
- `AttachedMover` / `AttachedFreeRotMover` 这类 mover 还会读取 owner 的实时位置/朝向
- `IYHDanmaku` 路径里的命中箱修正也依赖实时状态

所以第一步不应该直接相信现有 `computeMove()` 可以安全放到 worker。

### 2.3 当前 cache 体系只能主线程预热，再异步消费快照

目前的 cache 结构：

- `EntityStorageCache`：每 tick 一个静态单例，内部 `FastMap` 懒建
- `UserMatrixCache`：3D 数组懒填充
- `SectionCache`：内部是可变 `ArrayList`

这意味着：

- worker 线程不能直接继续 `get()` / `foreach()`
- worker 线程只能消费主线程已经构造好的只读快照

### 2.4 `UserMatrixCache.R` 当前是 `5`

当前代码不是旧文档里的 `13`，而是 `5`。  
这对第三步很关键：

- 如果要做 owner-centered dense `BitSet`
- 当前固定矩阵大小只有 `11 x 11 x 11 = 1331` 个 section slot

这对同线程预热是好事，但要先统计越界率，不能默认它一定覆盖后续全部碰撞范围。

## 3. 总体建议

建议把你的四步理解为：

1. 先把服务端虚拟弹幕 tick 拆成统一阶段，但仍然同线程执行
2. 在统一阶段里把耗时拆出来，先拿到可靠 baseline
3. 再把“section 预热”抽成独立模块，用同线程 `BitSet` 做统一预热
4. 最后只把“纯计算阶段”异步化，所有 world mutation 仍然回主线程 commit

换句话说，正确边界应该是：

- 主线程：收集输入、预热、构建快照、最终提交
- worker：只做纯计算

而不是：

- worker 直接碰 live world / live entity / capability / lazy cache

## 4. 对应四步的落地建议

### 第一步：拆分 tick 逻辑

建议先新增一个统一入口类，例如：

- `VirtualDanmakuTicker`
- 或 `DanmakuTickPipeline`

先只替换：

- `YoukaiEntity.tickDanmaku()`
- `DanmakuProxyEntity.tickDanmaku()`

不要一上来改 world-added projectile 的自然实体 tick。

在当前代码里，`SimplifiedProjectile` 的直接子类只有：

- `BaseProjectile`
- `BaseLaser`

所以把一个新的中间层 `AsyncProjectile` 插在这里是合适的，结构上也足够干净。

#### 1.1 建议采用 `AsyncProjectile` 中间层

你补充的这三条我认为是对的，第一步建议直接按这个骨架推进：

1. `AsyncProjectile extends SimplifiedProjectile`
2. `BaseProjectile extends AsyncProjectile`
3. `BaseLaser extends AsyncProjectile`
4. 所有弹幕共享 `AsyncProjectile.tick()`

这样做的价值是：

- 统一模板方法真正落在共享抽象层
- 虚拟弹幕和非虚拟弹幕都能复用同一阶段语义
- 服务端和客户端逻辑的差异可以收口在阶段方法内部

#### 1.2 `AsyncProjectile` 应该自带 `TickData`

建议 `AsyncProjectile` 内置一份“每实体复用”的 `TickData`，而不是每 tick 临时 new。

更合适的形式是：

- `protected final TickData tickData = new TickData();`
- 每个 tick 开头 `tickData.reset(this);`

这样第二步做 baseline 时，数据流已经和后续并行框架一致。

但这里要明确边界：

- `TickData` 只存单发弹幕本 tick 的临时结果
- `allDanmakus / temp / toBeSent / removeDanmaku` 仍然属于容器级状态

也就是说：

- `TickData` 是 projectile-level
- `VirtualDanmakuTicker` 是 container-level

这两层不要混。

#### 1.3 `AsyncProjectile.tick()` 只做阶段编排

第一步建议就把 `tick()` 收成模板方法，自己不再承担业务逻辑。

建议的职责分配是：

- `AsyncProjectile.tick()` 只负责编排阶段顺序
- `BaseProjectile` / `BaseLaser` override 阶段方法
- 具体子类继续 override 更细粒度 hook

这样后续如果 `VirtualDanmakuTicker` 想批量调用某个阶段，就可以直接复用这些方法，而不需要再“模拟一次完整 tick”。

#### 1.4 `BaseProjectile` 和 `BaseLaser` 不再 override `tick()`

这一条建议直接定成第一步的结构目标：

- `BaseProjectile` 不再 override `tick()`
- `BaseLaser` 不再 override `tick()`
- 其子类也不再通过 override `tick()` 表达差异
- 差异全部改成 override 拆分后的阶段方法

这样完成后，继承关系会稳定成：

- `SimplifiedProjectile`
- `AsyncProjectile`
- `BaseProjectile` / `BaseLaser`
- `YHBaseDanmakuEntity` / `YHBaseLaserEntity`
- `ItemDanmakuEntity` / `ItemLaserEntity`

这会明显降低后面第二、三、四步的改动扩散面。

#### 1.5 第一版阶段方法不要切得过细

第一版不建议一开始就切十几个公共 hook，不然公共抽象层会很快碎掉。

建议第一步的公共模板先保留 6 个主阶段：

- `beginTick`
- `planMove`
- `planPreheatRange`
- `collectCollisionInput`
- `resolveCollision`
- `finishTick`

然后 `BaseProjectile` / `BaseLaser` 在各自内部再按需要拆私有 helper。

#### 1.1.1 / 1.1.2 / 1.1.3 建议拆法

为了让每一小步都能单独测 bug 和性能，建议第一步继续细分成下面三小步：

##### 1.1.1 引入 `AsyncProjectile` 模板层

- [x] 已完成

范围：

- 新增 `AsyncProjectile extends SimplifiedProjectile`
- `BaseProjectile` / `BaseLaser` 改为继承 `AsyncProjectile`
- `AsyncProjectile` 持有 `TickData`
- `AsyncProjectile.tick()` 只负责阶段编排
- `BaseProjectile` / `BaseLaser` 不再 override `tick()`，改为 override 阶段方法
- 暂时保留 `YHBaseLaserEntity.tick()`，先不碰 laser 子类自定义收尾

目标：

- 只做继承层和模板层接线
- 现有行为尽量保持不变
- 给后续 `1.1.2` 留出稳定落点

本小步建议手测：

- 几百个低速普通弹幕，确认能命中玩家
- 几百个高速普通弹幕，确认不会穿人或提前消失
- 几百个 laser，确认现有命中和显示长度没有明显异常
- 简单看一次 TPS / tick 体感，确认没有明显额外损耗

##### 1.1.2 收掉 `YHBaseLaserEntity.tick()` 剩余逻辑

- [x] 已完成

范围：

- 把 `YHBaseLaserEntity.tick()` 里的 `danmakuMove()` 和超时 `discard()` 移入拆分后的阶段方法
- 删除 `YHBaseLaserEntity.tick()` override
- 确保当前 projectile hierarchy 内不再有弹幕类 override `tick()`

目标：

- 把 laser 分支也真正纳入模板体系
- 让“所有弹幕共享 `AsyncProjectile.tick()`”这件事真正成立

本小步建议手测：

- 低速/高速 laser 对玩家命中是否正常
- 激光展开、收束、提前截断长度是否正常
- 生命周期结束时是否正常消失

##### 1.1.3 固化第一步验收口径

范围：

- 补齐第一步的最小 smoke test 口径
- 固定“低速/高速、普通弹幕/laser、虚拟/非虚拟”的检查项
- 记录第一步完成后的 baseline，确认没有新 bug 和明显性能回退

目标：

- 不急着进入第二步
- 先确认第一步结构重构本身没有撞坏判定和时序

本小步建议手测：

- `YoukaiEntity` 虚拟弹幕
- `DanmakuProxyEntity` 虚拟弹幕
- world-added 普通弹幕
- world-added laser
- 几百发低速/高速混合，重点看：
  - 能不能打到人
  - 判定范围有没有漂移
  - 弹幕是否提前/延后消失
  - 是否出现明显性能退化

建议把单发弹幕 tick 拆成以下阶段：

1. `beginTick`
   - `setOldPosAndRot()`
   - `++tickCount`
   - `baseTick()`
   - 若已失效，立刻短路
2. `planMove`
   - 只计算本 tick 的移动结果
   - 得到 `src / dst / plannedMovement`
   - 不在这里执行 trail、hurt、erase、setPos
3. `planPreheatRange`
   - 直接根据 `src / dst / searchBox` 写出 section 范围
   - 这一步只产出范围，不做 cache 访问
4. `collectCollisionInput`
   - 根据移动前后位置做 block hit 输入采集
   - 根据 section 范围采集候选实体快照
5. `resolveCollision`
   - 只做几何判定和命中结果归约
6. `finishCommit`
   - 执行 `doGraze`
   - 执行 `onHit`
   - 执行寿命/死亡/卸载判定
   - 执行 `terminate`
   - 执行 `applyMove`
   - 维护列表、发包、flush erase

这里最重要的一点是：  
你的“移动”阶段应该是“规划移动”，不是“立刻改实体状态”。  
因为你后面第三、第四步都要复用这份移动结果。

#### 1.6 这个结构下要额外注意两点

1. `AsyncProjectile.tick()` 不要把服务端/客户端差异硬写在模板顺序之外。  
   更好的做法是模板顺序固定，阶段内部再判断 `level().isClientSide()` 是否执行具体工作。

2. `BaseLaser` 当前语义和普通弹幕并不完全同构。  
   第一版即使统一进 `AsyncProjectile`，也要允许 `BaseLaser` 在阶段实现里保留自己的顺序差异，不要为了共享代码强行压平。

### 第二步：构建统一 tick 框架，先做同线程 baseline

这一轮第二步按你的新规划，目标不是直接上多线程，而是先把“按阶段批量驱动所有弹幕”的主线程基线搭出来，并把后续异步所需的接口边界一次性准备好。

本轮第二步建议拆成 `1.2.1` 到 `1.2.5`，每一小步都单独验证、单独提交。

#### 1.2.1 构建 `ParallelTicker`，但先只跑主线程

范围：

- 新增 `ParallelTicker`
- 不再由容器层对每发弹幕直接调用 `tick()`
- 改为 `ParallelTicker` 对全体弹幕按阶段批量调用：
  - `beginTick`
  - `planMove`
  - `planPreheatRange`
  - `collectCollisionInput`
  - `resolveCollision`
  - `finishTick`

目标：

- 先建立“全体弹幕一起跑每个阶段”的统一框架
- 先不引入 worker 线程
- 让当前主线程路径就已经长成未来并行路径的形状

这一小步的关键要求：

- `ParallelTicker` 不直接调用 `tick()`
- 当前只是调度方式变化，不应该改变单发弹幕语义

#### 1.2.2 构建 `StageTrace`，统计各阶段耗时

范围：

- 新增 `StageTrace`
- 统计 `ParallelTicker` 各阶段耗时
- 先只做主线程 baseline

建议至少统计：

- `begin`
- `move`
- `preheat`
- `collisionInput`
- `resolve`
- `finish`
- `total`

建议同时记录以下计数：

- 弹幕总数
- touched section 数
- 预热 section 数
- 候选实体总数
- 实际命中数
- graze 触发数
- 失效/擦除数

目标：

- 先拿到第二步的阶段基线
- 后面每个子步骤都能直接对比有无退化

#### 1.2.3 为 `IEntityCache` 增加 `asyncGet` / `asyncForEach`

范围：

- `IEntityCache` 新增 `asyncGet`
- `IEntityCache` 新增 `asyncForEach`
- `asyncGet` 允许返回 `null`

语义要求：

- async 版本遇到“当前 tick 尚未缓存的 section”时直接跳过
- 不触发新的 section 懒加载
- 不修改缓存结构

这样做的目的不是现在就提速，而是先建立一个“多线程下只消费已缓存 section”的线程安全接口。

这一小步要特别注意：

- async 版本暂时不应该被现有实体本地 `tick()` 直接拿去替换同步版本
- 它的第一职责是提供安全边界，而不是立刻改变现有行为

#### 1.2.4 构建 `AtomicBitSet` 预热，直接使用 `UserMatrixCache` 同范围

范围：

- 新增 `AtomicBitSet` 预热工具
- 预热范围直接使用 `UserMatrixCache` 的固定范围
- 不再额外统计 touched range

语义要求：

- 预热的唯一目标是触发 `UserMatrixCache` 的 section 缓存加载
- 不在这一步附带做碰撞判定
- 不在这一步引入新的范围统计热路径

这里要明确：

- 本轮第二步里的 `AtomicBitSet` 是“预热工具”
- 不是新的碰撞候选收集结构

#### 1.2.5 引入 `IEntityIterator`，让碰撞判定不再自己决定 cache 访问方式

范围：

- 定义 `@FunctionalInterface IEntityIterator`
- 只声明 `foreach`
- `IEntityCache` 保持独立，直接使用 `cache::foreach` / `cache::asyncForEach` 适配到 `IEntityIterator`
- 弹幕碰撞判定方法增加参数 `IEntityIterator`

职责变化：

- 实体自身 `tick()` 路径传入 `cache::foreach`
- `ParallelTicker` 路径传入 `cache::asyncForEach`
- 碰撞判定代码本身不再决定调用哪种 cache 方法

当前实现备注：

- `IEntityIterator` 已作为独立函数式接口引入
- `ProjectileHitHelper` / `LaserHitHelper` 只保留显式传入 `IEntityIterator` 的入口
- 当前主线程路径仍统一传 `cache::foreach`
- `asyncForEach` 已可作为后续并行路径输入，但本轮暂未切换使用

#### 1.2.6 拆分 block hit / entity hit 逻辑

范围：

- `ProjectileHitHelper` 继续拆分
- `LaserHitHelper` 继续拆分
- 方块碰撞逻辑和实体碰撞逻辑分离

目标：

- 让 `trimMove` 和后续异步判定更容易复用

#### 1.2.7 引入 `trimMove` 阶段

范围：

- `planMove` 先写出 `dst / moveDst`
- 新增 `trimMove` 阶段
- 对不能穿墙的激光和弹幕，在 `trimMove` 中根据方块碰撞修正一次 `dst`

目标：

- 避免隔墙打人

#### 1.2.8 改造 `BaseProjectile` 的多实体命中消费

范围：

- `BaseProjectile` 不再默认只处理第一个实体
- 应遍历命中实体列表逐个执行命中逻辑
- 若命中后弹幕已消亡，则提前停止遍历

目标：

- 同时支持“可穿透多目标”和“只打一个就消亡”两类行为

这样做之后，碰撞判定的线程安全边界会更清楚：

- 判定代码只依赖迭代器抽象
- 调度层决定当前使用同步迭代还是 async-safe 迭代

即使这一轮仍然全部跑在主线程，这个接口改造也值得先做，因为它会直接决定后面第三、第四步能不能平滑切过去。

#### 第二步统一验收要求

`1.2.1` 到 `1.2.8` 每一步都建议单独验证、单独提交。

每一步至少做：

1. 编译通过
2. 几百个低速弹幕验证命中
3. 几百个高速弹幕验证命中
4. 普通弹幕和 laser 都要测
5. 看判定范围有没有漂移
6. 看有没有提前/延后消失
7. 看性能是否明显变差

建议这一步的核心判断标准是：

1. `ParallelTicker` 主线程版是否语义正确
2. `StageTrace` 是否能稳定给出阶段基线
3. `asyncGet` / `asyncForEach` / `IEntityIterator` 是否把后续异步边界提前做干净
4. `AtomicBitSet` 预热是否没有引入新的范围统计热点

### 第三步：待第二步稳定后再单独细化

原文档里“同线程 BitSet 统一预热”的内容，已经并入这一轮第二步的 `1.2.4`，当前实现已具备：  

- `UserMatrixCache.preheat(AABB)` 负责按碰撞搜索盒标记预热范围
- `AsyncProjectile.planPreheatRange(data, cache)` 负责让每发虚拟弹幕决定自己的预热范围
- `ParallelTicker` 在 preheat 阶段统一 `flushPreheat()`

因此原第三步的“统一预热”目标可以视为已经完成。  
第三步不再沿用旧描述，等第二步 `1.2.1` 到 `1.2.8` 跑稳后再单独写新的后续内容，避免文档和实现顺序重新打架。

### 第四步：把移动 / 碰撞检测 / 收尾异步化

这一步要非常明确地拆成：

- 异步“计算”
- 主线程“提交”

不建议把完整 `finish` 整段直接扔进 worker。  
按当前代码，真正能安全异步的只有“纯判定”部分。

#### 4.1 可以考虑异步化的部分

1. 移动规划
   - 前提：先把 `updateVelocity()` 里的副作用拆出去
   - 只留下纯数学移动计算
2. 几何碰撞判定
   - 前提：候选实体已经在主线程冻结成快照
   - worker 只读 `CachedTarget`
3. 收尾判定
   - 例如是否寿命结束、是否需要 erase、是否需要 terminate、是否需要 apply move
   - 只写回 `TickData` 的 flag，不直接改 world

#### 4.2 必须留在主线程的部分

以下内容按当前代码都不建议直接异步：

- `level.clip()`
- `IEntityCache.foreach()` / `UserMatrixCache.get()` / `EntityStorageCache.get()`
- `onTrail.execute(...)`
- `doGraze(...)`
- `onHit(...)`
- `hurtTarget(...)`
- `terminate()`
- `markErased(...)`
- `applyMove(...)`
- `DanmakuManager.send(...)`
- `DanmakuManager.flushErases()`
- `allDanmakus / temp / toBeSent` 的维护

#### 4.3 当前代码下异步前必须先拆的点

在真正把 move / resolve / finish 放进 worker 前，建议先做这几个解耦：

1. `ItemDanmakuEntity.updateVelocity()`
   - 把 `onTrail` 从移动计算里拆出去
2. owner 依赖 mover
   - `AttachedMover`
   - `AttachedFreeRotMover`
   - 以及可能依赖 owner / target 实时状态的 mover
   - 先改成消费主线程快照
3. `IYHDanmaku.alterHitBox()`
   - 不要在 worker 内继续读取 `self().getOwner()`、`youkai.targets.contains(player)`、`GrazeHelper`
   - 这些都要提前算成 snapshot 字段
4. `finish`
   - 分成 `finishPlan` 和 `finishCommit`
   - worker 只做 `finishPlan`
   - 主线程执行 `finishCommit`

这一步的目标不是“把最多代码搬离主线程”，而是“只搬走可证明安全的计算”。

## 5. 推荐的数据结构

### 5.1 `TickData`

建议让每发弹幕有一个可复用的 `TickData`，字段可按需要裁剪，但建议至少有：

- `SimplifiedProjectile entity`
- `Vec3 src`
- `Vec3 originalVelocity`
- `ProjectileMovement plannedMovement`
- `Vec3 dst`
- `AABB searchBox`
- `SectionRange localRange`
- `HitResult blockHit`
- `List<CachedTarget> candidates`
- `EntityHitResult entityHit`
- `IntArrayList grazeTargetIds` 或等价结构
- `boolean expired`
- `boolean erased`
- `boolean shouldTerminate`
- `boolean shouldApplyMove`

### 5.2 `CachedTarget`

如果第四步要异步跑碰撞，这个快照至少应包含：

- `Entity entity`
- `AABB boundingBox`
- `Vec3 deltaMovement`
- `AABB sweepBox`
- 预计算 hitbox 修正所需的数据
- 预计算 graze 所需的数据
- 预计算 owner / target 关系结果

原则是：  
worker 判定阶段不再回头读 live entity。

### 5.3 `TickAdapter`

普通弹幕和 laser 当前 tick 顺序不同，所以建议不要硬写成一个 if/else 大函数。  
更稳妥的是做适配层，例如：

- `ProjectileTickAdapter`
- `LaserTickAdapter`

第一版甚至可以先只让 `BaseProjectile` 走新框架，`BaseLaser` 先保留串行老路径，等普通弹幕稳定后再接。

## 6. 建议的实现顺序

建议按下面顺序推进，和你给的四步是对齐的：

1. 抽出统一 `tickAll()` 入口，先消掉 `YoukaiEntity` / `DanmakuProxyEntity` 的重复逻辑。
2. 抽出阶段函数，但仍然同线程执行。
3. 建立 `TickData` 和阶段耗时统计，拿到 baseline。
4. 把“预热范围计算”和“统一预热”抽出来，用同线程 `BitSet + touchedOrder` 跑通。
5. 只在主线程冻结候选实体快照，不要急着并行判定。
6. 拆 `updateVelocity()` 的副作用，把纯 move 和副作用分开。
7. 把几何 move / resolve / finishPlan 放到异步线程。
8. 最后再决定是否把 laser 接入同一框架。

## 7. 我认为当前最重要的几条结论

1. 当前 `perf-fulldev` 的真实代码基线还是“服务端串行直接 `e.tick()`”，所以第一目标不是“继续接旧 step2/step3”，而是先把统一框架重新搭起来。
2. 你的四步方向是对的，但第四步必须理解成“异步判定 + 主线程 commit”，不能把 world mutation 直接扔到 worker。
3. 第三步如果要上 `BitSet`，推荐 `BitSet` 只负责去重，遍历仍用 `touchedOrder`，这样最贴合当前代码，也最容易复用到后续并行版本。
4. 当前最大的技术前置不是线程池，而是把 `computeMove`、hitbox 修正、候选实体访问这三块彻底做出清晰边界。

如果按这个顺序推进，第二步结束时你就会有一条能测、能对比、能回退的统一串行框架；第三步可以稳定验证预热值不值；第四步再做异步时，风险会比直接改现有 `e.tick()` 小很多。
