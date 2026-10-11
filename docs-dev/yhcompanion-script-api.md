# YH 伴飞脚本接口 v1（0.32.2）

`YHCompanion` 是仅服务端的 KubeJS 全局，等价 Java 类为
`dev.xkmc.youkaishomecoming.compat.kubejs.YHCompanionApi`。接口配置原生
`YoukaiFlyCompanionGoal`，不激活它、不绑定玩家、不负责攻击或施放符卡。

| 方法 | 语义 |
|---|---|
| `getApiVersion()` | 当前为 1 |
| `getDefaultsJson()` | 当前 `YHModConfig.COMMON.companion*` 默认配置 |
| `getConfigJson(entity)` | 实体有效配置；未配置则为服务端默认值 |
| `configure(entity, owner, json)` | 验证后原子写入；其他 owner 已持有时返回 false；无效输入抛异常 |
| `clear(entity, owner)` | 仅移除匹配 owner 的配置，恢复服务端默认值 |
| `getStatus(entity)` | 最近一次 Goal 状态：ACTIVE、BLOCKED_TARGET、INACTIVE，未运行可为空 |

修改要求实体为 YH Youkai、未移除且位于服务端线程。JSON 不接受未知字段、字符串数字、非有限数值或零采样间隔。
配置持久化到实体 `yh_companion_flight`，调用者必须在退出、取消、卸载重载恢复时清理自己 owner 的配置。
不要用 `clear` 删除其他运行的标签或玩家绑定。

| 字段 | 取值与单位 |
|---|---|
| `target_mode` | LEGACY、PLAYER_LOCAL、PLAYER_WORLD、ABSOLUTE |
| `x/y/z` | LEGACY 忽略；LOCAL 的 X 向右、Y 向上、Z 向前并随玩家身体 yaw 旋转；WORLD 按世界轴相加；ABSOLUTE 为世界坐标 |
| `dimension` | ABSOLUTE 必填，必须匹配实体当前维度；跨维度后目标会受阻 |
| `max_speed` | 平滑速度上限，格/刻，0.01–4；不限制锁定位置或原有远距补偿 |
| `update_interval_ticks` | 目标位置和朝向的采样间隔，1–200；配置改变会立即重采样 |
| `movement_mode` | STEER 平滑追赶；LOCK_TO_TARGET 直接定位到采样位置 |
| `facing_mode` | TRAVEL、PLAYER_BODY、PLAYER_VIEW、KEEP |

没有填写的字段在配置提交时从服务端配置取值；不是另一份脚本默认常量。
未配置的首个伙伴保持原轨道坐标、0.85 速度上限及每 tick 更新；新增默认数值只定义在 YHModConfig。
原版 FlyingMoveControl 不再叠加第二次加速或覆盖配置朝向。

```js
const owner = 'my-script:my-run-id'
if (!YHCompanion.configure(entity, owner, JSON.stringify({
    target_mode: 'PLAYER_LOCAL', x: 0, y: -10, z: 0,
    update_interval_ticks: 1, movement_mode: 'LOCK_TO_TARGET',
    facing_mode: 'PLAYER_BODY'
}))) throw new Error('伴飞配置已被其他控制器持有')
// 仍由调用者设置 cchat_companion_player UUID 与 cchat_flight_companion 标签。
// 结束时移除自己持有的激活状态，并调用：
YHCompanion.clear(entity, owner)
```

Goal 检查目标实体包围盒的高度、世界边界、已加载区块和端点碰撞；没有可用端点时停止速度并报告 BLOCKED_TARGET。
ACTIVE 表示 Goal 正在处理目标，并不保证已经抵达，也不保证绕过任意地形。
不强制加载区块；锁定模式不进行连续路径避障。

0.32.2 的日常增强仅适用于 `target_mode=LEGACY` 且 `movement_mode=STEER`：

