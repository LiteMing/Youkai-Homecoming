# 符卡类型与特殊符卡设计方案

## 文档状态

本文档记录 `last_spell`（终符）、`timeout_spell`（时符）和
`non_spell`（非符）的已确认语义，作为后续实现与评审的设计契约。本文档只定义类型边界和
跨系统约束；非符的具体示范 JSON、节点白名单和数值预算待后续补充。

复刻玩法的总体流程见 [`plan-spell-replica.md`](plan-spell-replica.md)。本文件对复刻行为的
补充规则优先于该文档中相应的类型假设。

## 1. 共同模型

### 1.1 类型是一等符卡属性

符卡类型不是 Minecraft 普通附魔，也不是可由普通玩家任意插入的 `on_enter` 动作。建议在
符卡定义的 item form 中增加显式字段：

```json
{
  "item_form": {
    "card_type": "normal"
  }
}
```

缺省值为 `normal`，旧 JSON 必须保持兼容。候选值：

```text
normal | last_spell | timeout_spell | non_spell
```

类型必须参与定义的 canonical bundle/hash。认证报价、认证控制器、证书和正式施放都读取同一
类型，不能只依赖物品 NBT 中的缓存字段。物品 NBT 可以投影类型以便客户端显示，但服务端定义
和认证快照才是权威。

类型在认证前确定，认证后冻结。已认证的普通符卡不能通过修改 NBT 或 Raw JSON 临时变成
时符/终符；需要另存为新的未认证草稿并重新认证。

### 1.2 权限与配置

- 类型策略、Tier 预算、节点许可和 B/XP 倍率由服务端配置/KubeJS 提供，禁止在 Editor 或
  客户端复制第二套判定。
- 普通玩家在 Editor 只能查看类型；OP 或服务器脚本才可设置类型。Raw JSON 提交也必须经过
  服务端权限和策略校验。
- 认证开始时应冻结本次报价所使用的类型规则和有效费用，避免配置热更造成 UI、证书和实际
  扣费不一致。
- 新增可调数值（倍率、冷却、非符预算等）全部进入 `YHModConfig` 或 KJS 策略，不在实体和
  渲染器中放第二份默认值。

## 2. 终符（`last_spell`）

### 2.1 使用条件

- 只能在真实弹幕战中主动使用；非弹幕战直接拒绝，不存在普通场景下消耗经验的备用入口。
- 每名玩家每场弹幕战最多成功启用一次。这个状态属于服务端战斗状态，不能只放在物品 NBT 或
  客户端冷却栏。
- 当前没有正在释放的符卡时才能启用。
- 即使玩家为 `0 LIFE / 0 BOMB`，仍允许这一次主动启用。

### 2.2 资源与结束语义

终符启用成功后，服务端立即把玩家资源强制设为 `0 BOMB / 0 LIFE`，防止终符期间或结束后
出现与“终符”定义不符的额外资源。该写入必须是服务端权威操作并同步客户端 HUD。

终符拥有独立冷却，默认值和单位由配置决定。冷却属于玩家战斗状态，跨物品实例共享；换一张
终符不能绕过冷却。冷却何时开始（启用时或结束时）必须在实现中固定并写测试，建议以成功启用
时开始，避免异常结束反复触发。

结束原因必须显式区分：

| 结束原因 | 结果 |
|---|---|
| `TIMEOUT` | 进入完整 `beaten` 流程 |
| `SPELL_BREAK` | 进入完整 `beaten` 流程 |
| `PLAYER_CANCEL`（主动 Shift+右键） | 结束符卡但不进入 `beaten`；本场仍记为已使用 |
| `EXTERNAL_ABORT`（离线、服务器清理等） | 清理运行时，不重复触发 `beaten` |

因此代理实体和战斗控制器需要使用明确的 `SpellEndReason`，不能用一个布尔值推断是否击破。

## 3. 时符（`timeout_spell`）

### 3.1 认证与实战

- 认证阶段不要求击破；达到符卡指定持续时间即可完成认证。现有
  `CertificationController.timeoutCompletes` 可作为实现基础。
- 在弹幕战中使用时，持续时间结束按“符卡被击破”处理：清除该符卡的弹幕和显示状态，扣除
  LIFE；最后一点 LIFE 被扣除后进入现有 `beaten` 流程。
- 中途主动取消已经统一按击破处理，时符沿用该逻辑，不另设取消例外。

### 3.2 消耗

- 时符没有独立冷却。
- 认证/使用所需 B 消耗乘以可配置倍率，默认 `0.25`；最低 B 消耗边界保持现有规则（不能因
  倍率降为 0）。
- 经验消耗乘以可配置倍率，默认 `1.0`，即保持现值。
- 报价界面显示倍率计算后的最终费用，而不是让玩家面对大量尾随零的原始内部单位。

类型规则必须在认证报价、扣费、证书记录和实战结束处理之间保持一致。已认证符卡才应用时符的
实战结束语义；认证后的类型不可改写。

