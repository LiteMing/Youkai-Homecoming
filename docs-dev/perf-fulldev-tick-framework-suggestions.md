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

### 第二步：构建统一 tick 框架，先做同线程 baseline

这一阶段的目标不是提速，而是把“串行真值路径”先做出来。

建议新增一份统一的 `TickData`，每发弹幕一份，至少包含：

- `entity`
- `srcPos`
- `prevVelocity`
- `plannedMovement`
- `dstPos`
- `searchBox`
- `sectionRange`
- `blockHit`
- `candidates`
- `entityHit`
- `grazeTargets`
- `lifetimeExpired`
- `shouldErase`
- `shouldApplyMove`

然后统一让串行路径跑这几个阶段。这样后续异步化时：

- 串行路径仍然是语义基准
- 并行路径只是“同一阶段的不同调度方式”

建议这个阶段同时做阶段耗时统计，但只统计大阶段，不先做太细的 trace：

- `begin`
- `move`
- `preheat`
- `snapshot`
- `resolve`
- `finish`
- `total`

除了耗时，还建议一起记几个关键计数：

- 弹幕总数
- 实际参与碰撞的弹幕数
- touched section 数
- 预热 section 数
- 候选实体总数
- 实际命中数
- graze 触发数
- 失效/擦除数
- 越出 `UserMatrixCache` 本地矩阵的弹幕数

这样你在第二步结束时就能回答三件事：

1. 统一框架本身有没有引入额外损耗
2. 预热到底值不值得做
3. `R=5` 的本地矩阵够不够用

### 第三步：用同线程 BitSet 抽出预热代码，统一预热

这一步建议只做“预热统一化”，不要顺手把碰撞判定也改了。

基于当前代码，我更建议的写法不是“BitSet + `nextSetBit()` 二次扫描”，而是：

- `BitSet touched`
- `IntArrayList touchedOrder`

流程如下：

1. 在 `planPreheatRange` 阶段，每发弹幕算出自己覆盖的 local section index
2. 若某个 index 第一次被触及：
   - `touched.set(index)`
   - `touchedOrder.add(index)`
3. 统一预热阶段只遍历 `touchedOrder`
4. 对每个 index 调一次 `cache.get(...)`

这样做的好处：

- 仍然满足“用 BitSet 去重”
- 但不会把 `BitSet` 用成新的扫描热点
- 预热顺序稳定
- 同一套逻辑后面串行/并行都能复用

建议这一阶段只覆盖 `UserMatrixCache` 本地矩阵路径。  
对于以下情况先保留旧 fallback：

- 没有 `EntityCachingUser`
- section 范围越出 `R=5` 的本地矩阵

也就是说，第三步的目标应该是：

- 先把“主路径预热”做干净
- 不要为了覆盖极少数 fallback，把整个预热结构搞复杂

建议把预热单独做成一个 helper，例如：

- `SectionPreheater`
- `MatrixSectionPreheater`

然后串行 baseline 和后续异步框架都只调用它，不再各自写一套预热代码。

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
