# 符卡可视化编辑器 — 迁移状态与缺失功能 (2026-04-07 v6)

## 已迁移符卡 (17/17)

| 符卡 | 关键功能 |
|------|---------|
| SunnySpell | conditional × 3 色循环 RING |
| LunaSpell | AND/NOT 条件 4/6 间歇 |
| StarSpell | onTrail + elevation(90) 锥形扩散 |
| CirnoSpell | onExpiry + direction_to_target 分裂, angleOffset=180 |
| MystiaSpell | SpawnShooterAction + BurstAction(32,3) + RandomChoice 颜色 |
| LarvaSpell | BurstAction(wave_variable) + RepeatAction 翅膀衰减角度, target_on_ground |
| YoumuSpell | 多条件动态 (distance/speed/ground/health), 樱花斩 repeat+sin, 回旋 burst |
| SanaeSpell | 双模式 (near/far) 切换, 旋转星型激光, 五谷爆裂弹 (CONE+SPHERE_RANDOM), delayedMover |
| ClownSpell | EntityTrait 难度分支, DynamicTickInterval, 链式 TrailAction, 变量状态管理, SequenceAction |
| **SakuyaSpell** | 3阶段多层飞刀环+时停追踪+螺旋+暴风+十字激光, DecelerationConfig减速mover |
| **KisinSpell** | 3阶段(SummonNear/Wing激光/SummonFar延迟追踪), GaussianRandom+ABSOLUTE origin |
| **RemiliaSpell** | 5步循环(Sweep+分支激光+传送枪突), Clamp/Max速度, TeleportAction |
| **DoremiSpell** | Maze/Madness状态机, 7发射器42变量, 激光阵列+螺旋+冷却 |
| **KoishiSpell** | Lissajous参数激光(10/tick), 减速→追踪弹链, 边界ConfineTargetAction |
| **ReimuSpell** | 3级追踪弹链(expandRing→homingTrail→finalHoming), 拦截传送, border每tick环, on_hurt序列, abyssal定时器 |
| **YukariSpell** | hidden(6激光+105蝶弹)+延迟复制, CompositeMover蝴蝶(decel→zero→polar→accel), 螺旋激光120t, TeleportRandomAction |
| **MarisaSpell** | Boss自移动(set_velocity) + Heightmap地面激光 + AttachedMover主激光, 9-phase 数据驱动组合迁移 |

## Legacy 清零状态

> 当前所有 TLM boss/fairy 符卡均已接入数据驱动 SpellDefinition。
> 原最后阻塞项 B1/B4/D3 已在 Marisa 迁移中补齐。

### 本轮完成的原阻塞功能

| 分类 | ID | 缺失功能 | 影响文件数 | 状态 |
|------|-----|---------|-----------|------|
| **动作** | B1 | Boss 自身移动 (setDeltaMovement, dash) | 1 (Marisa) | 已实现 ✓ |
| **动作** | B4 | Heightmap 查询 (地面高度) | 1 (Marisa) | 已实现 ✓ |
| ~~**动作**~~ | ~~B5~~ | ~~设置实体 flag (视觉/phase 标记)~~ | ~~1 (Reimu)~~ | ~~已实现~~ ✓ |
| ~~**事件**~~ | ~~C1~~ | ~~`on_hurt` 回调 — 受伤反击~~ | ~~2 (Reimu, Yukari)~~ | ~~已存在~~ ✓ |
| ~~**事件**~~ | ~~C2~~ | ~~Per-Spell 自定义伤害类型~~ | ~~2 (Reimu, Yukari)~~ | ~~已实现~~ ✓ |
| **Mover** | D3 | `AttachedMover` (激光跟随施法者) | 1 (Marisa) | 已实现 ✓ |

### 历史阻塞详情 (已全部清零)

