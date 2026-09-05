# 认证玩法实机审查问题清单（2026-08-09）

> 状态：审查记录，尚未修改代码。对应提交前最后一次实机验证反馈。
> 范围：`0.22.0/feat-spell-certification` 分支认证/符卡玩法。

---

## 1. 玩家可以删除其他符卡（非作者应禁止，管理员除外）

**现象**：非制作者玩家可以删除他人制作的符卡。

**现状代码**：
- 非 OP 删除路径 `SpellEditorSyncToServer.deleteOwnSpell`（`content/spell/preview/SpellEditorSyncToServer.java`）：校验 `CustomSpellStorage.loadOwner` 与玩家 UUID 一致。
- OP 删除路径 `deleteSpell`：无 owner 校验（管理员全权，符合预期）。
- owner 旁文件只在 `saveSelfMadeSpell` 的「全新 id」分支写入（`origin == null` 时 `saveOwner`）。

**疑点**：
1. `loadOwner` 为 null 时 `deleteOwnSpell` 拒绝 → 理论上非 OP 删不掉。但**创建路径不唯一**：`/yhspell create`（OP 指令）与 OP 的 `saveSpell` 创建的卡不写 owner → 这些卡对非 OP 玩家「不可删」（null 拒绝）——方向相反，安全。
2. 真正的漏洞候选：**客户端** `SpellEditorController.canDeleteSelectedSpell` 对所有非内置 CUSTOM 卡显示删除按钮 → 非 OP 点击后服务端拒绝（只有消息）。如果玩家在**集成服务器/单机**（自己即 OP）删除别人卡 → 属管理员行为。
3. 待核实：`deleteOwnSpell` 是否覆盖所有网络包路径（分块组装后 action 恢复、`handleChunk` 后的 `execute`）；以及**非 OP 保存覆盖他人 CUSTOM 卡**（`saveSelfMadeSpell` 的 owner 校验）是否同样完整。

**修复方向**（待定）：统一 owner 写入（所有创建路径）；确认服务端删除/覆盖校验覆盖分块路径；客户端删除按钮按 owner 隐藏。

---

## 2. 认证实体还是没有隐形

**现象**：`setInvisible(true)` + 渲染器跳过未生效，实体模型仍显示。

**现状代码**：
- `SpellCertificationEntity` 构造：`setInvisible(true)`（entityData 同步）。
- `GeneralYoukaiRenderer.render`：`if (e.isInvisible()) return;`（在 YSM/TLM/super 之前）。

**疑点**：
1. 实体构造后、`addFreshEntity` 前是否被其他逻辑重置 `invisible`（`YoukaiEntity`/`GeneralYoukaiEntity` 的 `validateData`、`beginDanmakuDefeat`、beaten 状态机等）？
2. `isInvisible()` 数据是否被 `setInvisible(false)` 覆盖（搜代码中所有 `setInvisible` 调用）。
3. 客户端是否运行的是最新 build（构建产物残留风险，INV-6）。
4. 渲染器跳过的是「模型」，但 **YSM/TLM delegate 内部可能有独立渲染路径**（如 YSM 的 entity render override 不走 MobRenderer.render）——需确认委托链是否真的经过 `GeneralYoukaiRenderer.render` 的 invisible 分支。

**修复方向**（待定）：全仓搜索 `setInvisible` 覆盖点；确认 YSM/TLM 委托路径；必要时在实体 tick 每 tick 强制 `setInvisible(true)`（服务端权威）。

---

## 3. 认证界面时长输入与配置最低值不一致

**现象**：配置 `certificationMinDurationTicks` 默认 1200（60s），但认证界面 Duration 输入框允许/默认显示 20 tick。

**现状代码**：
- `CertificationScreen`：`durationTicks = Math.max(20, definition.itemForm.duration())`；输入框仅 `Math.max(20, ...)` 兜底。
- 服务端 `CertificationService.clampDuration`：clamp 到配置 `[minDurationTicks, maxDurationTicks]`。
- 界面与配置脱节：UI 可输入 20 tick，实际服务端 clamp 到 60s → 用户困惑。

**修复方向**（待定）：界面输入 clamp 到配置 `[minDurationTicks, maxDurationTicks]`（与服务端一致）；默认值取配置 min。

---

## 4. 释放符卡期间禁用弹幕：与 bomb 自动扣除机制的耦合未打通

