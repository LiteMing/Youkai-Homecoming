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
| P0-2 | `SpellRegistry.java:14-115` | `applyDatapackDefaults()` 改为基于单一 `RegistryState` 的 snapshot-and-swap，避免 reload 中间态对外可见 |
| P1-3 | `SpellRegistry.java:27-35` | `register()` 增加 Javadoc，明确其为 transient live registry 写入，不保存 authoritative defaults |
| P2-1 | `YHModConfig.java:70-210` + `YoukaisHomecoming.java:255-261` | 新增 `enableTestingSpellTab` 开关，默认仅展示 `itemForm.generate()` 的正式符卡 |
| P3-1 | `ActionEditorPanel.java:1354-1367` | `notifySimple()` 不再静默吞掉 `ClassCastException`，改为 WARN 日志并保留当前忽略陈旧 responder 的行为 |
| P3-2 | `LegacyTickerAction.java:15-59` | `legacy_ticker` 缺 factory 时改为 per-spell-id warn，并向触发该符卡的服务端玩家发送一次客户端提示 |
| P3-3 | `MoverConfigs.java:261-281` | `HomingMoverConfig.create(Vec3,Vec3)` 在无 `SpellContext` 路径下输出一次降级 WARN |
| P3-4 | `NumberProviders.java:502-518` | `HeightmapY` 增加 `hasChunkAt()` 保护，未加载区块时回退到 target/holder 当前 Y |
| P3-5 | `SpellItemForm.java:9-37` | 增加 compact constructor 校验，拒绝负数 `cooldown` / `duration`，并兜底空 `iconItem` |
| P4-1 | `docs/preview-performance-plan.md` | 将 PH 状态从“已实现”更正为“待实施”，并注明当前代码仍为同步 flush |
| P4-2 | `docs/GLM/spell-editor-migration-plan.md` | 补全 `SpellItemForm` 配置表的 `duration` 列，并同步现有 testing-tab 配置说明 |

---

## P0 — 线程安全（应尽快处理）

| 项 | 问题 | 建议 |
|----|------|------|
| P0-1 | 并行 Step3 残留实时 entity 读取（`getOwner()`、`targets.contains()`、`GrazeHelper`） | 将 owner/targets 信息纳入 `CachedTarget` 快照或移至 Step2 主线程 |

---

## P1 — 语义正确性（应在下次发版前处理）

| 项 | 问题 | 建议 |
|----|------|------|
| P1-1 | `CONTINUE` 语义从"忽略实体"变为"穿透+伤害" | 与最近修复的 editor `onHitEntity` / `discard` 问题不是同一项；这里是运行时语义变化，仍需审计所有 CONTINUE 使用点，如需"装饰性穿透"则新增 `PASS_THROUGH` 枚举值 |
| P1-2 | 碰撞检测向量与实际移动向量不匹配 | 将 `computeMove()` 移至 Step1 |

---

## P2 — 架构流程（建议处理）

| 项 | 问题 | 建议 |
|----|------|------|
| P2-2 | commit `b09e447` 粒度失控 + 信息不准确 | 后续拆分或修正信息 |
| P2-3 | `/yhspell reload` 实为全量 reload | 文档说明或实现轻量 reload |

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
| Sanae SpellItemForm | 已确认早期遗留隐患：除 `SpellItemForm.NONE` 外，Sanae far 模式下的随机弹幕散射/其代码支持也可疑；当前实测卡死时后台伴随 `Invalid entity rotation: -Infinity/Infinity, discarding.`，需优先从该方向复现并定位 |
| DanmakuProxyEntity 硬性上限 | 考虑添加 `MAX_PROXY_TICKS`（如 6000 tick / 5 分钟）作为安全网 |
| DanmakuProxyEntity 可被 Jade 等方式查看到 | 低优先级：proxy 作为内部承载实体，按设计目的最好尽量对探针/查看类 UI 隐藏；当前影响不大，后续再评估兼容层或展示抑制方案 |