| 符卡 | 行数 | 阻塞项 | 迁移状态 |
|------|------|--------|---------|
| ~~LarvaSpell~~ | ~~90~~ | — | ~~已迁移~~ |
| ~~SanaeSpell~~ | ~~189~~ | — | ~~已迁移~~ |
| ~~KoishiSpell~~ | ~~163~~ | ~~B2~~ | ~~已迁移~~ — ConfineTargetAction 替代强制移动 + Lissajous参数激光 |
| ~~ClownSpell~~ | ~~205~~ | — | ~~已迁移~~ |
| ~~KisinSpell~~ | ~~294~~ | ~~E3, E4~~ | ~~已迁移~~ — 用3阶段PhaseDefinition+GaussianRandom近似替代SubSpell |
| ~~RemiliaSpell~~ | ~~175~~ | ~~B1, B3~~ | ~~已迁移~~ — TeleportAction替代raycast, 枪突简化为弹幕线 |
| ~~MarisaSpell~~ | ~~394~~ | ~~B1, B4, D3~~ | ~~已迁移~~ — 9 phase 数据驱动实现, 不再走 LegacySpellBridge |
| ~~ReimuSpell~~ | ~~358~~ | ~~B5, C1, C2~~ | ~~已迁移~~ — 3级onExpiry链+拦截传送+border变量+on_hurt序列+abyssal定时器 |
| ~~YukariSpell~~ | ~~294~~ | ~~C1, C2~~ | ~~已迁移~~ — CompositeMover蝴蝶+hidden激光阵+TeleportRandomAction+螺旋激光 |
| ~~SakuyaSpell~~ | ~~589~~ | — | ~~已迁移~~ — 3阶段飞刀系统 |
| ~~YoumuSpell~~ | ~~811~~ | — | ~~已迁移~~ |
| ~~DoremiSpell~~ | ~~194~~ | ~~E5~~ | ~~已迁移~~ — 用Java for循环生成42变量SetVariable替代数组 |

## 推荐下一步实现优先级 (按解锁符卡数排序)

### 已完成 (本次批量实现)

- ~~A1: `target_on_ground`~~ ✓
- ~~A2: `target_speed`~~ ✓
- ~~A3: `entity_trait` (is_lunatic, is_chaotic)~~ ✓
- ~~A4: `random_chance`~~ ✓
- ~~A5: `dynamic_tick_interval` (变量驱动周期)~~ ✓
- ~~A6: `compare` (NumberProvider 比较条件)~~ ✓
- ~~A7: `variable_check` (变量==常量)~~ ✓
- ~~D1: `setDelayedMover` (激光 delayed_v0/delayed_v1)~~ ✓
- ~~D2: 链式 TrailAction + 变量快照~~ ✓
- ~~E1: DelayAction 支持 NumberProvider 表达式~~ ✓

### 新增 NumberProvider 类型

- ~~`random_choice` — 从离散列表随机取值~~ ✓
- ~~`conditional` — 基于 SpellCondition 返回 if_true/if_false~~ ✓

### 剩余未迁移符卡的优先级 (已更新 2026-04-07)

1. ~~**C1 + C2: `on_damage` hook + Per-Spell 伤害类型**~~ ✓ 已实现
2. ~~**B5: 设置实体 flag**~~ ✓ 已实现
3. ~~**ReimuSpell 迁移**~~ ✓ — 已迁移, 3级onExpiry链+拦截传送+border变量
4. ~~**YukariSpell 迁移**~~ ✓ — 已迁移, CompositeMover蝴蝶+TeleportRandomAction
5. ~~**B1 + B4 + D3: Boss移动 + Heightmap + AttachedMover**~~ ✓ 已实现并完成 Marisa 迁移
   - `set_velocity` action: 自身方向速度控制
   - `heightmap_y(x, z)` provider: 地形高度查询
   - `attached` mover config: 激光跟随施法者

### 当前结论

- 所有 17/17 符卡都已迁移到数据驱动 SpellDefinition
- `TouhouSpellCards` 中 Marisa 已不再注册 legacy `MarisaSpell::new`
- 后续若继续推进, 应转向编辑器易用性或新的 gameplay 功能, 不再是 legacy 迁移补洞

### 已通过简化方案解决的原阻塞项

- ~~B2 (强制移动玩家)~~ — KoishiSpell 用 ConfineTargetAction 替代 ✓
- ~~B3 (raycast)~~ — RemiliaSpell 用 TeleportAction + 距离条件替代 ✓
- ~~E3/E4 (嵌套SubSpell)~~ — KisinSpell 用3阶段Phase替代 ✓
- ~~E5 (7参数随机数组)~~ — DoremiSpell 用 Java for循环生成42个SetVariable ✓
- ~~E6 (跨阶段状态)~~ — SakuyaSpell 用3独立Phase + 各自onExit清屏替代 ✓

---

## 本次变更总结 (2026-04-07 batch 6)

### 新迁移符卡: MarisaSpell

