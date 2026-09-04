# 开发计划:3D 弹幕自动闪避系统 (Dodge Pilot)

> 本文档为独立执行计划,面向没有先前对话上下文的代码执行 agent。
> 执行前请通读全文,尤其是「执行须知」与「核心设计约束」。
> 分阶段执行,每个阶段独立提交、独立验证,不要跨阶段合并提交。

## 进度看板

| Phase | 状态 | 说明 |
|---|---|---|
| 0 威胁提供者 / 分级预测 | **完成** | `predict/` + `runPilotPredictTest`（259 pass） |
| 1 扫掠碰撞 / 评分 | **完成** | `threat/` + `runPilotThreatTest`（含搜索、细搜、地形与性能窄测试） |
| 2 势场 + 预览 MVP | **完成** | APF + AI 试飞 + Dbg 叠加 + 死亡回放 dump |
| 3 时空搜索 | **完成** | MaxiMin best-first + 路线承诺 + 全堵时局部锥细搜；中高难符卡对比待人工 |
| 4 试飞统计(轻量) | 未开始 | **已降级**:只做统计小结+JSON dump,不做难度榜单/评分矩阵 |
| L 激光精度专项 | **完成** | T3 旋转/锚点外推已修;副作用:移动抖动增多 → 专项 J |
| J 抖动治理 | **完成(调参)** | 阻尼↑/激光近场力↓/死区↑/迟滞加宽+搜索驻留5t;Perf 显示 flip/mode 率;待旋转激光 30s 人工验收 |
| 5 球面缝隙 | **完成(核心)** | 低频全局方向走廊搜索与缓存已接入；触发率统计/收缩率模型保留为可选增强 |
| 6 Boss 接入 | **完成(实验)** | `YoukaiDodgePilot` 服务端直控已接入；实机性能与手感待人工复核 |
| 7 玩家 buff | **完成(核心)** | 三档共用完整控制器并线性增强；普通输入接管、Control 安全偏置已接入 |

**分支策略**：单一功能分支 `feat/pilot`（不再 per-phase 开分支）。

### 已交付摘要

- `predict/`：Threat 链 T1/T2/T3；激光 length+active
- `threat/`：SweptCollision(slab)、SelfBoxModel、NodeScorer、ThreatSnapshot+时空哈希、CollisionOracle
- `apf/` + `DodgePilot`：势场 + 迟滞切搜索
- `search/`：FreeFlight/Grounded 动作、SpatioTemporalSearch 节点预算、局部锥细搜与路线承诺
- `gap/`：低频全局开放走廊引导，和近身局部细搜分层运行
- 预览：`AI` / `Dbg` 按钮、`targetVelocity` 回写、Perf 显示 mode/clr/nodes
- 调试：`pilot/debug/` 轨迹线+力箭头+trail（death dump 已移除，Phase 4 降级）
- 玩家 buff：`YHEffects.AUTO_DODGE` + `AutoDodgeClientHandlers` + `dodge_sake`/`_ii`/`_iii`
- 待人工：`runClient` 开 AI 试飞 ≥60s；玩家三档 buff 箭雨/符卡；妖怪服务端 Pilot 性能与手感；Phase 4 测评

## 1. 背景与目标