## 4. 非符（`non_spell`）

### 4.1 已确认边界

- 不需要认证，可直接作为非符使用。
- 不提供符卡无敌或符卡血量功能；包含 `set_spell_health` 等符卡展示/生命专用节点时必须由
  服务端拒绝或在保存前给出明确错误。
- 禁止符卡展示相关节点，至少包括 `show_spell_title`、`set_spell_circle` 及同类认证/展示
  能力。最终白名单以非符示范 JSON 和 KJS 策略确定。
- 不消耗 B 或经验。
- 非符虽然使用符卡基底承载，但类型语义上不是“符卡/Bomb”。在认证战斗中启用、关闭或持续
  运行非符，不得触发 No-Bomb/No-Hit（NBNH）失败；非符发射的玩家侧弹幕仍作为普通攻击
  正常参与伤害和碰撞。
- 弹幕战中可以开启/关闭，同一玩家同时只能有一个非符运行时；启用另一个非符时先清理旧的，
  再次启用当前非符则关闭它。是否允许在非弹幕战切换由后续示范和测试确定，不能凭客户端按钮
  绕过服务端条件。
- 非符不参与复刻：复刻底片不能以 `non_spell` 作为来源生成副本，非符也不需要提供被复刻的
  入口。

### 4.2 认证 NBNH 边界

认证系统必须按服务端权威的符卡类型区分攻击来源，不能仅因物品是 `DynamicSpellItem` 或使用了
符卡基底就把非符判定为 Bomb。非符启用路径不得调用认证战的符卡/Bomb 使用失败入口；非符
弹幕也不得被登记为认证敌机对认证者的 No-Hit 接触。

这项豁免只排除“非符自身导致的 NBNH 失败”，不赋予认证者无敌：认证敌机的有效弹幕在非符
运行期间命中认证者时，仍然必须照常触发 No-Hit 失败。普通伤害、碰撞、弹幕战 LIFE 和
`beaten` 规则也不因非符而关闭。

### 4.3 性能与能力

非符使用独立的 analyzer profile：可用节点范围更窄、节点数量和认证上限更低，并且 Tier 对
预算的缩放比普通符卡更严格。所有预算和能力许可由 KJS 配置，未知节点继续 fail-closed。

## 5. EX 灵气与 EX 特性

### 5.1 获取与合成

EX 灵气不是第四种互斥符卡类型，而是可以附加在类型之上的独立特性。默认通过隐藏成就发放：

```text
收集全部默认注册的预设符卡（排除 DynamicSpellItem 和 CustomSpellItem）
→ 完成隐藏成就
→ 奖励 1 个 EX 灵气
```

“默认注册”以服务端 `SpellRegistry` 的 builtin/default 集合为准，不把运行时 KJS、市场、玩家
复刻或 `DynamicSpellItem`/`CustomSpellItem` 加入收集要求。默认 advancement 和奖励 loot table
提供稳定 ID；KJS 可以修改条件、数量或增加其他来源。

EX 灵气作为一次性材料参与类型转换：

```text
Tier N 未认证符卡基底 + EX 灵气
→ Tier N、未认证、带 ex_spell 特性的符卡基底
```

转换不改变 Tier、预算或节点内容，也不能绕过认证。已认证成品不能通过 NBT 或配方直接追加
EX 特性；必须先回到未认证草稿并重新认证。EX 灵气默认允许交易，成就只负责提供默认获取
来源，服务器可通过 KJS 增加或限制来源。

### 5.2 实战语义

带 `ex_spell` 特性的符卡只免疫**玩家符卡弹幕对该符卡自身符卡血量的伤害**。它不保护玩家
本体 LIFE，不提供全局无敌，也不免疫近战、环境、原版攻击或其他非符卡伤害。免疫判断必须
使用服务端权威的伤害来源分类，不能从 projectile owner 或客户端标记推断。

EX 特性与 `last_spell`、`timeout_spell`、`normal` 的组合是否全部开放由 KJS 能力策略决定；
`non_spell + ex_spell` 默认禁止，因为非符没有符卡血量语义。EX 特性不改变认证 NBNH 规则，
也不要求把 NBNH 判定从认证战搬到所有普通对战中。

### 5.3 成就稳定性

隐藏成就的完成进度由原版 advancement 持久化。未来新增默认预设符卡时，新世界会自动获得
扩展后的收集条件；已经完成成就的玩家不因注册表扩展而撤销进度。交易获得的 EX 灵气可以正常
使用，不要求使用者本人重新完成收集成就。

## 6. 复刻与类型的关系

复刻的目标是最大化节点和行为还原度，而不是生成一个“简化版”弹幕。达到 100% 时，服务端
应尽可能复制来源符卡的 phases、actions、hooks、变量和生命计划，使用深复制/Codec 路径，
重写根 ID 和 phase 引用以形成玩家自己的草稿。

复刻产物仍然必须满足以下边界：