**现象**：玩家释放符卡期间禁用其他弹幕（`forbidDanmaku`），但**弹幕战中的自动 bomb 扣除**（`GrazeCapability.performDanmakuHit` 的 `autoBombOnHit` → `useBomb()`）仍可触发——即「禁用弹幕」只拦了物品 use，没有拦 bomb 资源的使用。

**现状代码**：
- 物品 use 拦截：`GrazeHelper.forbidDanmakuWithMessage`（5 个物品已接）。
- bomb 路径：`GrazeCapability.performDanmakuHit`（autoBomb）与 `YHStgApi.tryManualBomb`（手动）——`useBomb()` 不经过 forbidDanmaku。

**疑点**：用户反馈「在弹幕战自动扣除 bomb 时候已经有体现了，现在还没用上，可能是一个解耦问题」——理解：自动 bomb 的扣除行为体现了「战斗资源在释放符卡期间仍被消耗」，而我们新增的禁用没有覆盖这条路径（解耦不彻底）。

**修复方向**（待定）：明确语义——释放符卡期间被弹幕击中时，autoBomb 是否应禁用（改为正常 miss）？手动 bomb 是否禁用？与认证战「no bomb」规则的一致性（认证战用 bomb 直接失败）。

---

## 5. stg_enter / stg_exit 未本地化

**现象**：`YHLangData.STG_ENTER` / `STG_EXIT`（弹幕模式切换提示）在 zh 分片与 en_us.json 缺失（与之前修复的 `stg_toggle` 同类问题）。

**现状代码**：`YHLangData` 第 124 行附近：`STG_ENTER("tooltip.stg_enter", ...)`、`STG_EXIT("tooltip.stg_exit", ...)`——仅有代码默认值。

**修复方向**：zh 分片 `main.json` tooltip 段 + `organizeLang` + en_us.json 三处补齐（与 `stg_toggle` 流程一致）。

---

## 6. 释放符卡期间玩家运动限制仍未生效

**现象**：速度钳制（`GeneralEventHandlers.onPlayerTick` 每 tick clamp 到 0.002）实测无效，玩家仍可正常移动。

**现状代码**：
- `GeneralEventHandlers.onPlayerTick`（`TickEvent.PlayerTickEvent`，Phase.END）：`SpellContainer.hasActiveProxy(sp)` 为真时 `setDeltaMovement(vel.scale(0.002/len))`。
- `SpellContainer.hasActiveProxy`：读 `ConditionalData` 的 `proxies`（服务端 capability）。

**疑点**：
1. `PlayerTickEvent.END` 的时序：玩家位移（`travel`/`aiStep` 由输入生成速度并位移）在 END 之前完成 → 钳制只在**位移后**改速度，下一 tick 输入又重新生成速度 → 位置仍累积（每 tick 位移由输入速度决定，钳制不影响输入生成的速度值？——钳制在位移后，但速度被钳后下一 tick 的 `travel` 用输入**重新计算**速度（`moveRelative` 在 travel 内按输入生成）→ 钳制完全无效）。
2. 因此**只钳速度在输入驱动移动模型下无效**（与用户实测一致）——需要阻止位移（位置回锁，有回弹）或阻止输入/覆盖 `travel`（`ServerPlayer` 无公开 input 字段，1.20.1 输入模型限制）或 mixin `ServerPlayer.aiStep/travel`。
3. `hasActiveProxy` 时序：`trackProxy` 在 `addFreshEntity` 后调用，首 tick 起生效——若钳制真生效应立刻见效。

**修复方向**（待定）：
- 方案 A：mixin `ServerPlayer.travel`（或 `aiStep`）在 proxy 活跃时跳过移动计算（干净、无回弹）。
- 方案 B：`PlayerTickEvent.START` 清输入并记录位置、END 回锁（有回弹风险，用户已否决）。
- 方案 C：每 tick 直接 `setPos` 到施放时的锁定点（回弹，已否决）。
- 倾向 A（mixin，服务端权威、无回弹）；认证中的同款限制（`CertificationController.tickActive` 的 `setDeltaMovement(0,0,0)`）同样无效，需一并处理。

---

## 附：审查结论

- 第 1、4、6 项需要代码改动（权限覆盖确认 / bomb 路径语义 / 移动限制机制替换）。
- 第 2 项需要实机日志/代码搜索确认 invisible 被重置点。
- 第 3、5 项为小修（UI clamp、lang 补键）。
- 本轮按要求仅记录，未修改任何代码。