**MarisaSpell** (`MigratedSpellCards.marisa()`, 9 phases):
- `select` 相位按距离/目标速度/血量/随机变量选择 `DashStar` / `MasterSpark` / `EarthLight` / `BlackHole`
- `phase3_intro` 复现 10% 血量阶段切换前的 50 tick 空窗
- `dash_star` 用 `set_velocity` 驱动 boss 冲刺, 同步沿路径散射彩色 STAR
- `earth_light` 用 `heightmap_y(x, z)` 在目标附近地表生成红/蓝激光
- `master_spark` 用 `attached` mover 绑定主激光, 后续按血量分支生成前/后向 STAR 与 SPARK
- `combo_earth_spark` / `combo_dash_hole` / `combo_phase3` 复现 legacy 的双 ticker 组合期

### 新增基础设施

- `SetVelocityAction` (`set_velocity`) — 数据驱动设置施法者速度, 用于 dash/自移动 boss
- `NumberProviders.HeightmapY` (`heightmap_y(x, z)`) — 数学表达式/Origin 可直接读取地面高度
- `MoverConfigs.AttachedMoverConfig` (`attached`) — 让激光等弹幕持续跟随施法者
- Preview fake caster 现在会按 `setDeltaMovement` 前进, 预览中可见 boss 位移

## 本次变更总结 (2026-04-06 batch 5)

### 新迁移符卡: ReimuSpell + YukariSpell

**ReimuSpell** (`MigratedSpellCards.reimu()`, ~210行):
- 5步周期: steps 0-2 shoot, steps 3-4 intercept (dist>40时传送)
- 3级 onExpiry 追踪弹链: expandRing(LIGHT_GRAY decel) → homingTrail(PURPLE direction_to_target) → finalHoming(RED/BLUE)
- 距离自适应参数: perc = clamp((dist-16)/24, 0, 1), 驱动 r0/t0/termSpeed
- border: 变量 $border 控制每tick 8发 BALL YELLOW 环
- intercept: TeleportAction + 8×RepeatAction 旋转 BUBBLE YELLOW
- on_hurt: SetVariable(border,1) + HealthBelow(0.5)→SetEntityFlag(abyssal) + ConditionalAction(abyss? 3×BurstAction : 1×BurstAction)
- tick>2400: 自动设置 abyssal flag
- 颜色→伤害类型: BLUE/YELLOW 弹幕使用 DanmakuDamageType.ABYSSAL

**YukariSpell** (`MigratedSpellCards.yukari()`, ~150行):
- hidden(): 6 MAGENTA 激光环 + 6 PURPLE 扩张泡 + 3×35 PURPLE 蝶弹网格 (GRID pattern)
- 蝴蝶: 2×100发 CompositeMover(decel 40t→zero 10t→polar 40t→accel 40t), CYAN 正旋/MAGENTA 反旋
- 螺旋激光: BurstAction(120, 1) 双色 (RED/BLUE), 角度递增 3°/tick
- 冷却系统: $cd 变量每tick递减, 控制攻击频率 (蝶60t, 激光120t)
- on_hurt: TeleportRandomAction(32, 0.8, 0.4, 16, true) + hidden 弹幕
- 全局 abyssal 伤害类型

### 新增基础设施

**TeleportRandomAction** (新增, 69行):
- 高斯随机方向传送, 16次尝试, 碰撞检测
- 参数: maxDistance, minDistanceFactor, distanceVariance, attempts, upwardBias, playSound
- 编辑器完整支持 (6个参数行)
- 注册为 `"teleport_random"` 类型

### 编辑器增强

1. **Polar Mover 完整参数** — 新增 `Rad Acc` (径向加速度) 和 `Ang Acc` (角加速度) 编辑行
2. **Composite Mover 编辑器** — 完整的段列表编辑:
   - 显示段数量, 每段独立的 Duration + 子 Mover 类型选择器
   - 子 Mover 内联参数编辑 (Acceleration/Deceleration/Rotate/Polar/Zero)
   - [+] Add Segment / [-] Remove Last Segment 按钮
3. **TeleportRandomAction 编辑器** — 6个参数行 (Max Dist, Min Dist %, Dist Var, Attempts, Up Bias, Sound)
4. **ActionListPanel 颜色** — TeleportRandomAction 使用与 TeleportAction 相同的绿色
5. **MOVER_TYPES** — 新增 "composite" 到类型选择器

---

## 本次变更总结 (2026-04-06 batch 4)

### 新增基础设施: Per-Spell 弹幕伤害类型系统 (C2)