本项目 (Youkai's Homecoming, Minecraft Forge mod) 拥有一套数据驱动的东方 Project 风格弹幕系统
(符卡 SpellDefinition → SpellRuntime → 弹幕实体),以及一个符卡编辑器/正交预览系统。

目标:实现一个**通用的 3D 空间自动飞行避弹算法核心**(以下称 Pilot),并接入三个消费场景:

| 优先级 | 场景 | 说明 |
|---|---|---|
| P1(主要) | **符卡编辑器 AI 试飞** | 预览界面中 AI 驱动靶子试飞符卡(已交付),附带轻量试飞统计(被弹/擦弹/存活等)。统计定位为**同环境相对参考**——预览与实际游玩是两套逻辑,不追求绝对难度评分 |
| P2 | **玩家自动闪避 buff** | 通过高价值食物/仪式获得分级自动闪避能力(官方"半作弊"渠道,照顾手残玩家) |
| P3 | **Boss 闪避强化** | 升级现有简陋的 `DodgeController`,让 Boss/妖怪躲玩家弹幕更聪明 |

算法蓝本来自一个 2D 东方避弹 AI 的成熟实践(人工势场法 + A* 时空搜索混合控制),
本计划将其适配到 3D + Minecraft 20 TPS 环境。**三个场景共享同一个算法核心,只是参数档位不同。**

## 2. 算法设计总览

Pilot 是分层混合控制器,每 tick 输出一个期望速度向量:

```
输入: ThreatSnapshot(所有敌对弹幕/激光的当前状态 + 预测轨迹) + 自机状态 + PilotProfile(参数档)
                        │
  ┌─────────────────────┼──────────────────────┐
  │ 宏观层: 球面缝隙投影  │ 中观层: 人工势场(APF) │ 近身层: 时空搜索(A*)
  │ 防封位, 输出逃逸偏置  │ 默认控制层, 有前瞻性   │ 危险时精确规划, 带迟滞切换
  └─────────────────────┴──────────────────────┘
                        │
输出: 期望速度 Vec3 (速度直控模型, 消费方自行转成实体运动)
```

关键算法要点(来自 2D 实践的移植与改造):

1. **速度直控模型**:自机(靶子/玩家/Boss)按"瞬时变向的速度控制"建模,不搜索加速度空间。
   常规动作以**14 个基础方向**(6 轴向 + 8 体对角)× 高/低速 2 档 + 原地不动 =
   **29 种操作**起搜；14 方向是低成本第一层采样，不是搜索分辨率上限。全部基础方向都被
   预测碰撞走廊覆盖时，必须围绕最不坏方向触发局部加密二次采样(见 Phase 3)。
2. **时空搜索**:在 (位置, tick) 空间做 best-first 搜索(带节点预算,非完整 BFS),深度 4~6 tick。
   - **MaxiMin 路径评价**(语义务必按此实现,勿混淆):
     `评分(节点) = min(评分(父节点), 该节点自身时空安全分)`——即一条路径的分数被其
     最危险节点封顶;父节点在**存活**子节点中取评分最高者作为自身传播值(Max);
     子节点全部死亡 → 该分支评分 = -∞ 作废。整体即 Max-over-分支 ( Min-over-路径 )。
     作用:避免"穿小缝去安全区"的假优路径,同时死路自动反向传播。
   - **剪枝规则**:同一路径内不允许高低速切换;剔除与上一步方向夹角 > 90° 的高速分支。
3. **人工势场**:每颗威胁弹幕按"预测最近接近点"施加斥力;目标点(锚点/攻击目标)施加引力;
   输出加**死区(deadzone)**和阻尼防震荡(2D 实践中的已知坑)。
4. **球面缝隙投影**(防封位):从自机向 Fibonacci 球面均匀方向撒 64~128 条射线,统计各方向命中
   预测弹幕的时间,得到"开放锥"分布;跟踪未来数 tick 内开放锥的收缩趋势,收缩过快则输出逃逸偏置力。
   (2D 的一维角度投影升级为二维球面投影。注意:3D 里封位远比 2D 难,该层触发应当是低频事件。)
5. **距离度量用逐轴 AABB 距离(切比雪夫式),不用欧氏距离**。MC 判定全是 AABB,
   评分函数按 (自机盒 ⊕ 弹幕盒) 的 Minkowski 展开逐轴算穿透/间隙。
5b. **自机有效判定盒 ≠ 原版碰撞箱(关键正确性点)**:自机被判中用的盒子取决于
   "谁在打谁",评分/搜索必须用与游戏实际命中逻辑一致的盒子,否则测评统计失真:
   - **玩家 vs 本模组弹幕**:`IYHDanmaku.alterEntityHitBox`(`IYHDanmaku.java:116-124`)——
     原版碰撞箱 X/Z 两侧内缩 `GrazeHelper.getHitBoxDelta(player)`、**底部抬高 2 倍缩减量、
     顶部不缩**,再向外 Minkowski 加弹幕半宽。缩减量来自 `YHAttributes.HITBOX` 属性,
     **可被装备/效果动态改变,必须每 tick 活取进 PilotState**,不可缓存为常量。
   - **妖怪/Boss vs 被妖怪追踪的弹幕**:`alterHitBox` 走 `boundingBox().inflate(GRAZE_RANGE)`
     (`IYHDanmaku.java:36-41`)——Boss 的有效受击盒被**放大** 1.5 格,Phase 6 的 Boss
     闪避必须按这个更大的盒子评分,否则会以为擦着过去了实际已判中。
   - **擦弹带**:`inflate(弹幕半宽 + GRAZE_RANGE)`,评分的擦弹统计用这个。
   - **任何实体 vs 原版/其他模组投射物**:原版逻辑,完整碰撞箱(原版命中另有 ~0.3 inflate)。
   - 结论:`Threat` 需携带命中语义类别(DANMAKU / VANILLA),自机盒由
     "消费方 × 威胁类别"的 SelfBoxModel 提供(见 Phase 1)。
6. **扫掠碰撞**:20 TPS 下弹幕每 tick 可位移 1~3 格,节点碰撞检测必须做扫掠,禁止逐点采样。
   **注意:`fastprojectileapi/collision/ProjectileHitHelper.checkHit` 不是真扫掠**——它是把
   目标盒沿其速度最多分 8 个子步离散采样、再 clip 弹幕位移线段(`ProjectileHitHelper.java:68-94`),
   这是游戏实际的命中逻辑但存在高速斜穿漏检窗口。Pilot **不要复用它**,自行实现
   slab 法真扫掠(相对运动化为线段 vs Minkowski 展开盒,一次求解,比 8 步采样更快且无漏检)。
   Pilot 的内部安全检查因此比游戏判定略保守(多躲不误事)。**统一真值原则**:Pilot 安全
   检查、测评中弹计数、擦弹计数共用同一套扫掠实现与同一套 SelfBoxModel,并明确 tick 内
   顺序(取弹幕本 tick 位移段 + 自机本 tick 位移段,做相对扫掠)。预览现有的 `onTargetHit`
   (`PreviewCardHolder.java:307-317`)是"移动后终点 + inflate(0.3)"的离散检查——高速弹
   会漏报、口径也偏松,只保留给预览的 HP/转阶段玩法回路,**测评统计不得依赖它**。
7. **分级轨迹预测(provider 链)**:预测不绑定本模组弹幕,按精度分四档,由 provider 链路由:
   - **T1 精确档**:本模组纯函数 Mover(白名单制,见 Phase 0)迭代 `move()` 前算。仅用于
     预览测评与球面防封位层(长前瞻曲率误差才值得付出),服务端默认不启用。
   - **T2 弹道档**:原版投射物物理是已知常数(如 AbstractArrow 每 tick `vel*=0.99, y-=0.05`),
     按**精确 EntityType 白名单**套 (drag, gravity) 常数表前算,覆盖箭/雪球/火球/三叉戟等。
     不按 `AbstractArrow` 等基类 instanceof 认领——其他模组子类可能覆写物理,基类匹配
     会产生"高置信度的错误预测",比 T3 更糟;白名单外的子类落 T3。
   - **T3 观测外推档(通用兜底)**:记录任意投射物最近 2~3 tick 位置,拟合速度+加速度做
     二阶外推。**对原版、其他模组、不可预测 Mover(追踪弹等)一律适用**,是默认档。
   - **T4 直线外推**:刚出现、历史不足 2 tick 的实体用当前速度直线外推。
   注意:6 tick 搜索前瞻内 T3 与 T1 差距很小(2D 蓝本用纯直线假设即可通关),
   **不要为追求 T1 覆盖率投入超额工程量**——它是可选增强,不是地基。

## 3. 现有代码基础(执行 agent 必读)

以下均为已核实的现状(分支 hd-stable):

### 弹幕实体与运动
- `content/entity/danmaku/YHBaseDanmakuEntity.java` — 弹幕基类,继承 `fastprojectileapi/entity/BaseProjectile`。
- `content/entity/danmaku/ItemDanmakuEntity.java` / `ItemLaserEntity.java` — 实际使用的弹幕/激光实体。
- `content/entity/danmaku/IYHDanmaku.java` — 有 `alterEntityHitBox(e, ...)` 与 **`GRAZE_RANGE`(擦弹判定)**,
  测评统计直接复用(用法参考 `fastprojectileapi/render/virtual/ClientDanmakuCache.java:344-350`)。
- `content/spell/mover/DanmakuMover.java` — 抽象类,唯一方法 `ProjectileMovement move(MoverInfo info)`。
  共 19 个 Mover 实现。**关键结构(已核实)**:`TargetPosMover` 是中间基类,定义抽象纯方法
  `Vec3 pos(MoverInfo)`,其 `move()` 只在位移² ≤ 1e-4 的兜底分支才读活体实体
  (`info.self().rot()`,见 `TargetPosMover.java:12-18`)——而**旋转对避弹无关紧要**。
  因此位置预测可以**绕过 `move()` 直接调 `pos(info)`**,一举覆盖 Rect/Polar/Bezier/
  MultiBezier/Spline/Formula/Orbital/Translate 等全部 TargetPosMover 子类,无需逐个特调。
  `ZeroMover`/`RotateMover`(直接继承 DanmakuMover)速度恒零或纯 tick 函数,同样可精确预测。
  依赖 owner 活体状态的 `AttachedMover`/`AttachedFreeRotMover` 与容器类
  `CompositeMover`/`LayeredMover`(除非其子 Mover 全部可预测)落 T3。
- `content/spell/mover/MoverInfo.java` — **纯 record** `(tick, prevPos, prevVel, self, ownerInfo)`,
  已有 `offsetTime(int)` 方法。这是轨迹预测的天然接口。注意 `pos(info)` 若依赖
  `ownerInfo`(如 OrbitalMover 绕 owner 旋转),预测时 owner 位置按快照冻结——射手
  多为悬停/慢移,冻结误差可接受;若实测误差过大再降 T3。
- **注意**:Mover 标注了 `@SerialClass`,但已核实其 `@SerialField` 均为构造期配置而非
  运行时可变状态(以 RotateMover 为例:`dir`/`rate` 构造后不变,`move()` 是 `info.tick()`
  的纯函数)。预测仍**绝不能调用会写状态或读活体实体的路径**(见核心设计约束 C2)。

### 预览系统(P1 场景接入点)
- `content/spell/preview/VirtualSpellScene.java` — 驱动预览:`doTick()` 调
  `runtime.tick(holder)` + `holder.tick()`;有播放/暂停/单步/倍速(0.25x~4x)/重置;有实体数熔断。
- `content/spell/preview/PreviewCardHolder.java` — 本地实体池(不进 world);
  **`fakeTarget`(ArmorStand)就是靶子**,已有 `setTargetPos(Vec3)` / `getTargetPos()` /
  `targetVelocity()` / `targetFlying` 标志 / `onTargetHit` 命中回调(带去重)。
  Pilot 只需每 tick 算出新位置调 `setTargetPos` 即可接管靶子。
- `content/spell/preview/SpellPreviewScreen.java` — 预览 UI 主界面(已嵌入动作编辑面板)。
- `content/spell/preview/SpatialHash.java` — 现成空间哈希(此前对单靶命中检测是负优化被弃用,
  但 Pilot 的大量邻域查询可能用得上,先测再用)。
- `content/spell/preview/OrthographicViewport.java` — 正交视口渲染,调试可视化画在这里。
- 打开方式:游戏内命令 `/yhspell preview <id>`,测试符卡 `youkaishomecoming:test_fire_danmaku`,
  更多真实符卡见 `content/spell/game/`(灵梦/魔理沙/琪露诺等 18 个角色符卡)。

### Boss 移动(P3 场景接入点)
- `content/entity/movement/DodgeController.java` — 现有简陋实现:扫 8 格半径,把危险弹幕位置
  取平均后反向逃跑,10 tick 冷却期间盲走上次方向。这就是要被 Pilot 替换的东西。
- `content/entity/movement/BossMovementController.java` — 控制器接口
  (`getDesiredMovement(CardHolder)` / `getPriority()` / `isActive()` / `getSpeedMultiplier()`),
  经 `CompositeMovementController` 组合,Pilot 以新控制器身份接入,**接口不用改**。

### 其他
- `content/spell/difficulty/DifficultyProfile.java` — 符卡难度随血量缩放的配置
  (speed/frequency/count),测评报告应按多个 healthRatio 分别跑分。
- 项目文档惯例:`docs/plan-spellcard-rework-claude.md`(符卡重构主计划,Phase 7 数据驱动迁移未完成)。
- 提交信息惯例:`type(scope): english summary / 中文摘要`。

### 参考实现:MovesLikeMafuyu 的玩家自动闪避(必读,尤其 Phase 7 前)
同机器上的另一个项目已实现一套"机动动作型"玩家自动闪避,经过实际验证:
`D:\IdeaProjects\MovesLikeMafuyu\src\main\java\com\mafuyu404\moveslikemafuyu\event\AutoDodgeEvent.java`
(效果注册在 `effect/AutoDodgeEffect.java`,食物触发在 `event/AutoDodgeFoodEvent.java`)。
从中吸收的关键经验:
- **移动权柄**:整个决策与执行跑在客户端本地玩家上(`Dist.CLIENT` + `isLocalPlayer()` +
  直接 `setDeltaMovement`);效果本身由服务端事件添加。玩家移动是客户端权威,
  这样做零拉扯感。**Phase 7 采用同一权柄方案,不再作为开放问题。**
- **出生即响应**:监听 `EntityJoinLevelEvent`,投射物出生瞬间做一次紧急评估
  (随后再持续扫描数 tick),把反应延迟压到最低。弥补"凭空出现的弹幕无法预测"的弱点。
- **地形感知**:每个候选位置检查 `level.noCollision` + 落脚支撑(不闪进墙、不闪下悬崖)。
- **动作词汇表**:横移脉冲(补速到目标速度而非叠加固定力)、跳跃(下半身威胁)、
  临时卧倒(上半身威胁,**判定箱高度缩到 0.62**——改变自身判定是一种闪避手段)、
  紧急动作带 6 tick 冷却。命中点按身体上下半区分流选择动作。
- **到达时刻对齐的路径安全检查**(`predictHitNearTick`):只在自机预计到达某中间点的
  tick 附近查碰撞——与本计划搜索层的时空节点检查同思路,可作实现参照。
- **弹道常数**:drag 0.99(水中 0.6)、箭重力 0.05、投掷物 0.03,T2 provider 直接参考。
- 它的局限(也是 Pilot 的补强方向):逐弹独立判断无密度场概念、只在预测命中时才动、
  仅地面(在水中/空中直接 return)、10 tick 直线式预测——对箭矢足够,对密集弹幕不够。

## 4. 核心设计约束(违反即返工)

- **C1 核心零客户端依赖**:算法核心包 `content/spell/pilot/` 不得 import 任何
  `net.minecraft.client.*` 或预览包类。预览(client)、Boss(server)、玩家 buff(server)都要用它。
  核心输入输出只用 `Vec3`/`AABB`/自定义 record。
- **C2 预测不污染状态**:轨迹预测不得改变活体弹幕实体和 Mover 的任何状态,
  也不得调用会读取活体实体实时状态的路径。T1 精确档策略(按第 3 节已核实结构):
  - **绕过 `move()`,直接调 `TargetPosMover.pos(info)`** 做纯位置预测(避开 rot 兜底
    分支的 `info.self()` 读取),覆盖全部 TargetPosMover 子类;
  - `ZeroMover`/`RotateMover` 位置恒定,直接白名单;
  - 白名单审核 checklist(逐个过,写进代码注释):① `pos()`/`move()` 是否写任何成员字段?
    ② 是否读 `info.self()`(rot 兜底以外)?③ 是否依赖 `ownerInfo` 之外的外部可变对象?
    三问皆否才进白名单;
  - `AttachedMover` 系、`CompositeMover`/`LayeredMover` 及任何 checklist 不过的实现
    **一律落 T3 观测外推**,不做克隆方案、不做逐 Mover 特调。宁可保守退化,不可污染状态。
- **C2b 通用性优先**:算法核心(threat/apf/search/gap/eval 各包)只消费抽象的
  `Threat` record(判定盒 + 预测轨迹 + 元数据),**不得 import 本模组任何弹幕实体类**。
  与具体弹幕体系的耦合全部隔离在 `predict/` 包的 provider 实现里。验证方法:把 T1 provider
  整个删掉,系统必须仍能对原版骷髅箭雨正常闪避(仅精度下降)。
- **C3 性能预算**:预览场景 pilot 计算 ≤ 2ms/tick(注意 4x 倍速时一帧内 doTick 4 次,预算按次算);
  Boss 场景服务端 ≤ 0.5ms/tick/实体(用低参数档 + 帧间分摊)。搜索必须带节点预算上限,
  超时立即返回当前最优,禁止无上限搜索。
- **C4 速度直控**:Pilot 输出期望速度向量,不模拟加速度/惯性。消费方各自负责落地
  (预览直接 setTargetPos 积分;Boss 走 MovementController;玩家场景见 Phase 7 专门讨论)。
- **C5 可复现(已降级为开发者便利,非约束)**:预览是闭环模拟,可复现技术上可达,
  但测评已降级(见 Phase 4),不再有依赖它的验收。保留两条低成本项:
  ① holder 随机源(`PreviewCardHolder.java:53`)可注入 seed(一行改动,供手动 A/B 调参);
  ② Pilot 自身零随机(打破对称用确定性扰动)——这条仍是硬要求,它同时保证死亡回放可读。
  不做:Math.random() 迁移、确定性自检、弹流哈希比对。统计代码仍应避免遍历哈希集合中的
  实体(实体 ID 每次运行不同,顺序不稳会让统计输出抖动),用插入序列表。
- **C6 激光单独建模**:激光是"线段 + 宽度"的胶囊/盒体,不能混进点状弹幕的球形/盒形距离模型。
  蓝本实践中两次重大翻车都出在激光判定宽度上,务必写单元测试核对
  `ItemLaserEntity`/`YHBaseLaserEntity` 的实际几何(长度、宽度字段、锚点位置)。

## 5. 分阶段计划

### Phase 0 — 威胁提供者架构与分级轨迹预测
**产出**:`content/spell/pilot/predict/` 包
- `Threat` record:核心算法唯一认识的威胁类型。**预测不是 `Vec3[]` 位置序列,而是逐 tick 的
  `ThreatFrame[]`**:`ThreatFrame(位置, 朝向(可空), 判定形状参数, active)`。原因:激光碰撞
  是"每 tick 从当前位置沿当前朝向打 `getLength()` 长的射线"(`BaseLaser.java:27-36`),
  而旋转激光可以**位置不动、仅朝向随 tick 变**(RotateMover 速度恒零、纯转向),位置序列
  表示不了;`active` 位表示该 tick 是否有判定(激光预警期/延迟启动)。普通弹幕的 frame
  朝向留空、形状为盒半宽。Threat 另携带命中语义类别(DANMAKU/VANILLA)、伤害标记、
  来源实体弱引用。
- `ThreatProvider` 接口 + 注册表:`boolean supports(Entity)` / `Threat capture(Entity, int horizon)`。
  按注册顺序首个 supports 者胜出。**接口与注册表放核心包(端中立);组装发生在各消费方
  初始化处**——预览在 client 侧组装(可含 T1),服务端在 Boss/玩家 handler 侧组装
  (默认 T2/T3),核心只消费组装好的 `List<ThreatProvider>`,维持 C1。本阶段实现四个 provider:
  1. **MoverExactProvider(T1)**:按 C2 策略——TargetPosMover 子类经 `pos(info.offsetTime(i))`
     直调预测(绕过 move() 的 rot 兜底),ZeroMover/RotateMover 位置恒定;白名单按 C2
     checklist 逐个过审,`ownerInfo` 依赖者 owner 位置按快照冻结。不做克隆、不逐个特调。
  2. **BallisticProvider(T2)**:**只认精确的原版 EntityType 白名单**(箭/雪球/火球/
     三叉戟等,常数从原版对应 tick 源码核实,写进注释)。不要按 `AbstractArrow` /
     `ThrownItemProjectile` / `AbstractHurtingProjectile` 基类 instanceof 认领,
     白名单外的模组子类落 T3(理由见第 2 节 T2)。
  3. **ObservedMotionProvider(T3,通用兜底)**:维护 `entityId -> 最近 3 tick 位置` 的
     轻量历史表(实体消失即逐出),二阶拟合外推。**supports 恒真**,任何 Projectile /
     SimplifiedProjectile 都接得住——原版、其他模组、追踪弹全走这里。
  4. 历史不足时内部退化为直线外推(T4)。
- 激光几何建模:从 `ItemLaserEntity`/`YHBaseLaserEntity`/`BaseLaser` 核实长度
  (`getLength()`)、有效判定半径(`getEffectiveHitRadius()`)、射线锚点
  (pos + BbHeight/2,见 `BaseLaser.java:31`)与预警期/激活条件,逐 tick 填进 ThreatFrame。
  旋转激光的朝向必须逐 tick 重算——这正是 T1 直调纯 Mover(如 RotateMover.move() 是
  `info.tick()` 的纯函数)能精确给出的信息,也是激光场景必须走 T1 的原因
  (T3 观测外推只看位置,对"位置不动的旋转激光"完全失效,此时保守处理:
  按当前朝向 ± 旋转历史外推,或整根激光所在平面记为危险)。
- **验收**:单元测试——白名单内 Mover(至少 Rect/Polar/Bezier 三个)T1 预测 20 tick 与真实
  实体逐 tick 轨迹逐点误差 < 1e-6;原版箭 T2 预测与真实飞行轨迹误差 < 0.01;旋转弹走 T3
  在 6 tick 前瞻内误差 < 0.5 格;激光宽度与实体判定箱一致;**删除 T1 provider 注册后
  其余测试全部仍通过**(C2b 通用性验证)。

### Phase 1 — 威胁模型、扫掠碰撞与评分函数
**产出**:`content/spell/pilot/threat/` 包
- `ThreatSnapshot`:某 tick 时刻全部威胁的不可变快照,**经 Phase 0 的 ThreatProvider 注册表构建**
  (预览:PreviewCardHolder 实体池;服务端:level.getEntities 扫描 + 敌我过滤),
  快照层不关心威胁来自本模组、原版还是其他模组。
- 扫掠碰撞:`sweptHit(自机盒, 自机位移, 弹幕盒, 弹幕位移) -> 碰撞时刻 t 或 miss`,
  slab 法一次求解(相对运动化为线段 vs Minkowski 展开盒)。**不要复用
  `ProjectileHitHelper.checkHit`**——那是 8 步离散采样,有高速斜穿漏检窗口(见第 2 节 6)。
- `SelfBoxModel`:自机有效判定盒工厂,按 (消费方 × Threat 命中语义类别) 出盒(见第 2 节 5b):
  预览靶子 = 预览实际命中语义(`PreviewCardHolder` 的 inflate(0.3) 检测);
  玩家 vs DANMAKU = `alterEntityHitBox` 收缩语义(每 tick 活取 `getHitBoxDelta`);
  Boss(妖怪)vs DANMAKU = `inflate(GRAZE_RANGE)` 放大盒;任何自机 vs VANILLA = 原版碰撞箱。
  实现放 provider 侧(读实体的部分),核心只拿到最终的盒参数。
- 节点评分函数:基于逐轴 AABB 间隙的最小时空距离,近距离指数惩罚,含擦弹容忍带
  (间隙 < GRAZE_RANGE 记擦弹不判死)。
- `CollisionOracle` 接口:`boolean isFree(AABB)` +
  `default boolean isSupported(AABB) { return true; }`(落脚支撑,Phase 7 地面模型才用,
  默认恒真,接口一次定形避免后续 break)。**主场景是空中飞行,障碍方块可能来自
  上下左右前后任意方向**,所以 `isFree` 是每个搜索节点/势场候选的通用检查,不是
  只查地面。实现按消费方:预览 = 恒 true(空场地);服务端 = `level.noCollision` 包装。
  **核心只认识这个接口,不 import Level**(维持 C1)。
- **时间预算与 broad-phase(节点预算 ≠ 时间预算)**:预览实体池上限 50,000
  (`PreviewCardHolder.java:50`),不做预筛的话每个搜索节点遍历全部威胁,2000 节点预算
  照样把 2ms 打爆。快照构建时即做:① 威胁 Top-K 截断(按预测最近接近时间/距离排序,
  K 进 Profile);② 对预测帧建时空 broad-phase(按 tick 分桶的空间哈希,节点只查本 tick
  桶内邻域,`preview/SpatialHash.java` 可作起点);③ pilot 每 tick 带真实
  `System.nanoTime()` 截止时间,超时立即返回当前最优——节点预算只是第二道保险。
- **验收**:单元测试覆盖:高速弹穿越薄自机盒(8 步离散采样漏检、扫掠必须报中)、
  斜穿角落、激光扫过、静止大玉逐轴间隙正确性、玩家收缩盒 vs 原版盒出不同结果;
  **性能基准**:100 颗弹幕构建 ThreatSnapshot(20 tick 前瞻)+ 全量扫掠评分一轮,
  记录耗时并断言 ≤ 0.5ms(在 CI 波动下可放宽到 1ms,但必须留基准数字)。

### Phase 2 — 势场层 + 预览接入 MVP + 调试可视化
**产出**:`content/spell/pilot/apf/` 包 + 预览接入
- `PotentialFieldSolver`:斥力 = 按预测最近接近点加权(只对"正在接近"的弹幕),
  引力 = 锚点(测评模式下为场地中心偏向,可配),输出限幅 + 死区 + 低通阻尼。
- `PilotProfile` record:核心算法参数(反应延迟 tick、搜索深度、方向数、射线数、速度档、
  死区大小、擦弹容忍……)。旧的 `NOVICE / ADEPT / LUNATIC` 仅是现有内部常量名，不能作为
  玩家可见称谓，也不能继续代表三套不同的控制策略；玩家分级改为同一控制器的线性 Profile
  派生(见 Phase 7)。
- `DodgePilot` 门面类:`Vec3 tick(ThreatSnapshot, PilotState)`,内部先只挂势场层。
- 预览接入:`SpellPreviewScreen` 加 "AI 试飞" 开关按钮;开启后每 doTick 构建 ThreatSnapshot、
  跑 pilot、`holder.setTargetPos(pos.add(vel))`,并把靶子限制在可配活动范围盒内(默认边长 = 预览
  Range 设置)。**必须同时把真实速度喂回去**:`PreviewCardHolder.targetVelocity()` 目前恒返回
  `Vec3.ZERO`(`PreviewCardHolder.java:241-243`),而自机狙/提前量符卡靠它做预判——不修的话
  这类符卡对 AI 靶子永远打不出提前量,测评难度被系统性低估。加 `setTargetVelocity` 或由
  setTargetPos 差分自动维护。
- 调试可视化(开发期就要,别留到最后):视口内画预测轨迹线、势场合力箭头、被击中前 60 tick
  的快照环形缓冲(死亡回放 dump 到日志/json,蓝本实践证明这是最重要的调参工具)。
- **验收**:`/yhspell preview youkaishomecoming:test_fire_danmaku` 开 AI 试飞,靶子能持续躲避
  简单弹幕 ≥ 60 秒;肉眼无高频抖动(死区生效);tick 耗时显示 ≤ 2ms(VirtualSpellScene
  已有 `lastTickNanos` 统计可扩展)。

### Phase 3 — 时空搜索层(近身精算)
**产出**:`content/spell/pilot/search/` 包
- `ActionModel` 抽象:搜索层的动作分支由它提供,节点状态含"当前姿态"。两个实现:
  - `FreeFlightModel`:14 方向 × 高/低速 + 不动 = 29 操作(预览靶子、飞行 Boss、飞行玩家);
  - `GroundedModel`(Phase 7 前置):8 水平方向脉冲 + 跳跃 + 卧倒/起身 + 不动,
    卧倒改变自机判定箱高度(评分用姿态对应的盒子),节点需 CollisionOracle 落脚检查。
    本阶段先把接口留好并实现 FreeFlightModel,GroundedModel 可延后到 Phase 7 一起交付。
- best-first 搜索:动作分支来自 ActionModel、深度 4~6、节点预算(默认 ≤ 2000 节点/tick,
  Profile 可调);MaxiMin 评价 + 死路反传;剪枝(同路径无高低速切换、剔除 >90° 高速反向分支)。
- 与势场层的迟滞切换:预测最小时空距离 < 阈值A 进搜索模式,> 阈值B 退回势场(A < B 防振荡)。
- **紧急局部细搜(已定)**:14 个基础方向的候选必须扫掠完整自机判定盒，而不是发一条
  无体积射线；每条候选记录最早碰撞 tick、无碰撞位移长度和沿途最小间隙。当 14 个方向
  全部失败时，按"存活时间最长 → 无碰撞位移最长 → 最小间隙最大"选出最不坏方向，围绕它
  生成一个局部锥并密集二次采样。细搜仍使用相同的未来 ThreatFrame 与扫掠体积判定：优先
  按与锥中心夹角从小到大检查，找到达到安全阈值的无碰撞路线即可短路并立即执行；预算内有
  多条候选但没有一条达到短路阈值时，执行评分最高者；仍无安全路线时执行存活最久的路线。
  此路径只在全堵时触发，严格受时间/节点预算约束。
- **计划连续性(已定)**:成功路线短暂承诺 4~8 tick；只有当前路线已不安全，或新路线的安全性
  显著更高时才换向。相同评分用与当前计划/速度的转角更小者稳定决胜，避免逐 tick 重新起搜
  导致反复横跳。搜索失败时沿用上面的"存活最久"兜底，因为下一波弹幕仍可能开缝。
- **验收**:用 `content/spell/game/` 里的中高难度符卡(如 RemiliaSpell、SakuyaSpell 系列)对比
  纯势场 vs 势场+搜索的存活时间,搜索版显著更长;节点预算命中时优雅降级不超时。

### Phase 4 — 试飞统计(轻量,定位为参考工具)
> **降级说明(用户拍板)**:预览与实际游玩是两套逻辑(命中路径、靶子实体、玩家属性
> 均有差异),绝对难度分永远对不齐真实体验——2D 蓝本的测评准是因为 AI 玩的就是游戏本体,
> 这里不成立。不做难度榜单、不做三档评分矩阵、不做无头批量、不做确定性自检。
> 保留的价值是**同环境相对比较**:同一张符卡改动前后、同一 Profile 下的数字对比依然
> 有效,服务编辑器调卡回路和 pilot 算法回归。

**产出**:轻量统计 + 预览小结(不建独立 eval 包,挂在现有 pilot/debug 旁即可)
- `PilotRunStats`:AI 试飞期间累计——被弹数、擦弹数、存活 tick、搜索层激活占比、
  最小间隙、pilot 平均耗时(封位触发数待 Phase 5)。中弹/擦弹计数走 Phase 1 统一扫掠
  检测器与 SelfBoxModel(统一真值原则),不依赖 `onTargetHit`。
- 展示:试飞停止/靶子死亡时在预览界面出一份小结(复用 Perf/Dbg 面板风格),
  可一键 dump JSON(挂到死亡回放的 `youkaishomecoming_exports/` 路径旁)。
- (可选)seed 注入按 C5 保留为一行开发者便利,供手动 A/B;不做任何依赖它的功能。
- **验收**:开 AI 试飞跑任一符卡,停止后统计小结数字合理(被弹数与死亡回放 dump 次数
  一致);JSON 可打开;改符卡参数重跑,统计随之变化。

### 专项 L — 激光精度强化(借鉴 Vertical_radar,可与 Phase 4 同 session)
> 背景:实测激光判断仍不准。根因(已核实代码):`ObservedMotionProvider.captureLaser`
> 把朝向和锚点**全部冻结**在 t=0(源码注释自己承认"Orientation frozen on T3")——
> 旋转/平移激光在 T3 下被当成静止光束,自机躲开当前光束后被扫过来的转动打中。
> 参考实现:`D:\IdeaProjects\Vertical_radar\src\main\java\com\litemingiewpoint_radar
adar\VerticalRadarRenderer.java`
> 的 `createLaserSnapshot`(它对激光几何的取数是对的,虽然它只画不预测)。

- **T3 旋转外推(主修)**:`SimplifiedProjectile` 自带 `xRotO/yRotO`——免费的一帧旋转历史。
  角速度 = wrapDegrees(rot − rotO)/tick,逐帧外推朝向;yaw 过 ±180° 用 wrapDegrees 防跳变;
  角速度做上限 clamp(防插值噪声)。有余力再升级成 2~3 帧朝向历史平滑(复用 history map 模式)。
- **T3 锚点外推**:激光基座位置同样走位置历史外推(现在 `captureLaser` 连 anchor 都是冻结的,
  平移激光同样失真);激光进入现有 history map,与普通弹幕同款二阶拟合。
- **长度语义**(radar 用法:`active ? getLength() : effectiveLength(pTick)`):
  `effectiveLength` 含 `earlyTerminate`(激光被方块截断后的实际长度,每 tick 由真实 block clip
  写回)。实现时先核实实体命中是否同样被截断——若是,active 帧也用 `effectiveLength(0)`,
  避免对已被墙挡住的光束段过度回避;若命中用全长,则维持 `getLength()`(游戏真值优先)。
- **新生激光忽略**:radar 规则——`tickCount < 2` 跳过(插值垃圾)、`percentOpen ≤ 0.01` 跳过。
  pilot 的 `isHitWindowOpen` 已覆盖后者,补前者即可。
- **验证项(先做)**:实际游玩路径扫的是 `ClientDanmakuCache` 虚拟弹幕——确认客户端侧
  `ItemLaserEntity.mover` 是否随 spawn data 同步(决定 T1 在游玩场景是否生效)。
  用现有 debug log 加一条 T1/T2/T3 命中占比统计:若游玩时激光全落 T3,本专项就是
  激光精度的主要来源;若 T1 生效,则主修项收益集中在其他模组/不可预测激光。
- (可选)`ThreatFilters.resolveOwner` 借鉴 radar `DanmakuCompat` 的反射 `getOwner` 兜底,
  降低其他弹幕模组(如 Arcadian Dream)友方弹幕的误判敌意——保守可不做。
- **大弹判定不需要借**:radar 的 `BbWidth/2` 与 pilot 现行 `danmakuHitRadius`(含大玉/泡泡
  修正)一致或更粗,已无差距。
- **验收**:预览里放旋转激光符卡(RotateMover 驱动)强制走 T3(临时禁用 T1 注册),
  AI 能预判扫动方向提前让位,而不是贴着旧光束位置被扫中;死亡回放中激光帧朝向逐帧变化。


### 专项 J — 抖动治理(专项 L 的后续,调参为主)
> 背景:专项 L 修好激光旋转/锚点外推后,实测**移动抖动明显增多**。这不是回归 bug,
> 而是力场性质变化:修复前朝向冻结 → 斥力方向错但稳;修复后方向对但会随光束扫动翻面。
> 2D 蓝本文稿对同类问题的既有经验:**势场震荡 → 加死区 + 调小输出力;搜索抖动 →
> 剪高速反向分支**。这些机制在代码里都已存在(死区/低通:`PotentialFieldSolver.java:134-143`;
> 反向剪枝:`FreeFlightModel.actions()`),本专项是**重新调参**,不是补机制。

已定位的三个震荡源(均已对照代码核实):
1. **激光垂直斥力翻面**:斥力方向 = 从光束线段最近点垂直推开
   (`PotentialFieldSolver.awayFromThreat`,约 line 205-216)。光束扫过自机瞬间,
   最近点从身体一侧跳到另一侧,斥力方向 180° 反转。
2. **激光近场力度过陡**:`PotentialFieldSolver.java:96-101`——sizeBoost ×1.8,
   3 格内 falloff 地板 `max(0.08, d*0.5)`,近场力必然触顶 maxForce。
   "大力 + 方向翻面"是抖动主源;死区只滤小力,对此无效,只有阻尼能压。
3. **模式乒乓(疑似,先确认)**:扫动光束让 minClearance 周期波动,若波幅超过
   `searchEnterClearance`/`searchExitClearance` 迟滞带宽,APF↔搜索高频互切
   (`DodgePilot.tick` 迟滞逻辑)。Perf 面板 mode 是否高频闪烁可直接确认。

**执行步骤(按文稿经验的优先序,调一步测一步)**:
1. 先加**量化指标**再调参:在 debug/统计里加"输出速度方向反转率"
   (相邻 tick 期望速度夹角 > 90° 的次数/秒)与"mode 切换次数/秒"。
   用旋转激光符卡跑 30s 记录基线——没有数字的调参都是玄学。
2. **加大阻尼**:`profile.damping()` 上调(如 0.5→0.7)。低通是唯一能压
   "大力翻方向"的手段;代价是反应迟半拍,由搜索层兜底。
3. **调小激光近场力**:falloff 地板 0.08 抬到 ~0.3,或 sizeBoost 1.8 降一档。
   文稿"调一下输出力的大小"的字面执行。
4. **适度加大死区**:滤远处光束扫动的小幅扰动。
5. 若 mode 确认高频闪:**拉宽迟滞带**或给搜索模式加最短驻留 tick(如 5 tick)。
6. (仅当 1-5 调完仍抖)结构性小改:旋转激光的斥力方向改为对前瞻数帧的
   away 向量做时间加权平均(方向低通)。动结构前必须有第 1 步的数据支撑。

**约束**:所有调整值进 `PilotProfile` 或常量并注释调参依据(执行须知 5);
逐步提交,每步附反转率前后对比数字。

**验收**:旋转激光符卡 AI 试飞 30s——方向反转率显著低于基线(目标 < 基线 1/3);
mode 切换频率恢复低频;在 test_fire_danmaku 与 2~3 张此前正常的符卡上回归,
存活表现不劣化(抖动治好但躲不动了 = 白治);Dbg 力箭头肉眼无高频翻面。


### Phase 5 — 球面缝隙投影(防封位)
**产出**:`content/spell/pilot/gap/` 包
- Fibonacci 球面 64~128 方向采样,对每方向求"最早被预测弹幕封锁的时刻",聚类出开放锥;
  跟踪开放锥立体角随预测时间的收缩率,超阈值时向最大开放锥输出逃逸偏置力(叠加进势场合力)。
  **角分辨率意识**:128 方向的相邻间距约 20°,窄于此的缝隙对本层不可见——这在
  "低频防封位"定位下可接受(钻窄缝是搜索层的职责,本层只管宏观逃逸方向),
  但找到候选开放锥后应在锥内局部加密二次采样,把逃逸方向做精。
- 该层低频运行即可(每 2~4 tick 一次,结果缓存)。
- **与 Phase 3 的职责边界(已定)**:本层负责提前发现更大的活动空间，改善只会后退、局部
  游荡和缺乏主动穿缝的问题；Phase 3 的局部锥细搜只在 14 个基础方向全堵时紧急触发，负责
  近身窄缝。两者可复用球面/锥采样工具，但不得合成一个每 tick 全量 128 射线的昂贵流程。
- 宏观层使用固定的守擂锚点与舒适半径，并对路线连续性、离锚距离计分；锚点不能每 tick
  跟随自机重建，否则约束永远追不上漂移。最大开放锥足够显著时允许朝前方或侧方主动穿越，
  不再固定偏好向后逃逸。
- 上线后给 Phase 4 的试飞统计补上封位触发数,并用几张常用符卡的统计小结做前后对比,
  确认该层只救命、不改变正常符卡下的表现。
- **验收**:构造一个"收缩包围网"测试符卡(球面收缩弹幕,JSON 导出到测试资源),
  无此层必死、有此层能提前钻出;测试符卡的出口锥角设计为略大于采样分辨率
  (~25°,确保采样密度对得上验收标准);正常符卡下该层基本不触发(日志统计触发率 < 5%)。

### Phase 6 — Boss 接入(P3)
**产出**:`content/entity/movement/PilotDodgeController.java`
- 实现 `BossMovementController` 接口,内部持 `DodgePilot`(低参数档:深度 2~3、方向 14、
  无球面层或 32 射线),替换 `DodgeController` 的注册点(全局搜索 `new DodgeController` 的使用处)。
- **输出协议适配(已核实的坑,不能想当然"接口不用改")**:
  `CompositeMovementController.MovementResult.getScaledMovement` 会把方向 `normalize()`
  后乘 baseSpeed × speedMultiplier(`CompositeMovementController.java:109-111`),Pilot 输出的
  速度幅值会被丢掉;且 `YoukaiAttackGoal` 对结果还有 0.8/0.2 的 EMA 平滑
  (`YoukaiAttackGoal.java:116-120`)——Boss 并非速度直控,执行器有一阶滞后。处置二选一,
  以死亡回放验证误差取小者:
  (a) 适配器返回归一化方向 + **动态覆写 `getSpeedMultiplier()` 编码幅值**,并在 Pilot 的
  自机运动模型中把 EMA 滞后计入(搜索节点用平滑后的实际速度递推,而非期望速度);
  (b) Pilot 控制器激活期间旁路 EMA 直写速度(需改 `YoukaiAttackGoal`,评估对其他控制器的影响)。
- 服务端预算:威胁快照构建限扫描半径 12 格 + 弹幕数上限截断(取最近 N 颗);
  Profile 提供 `SERVER_BUDGET` 档;**服务端默认只启用 T2/T3 预测档,T1 精确档由 config
  开关控制且默认关闭**(6 tick 前瞻内精度差异可忽略,省去白名单路由开销)。
  **保留旧 DodgeController 类不删**,加 config 开关可回退。
- 帧间分摊:搜索层允许隔 tick 运行,中间 tick 沿用上次计划路径;
  另监听投射物出生事件(服务端 `EntityJoinLevelEvent`)触发立即重规划,
  弥补"凭空出现的弹幕无法预测"的弱点(参考 MLM 的出生即响应)。
- **验收**:runClient 实测玩家用弹幕符卡打 Boss,肉眼可见闪避变聪明;
  spark/profiler 确认服务端 tick 无明显新增热点;config 关闭后行为回到旧版。

### Phase 7 — 玩家自动闪避 buff(P2)
> **平衡性/获得渠道动手前须向用户确认;移动权柄与 MLM 共存均已定案,不再是开放问题。**

- **移动权柄(已定)**:照搬 MovesLikeMafuyu 的验证方案(见第 3 节参考实现)——
  服务端负责效果获得(食物/仪式事件加 MobEffect),决策与执行在**客户端本地玩家** tick 上跑
  (`isLocalPlayer()` 检查 + `setDeltaMovement`)。玩家移动是客户端权威,零拉扯感。
  同样监听 `EntityJoinLevelEvent` 做出生即响应。
- **分级设计(2026-09-04 已重定,覆盖旧 Rescue/Assist/Takeover 方案)**:
  - 玩家可见称谓统一为 **I 初阶(Basic) / II 进阶(Enhanced) / III 高阶(Advanced)**；称谓只说明
    强度，不暗示三种互不相干的工作模式。现有 `Rescue / Assist / Takeover` 文案与分支逻辑
    在实施时删除，`NOVICE / ADEPT / LUNATIC` 不再暴露给玩家。
  - 三档都运行同一套完整控制器：威胁预测、势场、时空搜索、障碍避让、宏观缝隙、紧急局部
    细搜和计划连续性一项都不能从低等级裁掉。否则 I/II 仍只会成为残缺的 III。
  - 等级只线性提高直观能力：最大移动速度、威胁扫描半径、预测前瞻，以及由 Profile 派生的
    搜索预算/局部细搜密度。必须保证 `III > II > I`，不能再用不同分支造成能力断层。
  - 威胁走通用 provider 链:东方弹幕、原版骷髅箭/恶魂火球、其他模组投射物一律可躲。
- **玩家输入权柄(已定)**:
  - 未按 Control 时，只要存在任意移动输入(WASD、Space、Shift)，本 tick 玩家输入完全接管，
    Pilot 不再混入或覆盖速度；无移动输入时才自动驾驶。
  - 按住 Control 时，W/S、A/D、Space/Shift 不直接写速度，而分别转成以玩家视角为基准的
    前后、左右、上下候选评分偏置。偏置只表达"希望往哪里走"，不能绕过碰撞预测；被预测为
    不安全的方向仍可拒绝。松开 Control 立即恢复普通输入权柄。
  - Control 单独按下而没有方向输入时不改变当前自主计划。实现时须处理原版冲刺键冲突，
    避免同一次按键既触发冲刺又作为 Pilot 修饰键。
- **公开配置收敛(已定)**:
  - 面向玩家/整合包只保留可解释参数：总开关、I 级基础速度、每级速度增量、I 级基础扫描
    半径、每级扫描半径增量。等级值统一按 `base + (level - 1) * step` 派生。
  - 预测前瞻、Top-K、搜索预算和局部细搜密度可由等级与扫描范围内部派生；迟滞阈值、APF
    权重、墙体 clearance、救场 pulse/cooldown 等算法细节留在 `PilotProfile`，默认不进入普通
    config 页面。只有出现明确的整合包调参需求时，才把单个高级参数重新暴露。
  - 现有 `rescue*`、`assist*`、`takeover*` 与六个逐档高/低速配置属于旧行为模型，迁移时
    直接删除，不保留第二套控制流。
- **与 MovesLikeMafuyu 的共存(已拍板)**:不做冲突处理——双方均为实验性质,
  实际同时启用两边闪避效果的场景可忽略。不写互斥代码,不做 compat 桥。
- **须先确认的设计决策**(列成清单写入 `docs/pilot-player-design.md` 交用户拍板):
  1. 获得渠道与档位对应:哪些食物/仪式给哪个等级、持续时间。
  2. 平衡钩子:救场消耗(饱食度/冷却/耐久)、PvP 场景(项目已有 PvpDanmakuStatus 体系)
     是否禁用、自动闪避是否故意保留擦弹以获取资源。
- 实现本体:MobEffect 注册 + 客户端 tick handler(构建 ThreatSnapshot,仅扫玩家周围
  敌对投射物)+ 按档位调 Pilot + GroundedModel 落地(含地形 CollisionOracle)。
- **验收**:创造模式分别给予三个等级，在同一符卡下确认三档算法功能一致且速度、扫描范围、
  前瞻/细搜能力单调增强；普通 WASD/Space/Shift 每 tick 完全接管；Control+方向键能让 Pilot
  安全地偏向指定方向；构造 14 个基础方向全堵但局部仍有窄缝的场景，确认二次细搜能脱出且
  不逐 tick 左右翻转；效果结束干净移除，无残留速度修正。

## 6. 执行须知(给执行 agent)

1. **阶段顺序执行**,Phase 0/1 是地基,禁止跳过直接写势场或搜索。整个计划是**跨多个
   session 的长期功能**:每个 Phase 是独立里程碑,单个 session 原则上只做一个 Phase,
   做完验收、提交、回写本文档进度即停,不要试图一口气连续实现全部八个阶段。
   P1 交付物 = Phase 0→3 的 AI 试飞(已完成)+ Phase 4 轻量统计;Phase 5 起均为增强。
2. 每阶段完成:`gradlew build` 通过 + 该阶段验收项逐条核对 + 独立 commit
   (信息格式 `feat(pilot): xxx / 中文`),不要一次提交跨多个 Phase。
3. 单元测试放 `src/test/java/`(若项目尚无 test 源集,Phase 0 顺手在 build.gradle 配好;
   若配置成本过高,退而用 GameTest 或独立 main 方法自检类,但必须留下可重跑的验证入口)。
4. 涉及预览 UI 的验收需要 `gradlew runClient` 人工确认,agent 无法自动完成的验收项
   在阶段总结中明确列为"待人工验证",不要谎报已验证。
5. 所有魔法数字进 `PilotProfile` 或常量类,并写注释说明调参依据;
   调试可视化和死亡回放是长期工具,做成可开关,不要事后删除。
6. 本文档若与实际代码冲突,以实际代码为准,并回头修订本文档(它是活文档)。
7. 遇到 C2 checklist 过不了又觉得可惜的 Mover,落 T3 即可,不要发明克隆方案。
   0.27.0 的算法、三档强度和输入权柄已定；后续若改变效果获得渠道或 PvP 平衡，另立产品决策。

## 7. 风险清单

| 风险 | 缓解 |
|---|---|
| 有状态 Mover 被预测污染 → 弹幕轨迹诡异且难排查 | T1 白名单制,拿不准的直接落 T3 观测外推;预测只走快照 |
| T1 精确预测过度工程化(逐 Mover 特调、维护负担) | T1 定位为可选增强档,白名单只收明显闭式实现;新增 Mover 不跟进也不影响功能 |
| 核心与本模组弹幕耦合 → 原版/其他模组投射物躲不了 | C2b 硬约束 + Phase 0 验收项"删 T1 后测试仍过";T3 provider supports 恒真兜底 |
| 搜索层在弹幕数暴涨时超预算 → 卡顿 | 节点预算硬上限 + 弹幕按威胁度截断 + 降深度优雅降级 |
| 势场震荡(2D 蓝本已知坑) | 死区 + 低通 + 迟滞切换,Phase 2 验收明确检查 |
| 激光几何建模错误(蓝本两次翻车点) | Phase 0 单测核对实体真实判定箱;死亡回放可视化复查 |
| 预览(client)与 Boss(server)共用核心时混入端依赖 | C1 硬约束;可在 build 时人工 grep `net.minecraft.client` 核查 pilot 包 |
| 试飞统计被误当成绝对难度 | 已定位为同环境相对参考(Phase 4 降级说明);UI 文案注明参考值 |
| 旋转/凭空激光预测失效(位置序列表示不了纯转向) | ThreatFrame 逐 tick 帧模型(Phase 0);激光走 T1 纯 Mover 直调;T3 兜底时按危险平面保守处理 |
| Boss 执行链丢幅值 + EMA 滞后,Pilot 以为在直控实际在漂 | Phase 6 输出协议适配二选一,死亡回放验证 |
| 与 MovesLikeMafuyu 自动闪避同时生效互相抢玩家速度 | 已拍板:不做冲突处理(双方均实验性质,冲突场景可忽略) |
| 闪避进墙/坠崖 | 已明确:主场景为空中飞行,障碍方块可能来自任意方向 → CollisionOracle 对每个候选位置做全向 isFree 检查,不只查地面 |
| 自机盒用错(原版盒 vs 弹幕收缩盒 vs 妖怪放大盒) | SelfBoxModel 按消费方×威胁类别出盒(第 2 节 5b),Phase 1 单测含"收缩盒与原版盒结果不同"用例 |
