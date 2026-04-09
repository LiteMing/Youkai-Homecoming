# 行动项清单

按优先级排列。标注 `[已修复]` 的项目已在本次审查中写入代码。

---

## 已修复

| 项 | 文件 | 修改 |
|----|------|------|
| BUG-1 | `DanmakuProxyEntity.java:316-319` | 添加 `isPickable()` 返回 `false` |
| BUG-2 | `DynamicSpellItem.java:158-167` | `resolveSpellDuration()` 兜底返回配置默认值而非 `DURATION_NATURAL` |
| BUG-3 | `DanmakuManager.java:37-46` + `DanmakuProxyEntity.java:225,233,238,247` | erase 包 tracking entity 与 spawn 包统一（`trackingOverride` 机制） |

---

## P0 — 线程安全（应尽快处理）

| 项 | 问题 | 建议 |
|----|------|------|
| P0-1 | 并行 Step3 残留实时 entity 读取（`getOwner()`、`targets.contains()`、`GrazeHelper`） | 将 owner/targets 信息纳入 `CachedTarget` 快照或移至 Step2 主线程 |
| P0-2 | `SpellRegistry.applyDatapackDefaults()` 非原子更新 | snapshot-and-swap 或明确文档化为仅主线程操作 |

---

## P1 — 语义正确性（应在下次发版前处理）

| 项 | 问题 | 建议 |
|----|------|------|
| P1-1 | `CONTINUE` 语义从"忽略实体"变为"穿透+伤害" | 与最近修复的 editor `onHitEntity` / `discard` 问题不是同一项；这里是运行时语义变化，仍需审计所有 CONTINUE 使用点，如需"装饰性穿透"则新增 `PASS_THROUGH` 枚举值 |
| P1-2 | 碰撞检测向量与实际移动向量不匹配 | 将 `computeMove()` 移至 Step1 |
| P1-3 | `register()` 不再保存 defaults 但 API 不变 | 重命名/降可见性/加 Javadoc |

---

## P2 — 架构流程（建议处理）

| 项 | 问题 | 建议 |
|----|------|------|
| P2-1 | `populateTestingTab()` 无生产环境开关 | 加 config flag 或 `FMLEnvironment.production` 检查 |
| P2-2 | commit `b09e447` 粒度失控 + 信息不准确 | 后续拆分或修正信息 |
| P2-3 | `/yhspell reload` 实为全量 reload | 文档说明或实现轻量 reload |

---

## P3 — 防御性编码（酌情处理）

| 项 | 问题 | 建议 |
|----|------|------|
| P3-1 | `notifySimple` blanket catch(ClassCastException) | 移除或改为 WARN 日志 |
| P3-2 | `LegacyTickerAction` 全局单次 warn | per-spell-id warn + 玩家侧提示 |
| P3-3 | `HomingMoverConfig.create(Vec3,Vec3)` 静默丢失追踪 | 加日志警告 |
| P3-4 | `HeightmapY` 无区块加载保护 | `hasChunkAt()` 检查 |
| P3-5 | `SpellItemForm` 无输入校验 | compact constructor 校验 |

---

## P4 — 文档（低优先）

| 项 | 问题 | 建议 |
|----|------|------|
| P4-1 | `preview-performance-plan.md` PH 状态不准确 | 验证并更新 |
| P4-2 | `spell-editor-migration-plan.md` 缺 duration 列 | 补全表格 |

---

## 待独立排查

| 项 | 问题 |
|----|------|
| ~~客户端弹幕渲染残留~~ | ~~已修复~~：`trackingOverride` 机制统一 spawn/erase 包的 tracking entity（BUG-3） |
| Sanae SpellItemForm | 已确认早期遗留隐患：除 `SpellItemForm.NONE` 外，Sanae far 模式下的随机弹幕散射/其代码支持也可疑；当前实测卡死时后台伴随 `Invalid entity rotation: -Infinity/Infinity, discarding.`，需优先从该方向复现并定位 |
| DanmakuProxyEntity 硬性上限 | 考虑添加 `MAX_PROXY_TICKS`（如 6000 tick / 5 分钟）作为安全网 |
| DanmakuProxyEntity 可被 Jade 等方式查看到 | 低优先级：proxy 作为内部承载实体，按设计目的最好尽量对探针/查看类 UI 隐藏；当前影响不大，后续再评估兼容层或展示抑制方案 |