**设计**: 在弹幕实体上添加 `damageTypeOverride` 字段, 由 `fire_danmaku`/`fire_laser` 的 `damage_type` 参数设置。

**实现文件:**

- `DanmakuDamageType.java` (新增, 63行) — 伤害类型枚举 (`DANMAKU`, `ABYSSAL`), 带 Codec 和 `resolve()` 方法
- `IYHDanmaku.java` (+15行) — 新增 `getDamageTypeOverride()` default 方法; `source()` 方法优先检查 override
- `ItemDanmakuEntity.java` (+8行) — 新增 `damageTypeOverride` 字段和 `getDamageTypeOverride()` 实现
- `ItemLaserEntity.java` (+10行) — 同上, 激光实体也支持伤害类型覆写
- `FireDanmakuAction.java` (+15行) — record 新增 `damageType` 字段 (Optional<DanmakuDamageType>), Codec 扩展, `emitDanmaku()` 注入
- `FireLaserAction.java` (+10行) — 同上, record 新增 `damageType` 字段, `execute()` 注入

**JSON 格式:**
```json
{
  "type": "fire_danmaku",
  "damage_type": "abyssal",
  "bullet": "circle",
  "color": "blue",
  ...
}
```

**解析优先级**: `per-danmaku override > CardHolder.getDanmakuDamageSource() > YHDamageTypes.danmaku()`

### 新增动作: SetEntityFlagAction (B5)

**文件:** `SetEntityFlagAction.java` (新增, 34行)

**JSON:** `{"type": "set_entity_flag", "flag": 4, "enable": true}`

在 `SpellActions` 注册为 `"set_entity_flag"` 类型。
支持设置 YoukaiEntity 的 bitfield flag (flag 4 = abyssal, flag 16 = feed CD, flag 32 = rage)。

### 新增条件: EntityFlagCondition

**文件:** `SpellConditions.java` (+20行)

**JSON:** `{"type": "entity_flag", "flag": 4}`

检查施法者 (YoukaiEntity) 是否设置了指定的 flag。

**EntityTrait 扩展:** 新增 `"is_abyssal"` trait (等价于 `entity_flag(4)`, 但语义更清晰)。

### 新增 NumberProvider 类型

- `target_fly_time` — 目标离地时间 (tick), `{"type": "target_fly_time"}`
- `target_speed` — 目标水平速度 (blocks/tick), `{"type": "target_speed"}`

### SpellContext 扩展

- `targetFlyTime()` — 返回目标离地时间

---

## 已实现功能: 贝塞尔曲线 Mover ✓

### 实现文件

- `BezierMover.java` (99行) — 继承 `TargetPosMover`, 实现三次贝塞尔曲线运动
- `MoverConfigs.BezierMoverConfig` — CODEC + 方向相对偏移参数 (forward/right/up)
- 编辑器 UI 完整支持 10 个参数行 (CP1/CP2/End 各 3 轴 + duration)

### 实现细节

- P0 = 发射位置（自动从 origin 获取）
- P1/P2/P3 = origin + 方向相对偏移 (forward=速度方向, right=ori.side(), up=ori.normal())
- 到达终点后沿最终速度方向直线延伸 (bezierDerivative 计算 t=1 时切线)
- 在 `ActionEditorPanel` 中注册为 "bezier" mover 类型

### JSON 格式

```json
{
  "type": "bezier",
  "cp1_forward": 5, "cp1_right": 3, "cp1_up": 0,
  "cp2_forward": 10, "cp2_right": -3, "cp2_up": 0,
  "end_forward": 15, "end_right": 0, "end_up": 0,
  "duration": 40
}
```

### 注意事项

- tick 是整数，短曲线 (<10 tick) 会有离散感，建议 duration >= 20
- 控制点是世界坐标绝对偏移，创建后不随目标移动
- 可与 `CompositeMover` 串联实现多段复合轨迹
- `TargetPosMover` 基类自动计算速度/朝向，无需手动处理

---

## 本次变更总结 (2026-04-04 batch 2)

### 新迁移符卡: SanaeSpell + ClownSpell

**SanaeSpell** (`MigratedSpellCards.sanae()`, ~200行):
- 双模式: near (距离<35 且 目标在地面) / far 自动切换
- Near 模式: 每10tick 追踪弹 + 每40tick 双旋转星型 PENCIL 激光 (BurstAction 40波, 9°/tick)
  - 使用 `OriginConfig.CASTER_FACING` 偏移 ±5.66 格实现卫星点
  - `delayedMover(v0=4.5, v1=1)` 实现激光延迟加速
