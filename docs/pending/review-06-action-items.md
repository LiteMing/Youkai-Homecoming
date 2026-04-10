# 行动项清单

按优先级排列。标注 `[已修复]` 的项目已在本次审查中写入代码。

---

## 已修复

| 项 | 文件 | 修改 |
|----|------|------|
| BUG-1 | `DanmakuProxyEntity.java:316-319` | 添加 `isPickable()` 返回 `false` |
| BUG-2 | `DynamicSpellItem.java:158-167` | `resolveSpellDuration()` 兜底返回配置默认值而非 `DURATION_NATURAL` |
| BUG-3 | `DanmakuManager.java:37-46` + `DanmakuProxyEntity.java:225,233,238,247` | erase 包 tracking entity 与 spawn 包统一（`trackingOverride` 机制） |
| BUG-4 | `BossYoukaiEntity.java:318-324` | danmaku 伤害不再被 `hurtCD` 二元冷却短路，改为进入 `DamageThrottleTracker` |
| P0-1 | `ParallelDanmakuTicker.java:67-541` | Step2 主线程预计算 projectile-specific hit/graze boxes，Step3 不再触碰 owner/targets/graze capability 等实时 entity 状态 |
| P0-2 | `SpellRegistry.java:14-115` | `applyDatapackDefaults()` 改为基于单一 `RegistryState` 的 snapshot-and-swap，避免 reload 中间态对外可见 |
| P1-1 | `HitBehavior.java:8-22` + repo-wide audit | 审计确认仓库内无 `hit_behavior_entity = continue` 定义，补充注释明确 `CONTINUE` 为“穿透并伤害”语义 |
| P1-2 | `BaseProjectile.java:33-52` + `ProjectileHitHelper.java:20-58` + `ParallelDanmakuTicker.java:67-541` | 顺序/并行碰撞检测均改为基于本 tick 的 pre-hit `computeMove()` 结果，避免搜索向量与实际轨迹脱节 |
| P1-3 | `SpellRegistry.java:27-35` | `register()` 增加 Javadoc，明确其为 transient live registry 写入，不保存 authoritative defaults |
| P2-1 | `YHModConfig.java:70-210` + `YoukaisHomecoming.java:255-261` | 新增 `enableTestingSpellTab` 开关，默认仅展示 `itemForm.generate()` 的正式符卡 |
| P2-2 | `docs/pending/review-00-overview.md` + `docs/pending/review-05-commits-7-to-13.md` | 补充后续说明，修正 `b09e447` 的摘要为其真实混合范围，不再沿用误导性窄标题 |
| P2-3 | `YHCommands.java:398-410` + `docs/archived/plan-spellcard-rework-claude.md` | `/yhspell reload` 明确提示其为全量 datapack reload（等价 `/reload`），文档同步更正 |
| P3-1 | `ActionEditorPanel.java:1354-1367` | `notifySimple()` 不再静默吞掉 `ClassCastException`，改为 WARN 日志并保留当前忽略陈旧 responder 的行为 |
| P3-2 | `LegacyTickerAction.java:15-59` | `legacy_ticker` 缺 factory 时改为 per-spell-id warn，并向触发该符卡的服务端玩家发送一次客户端提示 |
| P3-3 | `MoverConfigs.java:261-281` | `HomingMoverConfig.create(Vec3,Vec3)` 在无 `SpellContext` 路径下输出一次降级 WARN |
| P3-4 | `NumberProviders.java:502-518` | `HeightmapY` 增加 `hasChunkAt()` 保护，未加载区块时回退到 target/holder 当前 Y |
| P3-5 | `SpellItemForm.java:9-37` | 增加 compact constructor 校验，拒绝负数 `cooldown` / `duration`，并兜底空 `iconItem` |
| P4-1 | `docs/archived/preview-performance-plan.md` | 将 PH 状态从“已实现”更正为“待实施”，并注明当前代码仍为同步 flush |
| P4-2 | `docs/archived/GLM/spell-editor-migration-plan.md` | 补全 `SpellItemForm` 配置表的 `duration` 列，并同步现有 testing-tab 配置说明 |

---

## P0 — 线程安全（应尽快处理）

| 项 | 问题 | 建议 |
|----|------|------|

---

## P1 — 语义正确性（应在下次发版前处理）

| 项 | 问题 | 建议 |
|----|------|------|

---

## P2 — 架构流程（建议处理）

| 项 | 问题 | 建议 |
|----|------|------|

---

## P3 — 防御性编码（酌情处理）

| 项 | 问题 | 建议 |
|----|------|------|

---

## P4 — 文档（低优先）

| 项 | 问题 | 建议 |
|----|------|------|

---

## 待独立排查

| 项 | 问题 |
|----|------|
| ~~客户端弹幕渲染残留~~ | ~~已修复~~：`trackingOverride` 机制统一 spawn/erase 包的 tracking entity（BUG-3） |
| ~~Sanae SpellItemForm~~ | ~~已修复~~：不再作为独立排查项保留 |
| `/yhspell give` 目标型符卡瞬时 erase | 已确认 `5dfb15b91618533e023f32f14c2daddac278a777` 引入新回归：通过 `/yhspell give` 使用需要目标的符卡时，施法特效会短暂出现，但虚拟弹幕/激光随后立即被 erase；说明施法入口和首 tick 基本已触发，问题更像是该提交中新引入的 target-tracking / item-spell 兼容路径导致的后续清场。优先排查 `DanmakuProxyEntity` 的 target 刷新与 cleanup 条件，以及 `TrackingAttachedMover`、`LerpDirectionAction`、`AimMode.VariableDirection` 在玩家持有者路径下的行为。 |
| DanmakuProxyEntity 硬性上限 | 考虑添加 `MAX_PROXY_TICKS`（如 6000 tick / 5 分钟）作为安全网 |
| DanmakuProxyEntity 可被 Jade 等方式查看到 | 低优先级：proxy 作为内部承载实体，按设计目的最好尽量对探针/查看类 UI 隐藏；当前影响不大，后续再评估兼容层或展示抑制方案 |
