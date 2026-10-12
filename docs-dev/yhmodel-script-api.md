# YHModel 与 OYSM preset 契约（HCDRS 0.34.0）

OYSM HCD 0.4.0 是模型目录、原生预览、参数/转盘控件、preset 定义、状态绑定、保存与同步的唯一所有者。
YH 提供模型资产、业务信号和实体表现请求。YH 没有第二份 profile、预设库或通用 editor；
`profileJson`、旧 profile 网络包和旧 JSON 导入路径已经删除。服务器查询命名预设需要安装 OYSM。

## 查询和调用

KubeJS 使用 OYSM 的公开 registry 查询，使用 YHModel 修改 YH 实体。
模型路径必须包含实际入口，例如 `TUMC/东方Project/「博丽灵梦」.ysm/main`、
`touhou/reimu2.0.5.ysm/main`、`youkaishomecoming/remilia/trident`；不能省略入口。

```js
const OysmPresets = Java.loadClass('rip.ysm.api.ExternalPresetAPI')
const model = String(entity.currentYsmModel())
const catalog = JSON.parse(String(OysmPresets.catalogJson(model)))
// catalog: { model, revision, presets: [{ id, model, description, clip, ticks, parameters }], bindings }
const preset = catalog.presets.find(value => value.id === requestedId)
if (preset && YHModel.supports(entity)) {
    YHModel.applyPreset(entity, model, preset.id)
}
```

ID 是 OYSM 返回的完整 ID。YH 来源的定义由 OYSM 分配 `yhysm:<key>`；
原生 editor 新建定义使用 `editor:<key>`。模型作用域和 preset namespace 是不同概念。
业务模组只提供本地 key、数值、信号名和绑定，不能拼接公共 ID。

| 方法 | 行为 |
| --- | --- |
| `supports(target)` | 是否实现 YH 表现目标接口 |
| `listPresets(target, model)` | OYSM registry 中该入口的完整 ID 列表 |
| `describePreset(target, model, id)` | OYSM 定义中的说明 |
| `applyPreset(target, model, id[, ticks])` | 原子应用模型限定的动画和参数 |
| `applyPresetOwned(target, model, id, ticks, owner)` | 按唯一请求归属应用 |
| `releaseOwned(target, owner)` | 仅释放此请求仍持有的覆盖 |
| `ownedStatusJson(target, owner)` | active、clip、parameters、remaining_ticks |
| `ownedPresentationApiVersion()` | 当前归属隔离协议为 1 |
| `defaultDuration()` / `maxDuration()` | 服务端配置中的默认时长 / 上限 |
| `play(target, clip, ticks)` / `stop(target)` | 请求或停止精确动画；重复 play 重播 |
| `setParameter(target, name, value, ticks)` | 请求数值覆盖 |
| `clearParameter(target, name)` / `clearParameters(target)` | 清除单项 / 全部数值覆盖 |
| `clear(target)` | 清动画和数值请求，保留实体模型绑定 |
| `getAnimation(target)` / `getAnimationTicksRemaining(target)` | 当前动画请求 / 剩余时长 |
| `getParameter(target, name)` | 当前数值请求；不是客户端模型默认值 |

修改真实实体必须在服务端线程运行。请求被接受不等于客户端资源存在或已成功渲染。
`ticks=-1` 在 preset API 中使用定义时长；`0` 保持到释放；正值为服务端 tick。
状态查询中 `-1` 是保持、`0` 是无覆盖。有限时长随实体 NBT 保存绝对游戏时钟，区块重载不重新计时。
每次调用应生成唯一 owner；释放不能清掉同 tick、同值的后来请求，也不复活已经被覆盖的请求。
纯参数 preset 保留当前动画；未提及的参数保留。模型作用域不匹配时不显示，到期时钟继续走。

## editor、绑定与命令

`/openysm editor` 可以在没有 YH 时打开 OYSM 原生 editor。
`/yhysm editor` 是 YH 的公开打开入口，只调用 OYSM，不提供独立界面或降级 editor。

OYSM editor 的模型页可选 main、arm、projectile、vehicle 入口；main 使用 OYSM 原生玩家预览、
动画轮盘和 ModelSettingsScreen 参数控件。其余入口使用对应几何预览与原始数值输入。
轮盘和参数操作只修改隔离预览，捕获后可保存为 preset；不会修改玩家外观或发送假实体操作到服务器。

状态绑定页使用业务来源登记的信号名，将所选模型入口的 preset 绑定到该信号。
绑定和 preset 都由 OYSM 保存到 `config/yes_steve_model/presets.json`，在线编辑需要 OP 2 或单人世界所有者权限。
保存按服务端 revision 校验，成功后广播；新加入者接收同一快照。YH 每次解析表现时读取最新 OYSM 绑定。

实体的 UUID/type 模型与纹理绑定仍通过 `/yhysm set`、`/yhysm bind` 和 YH 的世界绑定契约设置；
通用 preset editor 不拥有 YH 世界绑定数据。符卡场景/弹幕编辑仍由 YH spell editor 提供。

```text
/yhysm preset list <model>
/yhysm preset apply <targets> <model> <preset> [ticks]
/yhysm anim play <targets> <clip> [ticks]
/yhysm anim stop <targets>
/yhysm param set <targets> <parameter> <number> [ticks]
/yhysm param clear <targets> [parameter]
/yhysm clear <targets>
/yhysm state <targets>
/yhysm inspect [entities]
/yhysm debug
```

参数接受 `v.name`、`v.roaming.name` 与等价的 `variable.` 名称，必须是有限数值。
客户端遵守模型声明的数值范围和 radio 选项；复杂 radio 表达式由原生控件执行，
preset 持久化只捕获可表示的数值输入。请求最大时长、数量和幅度来自 YH 服务端配置。
战败三相位的身体优先于显式请求，自动状态与战斗优先级继续由 YH 决定。

Shooter 初始配置只使用 `spawn_shooter.ysm`：model、texture、hint、duration、clear_target。
旧的平铺字段已经删除。`ysm_render` 节点只提供 hint/duration，不充当 preset 编辑节点。

## 切换与验证

两端需要同时升级到 YH HCDRS 0.34.0 / OYSM HCD 0.4.0；新 preset/绑定包使用新的 OYSM 网络协议。
旧 `config/youkaishomecoming/ysm_presets.json` 不再读取，运行时代码没有迁移或双恢复路径。
部署时可显式一次性转换已有数据到 OYSM 文件并归档原文件；这不是模组运行时兼容入口。

构建与契约检查不能证明实机渲染。实机应核对原生控件隐藏物件、轮盘子菜单、捕获与保存、
CChat 普通对话和 World Director 的应用/释放，以及战败和区块卸载恢复。