- Far 模式: 每20tick 五谷爆裂 (BurstAction 5子波 × RepeatAction 5发射点)
  - 垂直环发射点: sin/cos(gi×72°)×12 偏移
  - RING(5发, elevation=72°) 锥形 + onExpiry SPHERE_RANDOM 爆炸
  - `NumberProviders.Distance` 驱动速度: 0.5 + dist/30

**ClownSpell** (`MigratedSpellCards.clown()`, ~240行):
- `Conditional` NumberProvider: `is_lunatic ? 30 : 60` 控制周期
- `DynamicTickInterval`: 用变量 `$cycle` / `$dur` 驱动触发间隔
- `CompareNumbers`: `phaseTick % $dur < $dur * 2/3` 实现前4/6步限制
- 链式 TrailAction + 变量快照: MENTOS → onExpiry → LASER + 静止 MENTOS
- `DelayAction(NumberProvider)`: `$pair * 10` 实现错开发射
- `SetVariable/AddVariable`: 状态管理 (kind, round, base_tilt, k1t)
- `SequenceAction`: 先设置随机变量再执行 burst

### 新迁移符卡 (2026-04-05 batch 3): SakuyaSpell + KisinSpell + RemiliaSpell + DoremiSpell + KoishiSpell

**SakuyaSpell** (`MigratedSpellCards.sakuya()`, ~320行):
- 3阶段: 100%-67% KnifeRing+Spiral → 67%-33% TimeStop+Sweep+Spiral → 33%-0% TimeStop(强)+双Spiral+Storm+CrossLaser
- DecelerationConfig: 飞刀扩张后减速停止, onExpiry → 冻结标记 → 追踪弹
- RepeatAction 多层: 2-3层飞刀环 (GRAY/LIGHT_GRAY/WHITE, 不同速度和角度偏移)
- Java Function 辅助: `mkSpiral` lambda 创建参数化螺旋 BurstAction

**KisinSpell** (`MigratedSpellCards.kisin()`, ~100行):
- 3阶段: SummonNear(双向散布弹) → Wing(翼激光BurstAction) → SummonFar(ABSOLUTE坐标延迟追踪)
- GaussianRandom 散布 + ByVariable 颜色交替
- ZeroMoverConfig 静止标记弹 → onExpiry DirectionToTarget 追踪

**RemiliaSpell** (`MigratedSpellCards.remilia()`, ~120行):
- 5步循环状态机: Mod(PhaseTick/20, 5) 驱动
- Sweep: 3层弹幕(BUBBLE/MENTOS/BALL, 递减速度)
- Lasers: 4组分支激光(父+3子×120°间隔, 从端点发射)
- Spear: TeleportAction传送 + 80发密集弹幕线

**DoremiSpell** (`MigratedSpellCards.doremi()`, ~200行 + 50行辅助):
- Maze/Madness 双模式状态机 (groundTime/cooldown/mazeTime/madTime 计数器)
- Maze: 2×8×12=192条激光阵列 (RotateConfig旋转) + 80tick螺旋弹幕
- Madness: Java for循环生成7发射器×6参数=42个随机变量 + BurstAction(100tick)
- buildMadnessEmitter/buildMadnessFullAction 辅助方法

**KoishiSpell** (`MigratedSpellCards.koishi()`, ~90行):
- Lissajous 参数曲线激光: cos(1.47t)*cos(t)/sin(t) × 32格半径, 10条/tick
- 减速→追踪弹链: DecelerationConfig(0.04) → onExpiry DirectionToTarget
- 24发 RING + Border预测弹 (dist>26)
- ConfineTargetAction(32, 1.0): 边界限制替代强制移动

### 基础设施增强

**新增条件类型** (`SpellConditions.java`, +78行):
- `EntityTrait(trait)` — 检查 boss 实体特征 (is_lunatic, is_chaotic)
- `DynamicTickInterval(period, offset)` — NumberProvider 驱动的周期条件
- `CompareNumbers(left, op, right)` — 两个 NumberProvider 的比较 (<, >, ==, !=, <=, >=)

**新增 NumberProvider 类型** (`NumberProviders.java`, +40行):
- `RandomChoice(values)` — 从离散列表随机取值
- `Conditional(condition, ifTrue, ifFalse)` — 基于条件分支返回不同值