1. 新物品是 Tier 1、未认证、独立的新草稿，不继承原卡证书、完成状态、费用、OP 权限、冷却
   或原始 `spell_id`。
2. 来源类型不自动继承。默认生成 `normal` 草稿；`non_spell` 不可复刻。特殊类型只能由
   OP Editor、KJS 或明确的服务器配方授予，并需要重新认证。
3. 原卡中的实验性/OP-only 节点应原样保留在草稿中以便玩家修缮，但认证分析器仍按当前策略
   拒绝不具备权限的节点；拍照不能绕过 `OP_ONLY` 限制。
4. 复制失败（来源快照不可用、节点不可解码等）不得静默生成空卡。底片应保留为完成状态并给
   出服务端可诊断的失败原因，或在绑定阶段拒绝不可恢复的来源。

## 7. 服务端状态与测试契约

类型相关状态必须由服务端持有：

- 终符：本场已使用标记、独立冷却、结束原因、资源归零结果；
- 时符：认证是否采用 timeout 完成、最终 B/XP 费用、按击破结束的结算；
- 非符：当前唯一运行时和开关状态；
- EX：是否持有 `ex_spell` 特性、来源/消耗记录、仅对符卡血量生效的伤害分类；
- 复刻：来源绑定、进度、来源类型排除和完成转换结果。

建议至少覆盖以下回归用例：

```text
LAST_SPELL_REJECTED_OUTSIDE_DANMAKU_BATTLE
LAST_SPELL_CAN_START_AT_ZERO_LIFE_ZERO_BOMB
LAST_SPELL_FORCES_ZERO_LIFE_ZERO_BOMB
LAST_SPELL_HAS_PLAYER_SHARED_COOLDOWN
LAST_SPELL_CANCEL_DOES_NOT_ENTER_BEATEN
LAST_SPELL_TIMEOUT_OR_BREAK_ENTERS_BEATEN
TIMEOUT_SPELL_CERTIFIES_ON_TIMEOUT
TIMEOUT_SPELL_CANCEL_COUNTS_AS_BREAK
TIMEOUT_SPELL_HAS_NO_INDEPENDENT_COOLDOWN
TIMEOUT_SPELL_B_COST_RESPECTS_MULTIPLIER_AND_MINIMUM
NON_SPELL_SKIPS_CERTIFICATION_AND_RESOURCE_COST
NON_SPELL_REJECTS_SPELL_HEALTH_AND_PRESENTATION_NODES
NON_SPELL_IS_SINGLE_ACTIVE_RUNTIME_PER_PLAYER
NON_SPELL_CAST_IS_NOT_CERTIFICATION_BOMB_USE
NON_SPELL_PROJECTILES_DO_NOT_CREATE_CERTIFICATION_NO_HIT_CONTACT
CERTIFICATION_ENEMY_HIT_STILL_FAILS_WHILE_NON_SPELL_IS_ACTIVE
NON_SPELL_IS_NOT_REPLICABLE
EX_AURA_ADVANCEMENT_COUNTS_BUILTIN_PRESETS_ONLY
EX_AURA_EXCLUDES_DYNAMIC_AND_CUSTOM_SPELLS
EX_AURA_CONVERSION_PRESERVES_TIER_AND_REQUIRES_CERTIFICATION
EX_SPELL_IGNORES_PLAYER_SPELL_DAMAGE_TO_SPELL_HEALTH
EX_SPELL_DOES_NOT_PROTECT_PLAYER_LIFE
EX_SPELL_DOES_NOT_IGNORE_NON_SPELL_CARD_DAMAGE_RULES
REPLICA_PRESERVES_MAXIMUM_NODE_FIDELITY
REPLICA_REMAINS_TIER1_UNCERTIFIED_DRAFT
REPLICA_SPECIAL_TYPE_REQUIRES_EXPLICIT_GRANT
```

实机验收仍是必要条件：终符的资源归零与结束相位、时符的取消/超时结算、非符唯一运行时和
复刻完成后的草稿转换都必须在服务端日志和客户端 HUD 中分别核对。客户端只能显示服务端投影，
不能本地决定类型、费用、冷却或 `beaten` 状态。

## 8. 后续实现顺序

1. 增加 `SpellCardType` 与 Codec/定义 hash 接线，完成旧 JSON 兼容。
2. 将类型规则接入认证报价、证书和正式施放；先落地终符资源/冷却和时符 timeout 结算。
3. 增加非符 analyzer profile、节点白名单和单运行时约束；待用户提供示范 JSON 后确定具体列表。
4. 增加 EX 灵气物品、默认收集成就/奖励和 EX 伤害分类；接入类型转换配方。
5. 将类型权限接入 Editor/KJS，普通玩家只读，OP/脚本可设置。
6. 按本文件更新复刻完成转换，保留最大节点还原度并明确排除非符。
7. 补齐服务端单测、存档/重连测试和实机验收，再决定版本号与拆分提交。