- 自动分位按当前维度与玩家分组；首个伙伴精确保留旧轨道，第二个在另一侧，后续形成左右成对的队列。
  成员按实际加入顺序取得空闲 Slot，已有成员不会因他人退出而换位；停止、切换显式目标或卸载后释放。
  注册表每世界 tick 最多清扫一次，仅保存 UUID 与活动刻，不扫描全世界实体，也不持有已卸载世界的强引用。
- 使用实体完整 AABB 覆盖短程运动区间，前瞻至少覆盖本 tick 的速度；受阻后跨 tick 优先抬升，再左右侧绕。
  已成功的绕行方向保持数刻，随后重试前向；失败时衰减速度，由原版移动碰撞裁剪残余位移。
- 原目标受阻时尝试沿玩家方向回退，仍失败才在后续 tick 检查玩家头顶。头顶也必须通过完整端点检查。
  回退目标随采样的玩家位置更新，并按间隔重试原目标；不覆盖脚本配置。
- 端点、回退和运动区间共用每实体每 tick 最多两次主动碰撞探测。恢复原目标或试探回退消耗预算后，
  本 tick 减速，不再追加绕行检测。原版实体移动的碰撞处理不属于 Goal 新增探测。

`PLAYER_LOCAL`（包括显式零偏移）、`PLAYER_WORLD`、`ABSOLUTE` 与所有 `LOCK_TO_TARGET` 不启用自动分位、
局部绕行或目标回退，保留原有目标与端点拒绝语义。LEGACY 仍忽略 x/y/z，不把非零数值解释为新契约。
飞行/走路切换、攻击优先级、符卡 caster_moves 和 API v1 JSON 字段保持原契约。

`YHModConfig.COMMON` 的伴飞分组新增服务端默认配置：

| 配置键 | 默认值 | 作用 |
|---|---|---|
| `automaticSlots` | true | 日常稳定分位 |
| `slotSpacing` | 2.4 | 成对队列行距，格 |
| `localAvoidance` | true | 日常局部绕行与目标回退 |
| `lookahead` | 1.5 | 短程前瞻距离，格；至少覆盖本 tick 位移 |
| `detourSpeed` | 0.4 | 抬升与侧绕速度，格/刻；仍受实体 max_speed 限制 |
| `detourHoldTicks` | 4 | 绕行方向保持时间，刻 |
| `targetRetraction` | 1.5 | 原目标回退距离，格；不会越过玩家 |
| `targetRecoveryTicks` | 20 | 原目标重试间隔，刻 |

这是局部控制，不保证穿越长墙、封闭空间或复杂死角；分位只分离默认目标，不保证全程实体互相避让。
超过 50 格的原有端点补偿仍保留，且仅在目标包围盒验证通过后执行。
已绑定玩家离线/死亡/旁观/离开维度时不会暗中换到最近玩家。
有活着的战斗目标时仍让位于攻击 Goal。每 tick 定位提供俯视操控的移动基础，
不包含战斗输入、弹幕施放、战斗移动仲裁或客户端逐帧同步。

跨仓库消费者：CreatureChat `yh_flight_companion` v4，配置 owner 与当前运行 lease 相同。
FLIGHT/WALK 切换保留配置，终止、取消、重载与过期 lease 清扫只清理匹配 owner；缺旧版本 API 时明确失败。

验证分层：纯 JVM 几何/速度、生产 KJS host 隔离契约、浏览器填参保存和构建检查；
实际位置、朝向、网络观感与碰撞必须由 Minecraft 人工验收。

日常局部控制回归入口：`runCompanionFlightMathTest`、`runCompanionFlightSettingsTest`、
`runCompanionFlightAvoidanceTest`。最后一项使用生产控制器与模拟碰撞盒覆盖矮墙、低天花板侧绕、
封闭受阻、薄墙扫掠、高速前瞻、目标回退/恢复、稳定 Slot 与卸载释放；它不是服务端性能或实机验收。