**新增 PatternType** (`PatternType.java`, +47行):
- `SPHERE_RANDOM` — 随机均匀球面分布 (uniform in sin(phi) space, 避免极地聚集)
- `SPHERE` 改为 Fibonacci 黄金角均匀分布 (取消 outerCount, 仅用 count)
- `NESTED_RING` tilt_angle 语义增强: 0°=vertical(orange-slice), 90°=perpendicular(stacked-hoop)

**FireLaserAction 增强** (+74行):
- 新增 `elevation` 字段 (激光仰角偏移)
- 新增 `delayed_v0` / `delayed_v1` 可选字段, 复现 legacy `setDelayedMover(v0, v1, prepare, setup)`
- 兼容构造器保证向后兼容

**FireDanmakuAction 增强**:
- NESTED_RING: tilt_angle 控制内环轴向 (0°=vertical, 90°=perpendicular, 任意值混合)
- SPHERE: 改用 Fibonacci 黄金角均匀分布, 取消 latitude×longitude 网格
- SPHERE_RANDOM: 新增 area-correct 随机球面分布

**DataDrivenTrailAction 增强** (+31行):
- 变量快照: 创建时保存 runtime variables, onExpiry 执行时临时恢复
- 解决 BurstAction 中 per-wave 变量 ($lw) 在延迟执行时值已变化的问题

**DelayAction 增强**:
- `delayTicks` 从 `int` 改为 `NumberProvider`, 支持 `$pair * 10` 等表达式

**SpellRuntime 修复**:
- `executeScheduledActions` 先快照再执行, 防止执行中 scheduleDelayed 导致 ConcurrentModificationException

### 编辑器增强

**透视 (Perspective) 相机模式** (`OrthographicViewport.java`, +349行):
- FPS 风格自由相机: WASD 移动 + 鼠标自由视角 (左键点击进入捕获模式)
- 右键拖拽 = 轨道旋转, 中键拖拽 = 视平面平移
- 滚轮调整飞行速度 (0.05 ~ 5.0)
- "BindTgt" 按钮: 将目标假人绑定到相机位置 (第一人称体验弹幕)
- 正交/透视切换按钮, ESC 退出捕获 → 再次 ESC 退出透视模式
- 透视渲染: 独立 glViewport + 透视投影矩阵 + 正确的深度缓冲清除

**SpellPreviewScreen 增强** (+185行):
- 透视模式的完整输入处理 (鼠标捕获/释放, WASD 抑制, ESC 层级退出)
- `removed()` 自动保存: 关闭编辑器时自动持久化到 SpellRegistry + 磁盘
- 光标状态正确恢复 (防止退出时光标隐藏)

**ActionEditorPanel 增强** (+123行):
- Bezier mover 编辑器: 10 个参数行 (CP1/CP2/End 各 forward/right/up + duration)
- FireLaserAction: 新增 Elevation 行, Delayed Mover 添加/删除按钮
- DelayAction: delayTicks 改为 NumberProvider 行 (支持变量表达式)
- NESTED_RING: 强制显示 "Axis Tilt" 行 (默认 0°), 移除 SPHERE 的 outerCount 行
- MOVER_TYPES 增加 "bezier"
- **SequenceAction 完整支持**: 可创建、展开子节点树、添加/删除/拖拽子 action、折叠/禁用

**ActionListPanel: SequenceAction 容器支持**:
- `hasChildren` / `buildActionTree`: 子 action 以 "actions" 分支显示, 带 [+] 按钮
- `doReplace` / `doInsert` / `doDelete` / `getActionRecursive`: 递归编辑/插入/删除/导航
- `collapseAllRecursive` / `doToggleDisabled`: 折叠和禁用切换
- 类型选择器新增 "Sequence" 按钮, `createDefaultAction` 支持空序列创建

### AimMode.DirectionToTarget 修正 — originPos 感知

`DirectionToTarget.getBaseDirection()` 原先始终从 `holder.center()`（施法者中心）计算指向目标的方向。
当 `OriginConfig` 设置了偏移（如垂直环上 12 格偏移的发射点）时，弹幕实际从偏移位置发射，
但方向仍然从施法者中心计算，导致弹幕不会射向目标。

**修复**: `AimMode` 接口新增 `default getBaseDirection(SpellContext ctx, Vec3 originPos)` 重载。
`DirectionToTarget` 覆写此方法，从实际发射位置 `originPos` 计算 `(target - originPos).normalize()`。
`FireDanmakuAction.execute()` 和 `FireLaserAction.execute()` 改为调用新重载。
其他 AimMode 实现不受影响（default 方法忽略 originPos）。

**影响**: SanaeSpell 五谷爆裂弹的 5 组发射点现在各自独立瞄准目标（收敛射击），与 legacy 行为一致。

### screenDeltaToWorldDelta 修正

`OrthographicViewport.screenDeltaToWorldDelta()` 的视图矩阵逆推公式修正:
- 修正了 view +X / view +Y 在世界空间的方向计算
- 之前: 右方向符号反转 + up 方向缺少 pitch 分量修正
- 现在: 正确推导 R^(-1) = R_y(-yRot) * R_x(-xRot) 的列向量

---

## dynamic_spell 物品实装计划

### 当前状态

`DynamicSpellItem` 代码已完整实现（castSpell、RuntimeItemSpell 驱动、NBT spell_id 解析、tooltip 显示），
目前已具备**开发/测试可用**的入口：
- `Youkai's Danmaku` 创造标签页会自动加入所有已注册 spell 的 `dynamic_spell` 测试栈
- `/yhspell give <player> <spell_id> [ticks]` 可直接发放指定 spell 物品

但它仍未完成正式物品化：
- 第一批 5 个简单已迁移符卡已配置 `SpellItemForm`
  - `sunny_milk`: `generate=true`, `cooldown=100`, `requiresTarget=false`
  - `luna_child`: `generate=true`, `cooldown=120`, `requiresTarget=false`
  - `star_sapphire`: `generate=true`, `cooldown=80`, `requiresTarget=true`
  - `cirno`: `generate=true`, `cooldown=100`, `requiresTarget=true`
  - `mystia_lorelei`: `generate=true`, `cooldown=140`, `requiresTarget=false`
- `SpellItemForm` 已新增独立 `duration` 字段；`DynamicSpellItem` 优先使用 `duration`，旧数据未填写时回退到 `cooldown`
- 所有 12 个 legacy 符卡也是 `NONE`（`LegacySpellBridge.fromLegacy` 硬编码）
- 当前创造标签页走的是 testing path：展示全部注册 spell，而不是仅 `itemForm.generate()==true` 的正式列表

### 实装需要的改动（按依赖顺序）

#### 步骤 1: 为已迁移符卡配置 SpellItemForm ⏳ 已部分完成

**文件:** `MigratedSpellCards.java`

将 `buildDefinition()` 方法拆分为两个版本，或增加 `SpellItemForm` 参数：

```java
private static SpellDefinition buildDefinition(ResourceLocation id, ResourceLocation mainPhase,
    PhaseDefinition phase, String modelId, SpellItemForm itemForm) { ... }
```

首批已落地：

| 符卡 | cooldown (tick) | requiresTarget | 理由 |
|------|----------------|----------------|------|
| sunny_milk | 100 (5s) | false | 全向 RING，不需要目标 |
| luna_child | 120 (6s) | false | 周期性全向弹幕 |
| star_sapphire | 80 (4s) | true | 锥形扩散需要方向 |
| cirno | 100 (5s) | true | direction_to_target 分裂 |
| mystia | 140 (7s) | false | Shooter + Burst 持续较长 |

剩余 migrated spell 仍需逐个验证是否适合玩家物品上下文，尤其是带 boss 自移动、受伤回调、强制位移或复杂 arena 逻辑的符卡。

#### 步骤 2: 接入 SpellItemAutoRegister ✅ 已完成（测试模式）

**文件:** `YHDanmaku.java` 或创建一个事件监听器

已在 `YoukaisHomecoming.buildCreativeTabContents()` 中接入创造标签页事件。
当前调用的是 `SpellItemAutoRegister.populateTestingTab()`，会把所有已注册 spell 加入 `Youkai's Danmaku` 标签页，方便调试。

正式物品化时仍建议改回只展示 `itemForm.generate()==true` 的列表，例如：

```java
// 在 YHDanmaku 的创造标签页构建回调中:
SpellItemAutoRegister.populateCreativeTab(output);
```

注意：创造标签页事件在客户端触发时，`SpellRegistry` 需要已被填充。
`TouhouSpellCards.registerSpells()` 在 `commonSetup` 阶段调用，应在标签页事件之前。

#### 步骤 3: 添加 /yhspell give 命令 ✅ 已完成

**文件:** `YHCommands.java`

现有命令：
``` 
/yhspell give <player> <spell_id> [ticks]
```

行为：
```java
// natural duration
DynamicSpellItem.createStack(YHDanmaku.DYNAMIC_SPELL.get(), spellId);
// fixed duration
DynamicSpellItem.createStackWithDuration(YHDanmaku.DYNAMIC_SPELL.get(), spellId, ticks);
```

支持 spell ID 补全；`player` 可用单人名或选择器（如 `@s`, `@a`）。

#### 步骤 4: 对 legacy 符卡的物品化决策

legacy 符卡通过 `LegacySpellBridge` 也在 `SpellRegistry` 中。它们的 `RuntimeItemSpell`
路径是否可用需要逐个验证：

| 符卡 | 物品化可行性 | 原因 |
|------|------------|------|
| LarvaSpell | **可行** | 简单弹幕，PlayerHolder 可支持 |
| ~~SanaeSpell~~ | ~~**已迁移**~~ | ~~数据驱动, 双模式切换~~ |
| KoishiSpell | **不可行** | 强制移动玩家，不适合物品使用 |
| ~~ClownSpell~~ | ~~**已迁移**~~ | ~~is_lunatic 条件在物品上下文返回 false (normal 模式)~~ |
| KisinSpell | **不可行** | 嵌套 SubSpell + 跨符卡注入 |
| RemiliaSpell | **不可行** | raycast 传送 + boss dash |
| MarisaSpell | **不可行** | boss 移动 + 激光跟随 |
| ReimuSpell | **不可行** | 7项阻塞，受伤回调 |
| YukariSpell | **不可行** | hurt 回调 |
| SakuyaSpell | **需验证** | 链式 Trail 可能可以简化版 |
| YoumuSpell | **需验证** | Ticker 简单但多条件 |
| DoremiSpell | **需验证** | 7参数随机数组 |

**建议:** 初期只对已迁移的 9 个符卡和确认可行的 legacy 符卡启用 `generate = true`。
其余符卡等完成数据驱动迁移后再启用。

#### 步骤 5: 物品模型与纹理差异化

当前所有 `dynamic_spell` 共用同一个模型 `item/spell/custom_spell`。
考虑利用 `SpellItemForm.iconItem` 字段区分不同符卡的图标，或：
- 使用 `ItemPropertyFunction` 根据 NBT `spell_id` 切换模型
- 或直接使用 tooltip 颜色/名称区分（最简单）

#### 步骤 6: 持续时间问题 ✅ 已完成基础拆分

`SpellItemForm.duration` 已加入并接入 `DynamicSpellItem`。
当前解析优先级：
1. stack NBT `duration`
2. `SpellItemForm.duration`
3. 兼容回退到 `SpellItemForm.cooldown`
4. 否则 natural end

首批 5 个 simple migrated spell 已显式配置 `duration`。
剩余 spell 仍需按具体 phase 时长逐个校准。

### 实施优先级（已修订）

1. **P0: Legacy 符卡数据驱动迁移** — 最高优先，在条件系统补全后逐个迁移
2. **P1: 创造模式测试调用** — ✅ 已完成
   - `/yhspell give <player> <spell_id> [ticks]`
   - `SpellItemAutoRegister.populateTestingTab()` 已接入 `Youkai's Danmaku` 创造标签页
3. **P2: dynamic_spell 基础物品化** — 进行中（首批 5 个 simple migrated spell 已配置）
4. **P3: 生存模式自定义符卡系统** — 见下文

### P3 构想: 生存玩家自定义符卡

与现有 spell 物品的设计完全不同。核心思路：

**入口:** 生存玩家通过某种途径（配方/NPC/解锁条件）获取打开 Preview 编辑器界面的权限。

**编辑:** 玩家在 Preview 界面中设计自己的符卡（弹幕样式、条件、参数等）。

**消耗计算:** 基于符卡实际效果的复杂度来计算制作成本：
- **实体数量** — 一段模拟时间窗口内同时存在的弹幕/激光实体峰值
- **Dummy 受伤** — 对测试假人造成的总伤害次数/总伤害量
- 两者结合得出一个"消耗系数"，映射为具体素材（弹幕碎片、妖力结晶等）

**制作:** 玩家支付对应素材，将设计好的符卡"铭刻"为可使用的 `dynamic_spell` 物品。

**平衡意义:** 越强力/越密集的弹幕模式消耗越多材料，形成自然的经济平衡。
杜绝"零成本无限弹幕"的问题。

> 此功能优先级最低，等其他系统完善后再考虑实装。
