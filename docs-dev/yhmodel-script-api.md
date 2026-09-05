# YHModel — 0.28.0 模型表现接口

状态：控制接口、预设查询与应用已接入源码；不是 0.28.0 发布声明，画面和联机验收尚未完成。
第三种 YSM Editor、服务端共享预设和状态映射已接入。用户操作见 `docs/ysm-editor-user-checklist.md`；
供应商边界及迁移官方 YSM 的说明见 `docs-dev/ysm-official-migration.md`。

## 公共契约登记

- Java：`dev.xkmc.youkaishomecoming.compat.ysm.YHModel`。
- KubeJS：同一个类注册为全局 `YHModel`，没有第二套实现或客户端执行权限。
- 目标参数类型：`YsmRenderOverrideTarget`；实际世界消费者为 YH 妖怪和发射器。
- 世界修改必须在服务端线程执行；客户端真实实体、已移除实体会拒绝修改。
- `PreviewCardHolder` 与假实体已接通独立内存状态和模拟时钟；YSM Editor 本地试播不向真实世界写入。
- 接口提交的是表现请求。专用服务器无须安装 OYSM，也无须读取客户端模型资产；
  请求被接受不代表每个观察者都拥有相同模型、动画或控件。

| 方法 | 含义 |
|---|---|
| `supports(target)` | 对象是否实现当前表现目标接口；不表示模型已绑定或客户端资源存在 |
| `defaultDuration()` | 配置中的默认 tick 数，并受最大持续时间限制 |
| `play(target, clip, ticks)` | 请求一个真实动画片段，当前仅 LOOP；重复调用会生成新的重播序号 |
| `stop(target)` | 清除新动画请求，恢复日常表现／既有发射器提示，不清模型绑定或参数 |
| `setParameter(target, name, value, ticks)` | 设置一个数值参数的临时覆盖 |
| `clearParameter(target, name)` | 清除此参数的新覆盖 |
| `clearParameters(target)` | 清除全部新参数覆盖，不停止动画 |
| `clear(target)` | 停止新动画并清除新参数，不修改模型、纹理绑定或临时模型覆盖 |
| `getAnimation(target)` | 当前请求的片段名；无请求为 `""`，不是客户端实际选中结果 |
| `getAnimationTicksRemaining(target)` | `0` 无请求，`-1` 保持，其余为剩余 tick |
| `getParameter(target, name)` | 当前请求的数值覆盖；无覆盖为 `null`，不是模型的基础值 |
| `listModels(target)` | 列出当前服务端世界已保存配置的模型 ID；不等于客户端已安装模型目录 |
| `listPresets(target, model)` | 列出该模型在服务端登记的稳定预设 ID，排序后的只读 Java List |
| `describePreset(target, model, id)` | 已登记预设的说明；不存在则抛出 IllegalArgumentException |
| `profileJson(target, model)` | 该模型共享定义的 format=1 JSON（不包含 revision）；未登记为合法空定义 |
| `applyPreset(target, model, id)` | 原子应用模型限定的预设，使用其默认 ticks |
| `applyPreset(target, model, id, ticks)` | 原子应用指定持续时间；0 保持，内部 -1 表示使用定义时长 |

真实实体的预设查询和应用需要服务端线程；隔离假施法者通过目标接口读取已同步副本，供符卡节点本地试播。
真实客户端实体仍拒绝写操作；`listModels` 仍是服务端世界目录。
纯表情预设不重启现有身体动画；预设未提及的显式参数不清除。同名参数被新值取代。
预设在目标当前模型 ID 不匹配时不渲染，但到期时钟继续走；它不会自动替实体绑定另一个模型。
显式预设请求保留调用当时的值，后来编辑共享预设不会追溯改写已有请求；自动映射使用最新同步定义。

```js
if (YHModel.supports(entity)) {
    const model = '实际模型ID'
    if (YHModel.listPresets(entity, model).contains('happy')) {
        YHModel.applyPreset(entity, model, 'happy', 100)
    }
}
```

`ticks=0` 表示保持到显式清除或服务端实体卸载；请求不写实体存档。
有限时长以服务端 `Level.getGameTime()` 的绝对截止时间同步，重新进入观察范围不会重新计时。
模型晚载入时只应用剩余有效期；动画帧相位目前仍从客户端控制器起播，**不承诺晚加入者逐帧相位锁定**。
PLAY_ONCE、HOLD_ON_LAST_FRAME 尚未开放；不使用循环加计时冒充这两种语义。

例子（服务端 KubeJS 回调中已取得 `entity`；变量名只适用于实际核对过的那份模型）：

```js
if (YHModel.supports(entity)) {
    YHModel.play(entity, 'extra5', 100)
    YHModel.setParameter(entity, 'v.roaming.mouth', 2, 100)
    YHModel.setParameter(entity, 'v.roaming.saihong', 1, 100)
}
// 可分别清理身体动画和表情。
YHModel.stop(entity)
YHModel.clearParameter(entity, 'v.roaming.mouth')
```

## 参数与恢复边界

- 只接受 `v.name`、`v.roaming.name`，也接受等价的 `variable.` 前缀；名称规范化为小写。
  禁止表达式、函数调用、数组或任意深度路径，不提供通用 Molang eval。
- 数值必须有限；数量、默认时长、最大时长和绝对值上限来自 `YHModConfig.COMMON.modelPresentation*`。
- 客户端优先遵守模型声明的 checkbox / range / 可解析 radio 选项。不符合选项的请求保留为
  服务端请求，但该客户端不应用，并在 `/yhysm debug` / `inspect` 显示跳过原因。
  未声明的合法数值变量可以作为 raw 参数使用；这不保证模型实际消费它。
- 每次同步渲染前覆盖输入，渲染返回后恢复原值；不把清除理解为写 `0`。
  模型在该帧写入不同的新值时保留其写入。模型仍可在自己的计算过程中覆盖输入，
  这不是强制拦截模型脚本的全变量沙盒，也不撤销动画本身的其他脚本副作用。
- 新参数请求覆盖同名旧请求，旧截止时间没有独立定时任务，因此不会撤销较新的值。
- 底层 raw 请求跟随实体当前有效模型；切换模型会重新验证新模型的控件约束。
  `applyPreset` 的身体/参数带 model scope，不能把 raw 请求当成已有的逐模型适配规则。
- 动画、参数相互独立；战败三相位的身体动画始终优先于手动动画。
  状态映射按身体和逐参数分别合成：战败 > 显式 > 受伤 > 近战命中 > 换卡 > 弹幕战开启 > 日常。
  发射器等既有身体 hint 在显式请求之下、自动事件/日常之上；新符卡节点直接使用显式请求。
  战败时未被战败预设同名覆盖的显式参数仍可应用；无战败映射继续既有模型无关回退链。
- 客户端目录按已加载模型 assembly 的身份缓存，资源替换后重建；实体缓存不强持有外部 animatable。
  断线清除本次会话的绑定和调试缓存。未安装 OYSM 时不安装其专用内置模型目录。

## 手动命令

服务端命令（OP 2；标准服务端选择器；不支持的实体会跳过，支持的目标先批量校验再修改）：

```text
/yhysm anim play <targets> <clip> [ticks]
/yhysm anim stop <targets>
/yhysm param set <targets> <parameter> <number> [ticks]
/yhysm param clear <targets> [parameter]
/yhysm clear <targets>
/yhysm state <targets>
/yhysm preset list <model>
/yhysm preset apply <targets> <model> <preset> [ticks]
```

客户端只读目录（省略目标时使用指向／已选调试目标）：

```text
/yhysm inspect [entities]
/yhysm anim list [entities] [page]
/yhysm wheel [entities] [page]
/yhysm param list [entities] [page]
/yhysm param get <entities> <parameter>
```

列表中的蓝色条目只会**填入**命令，用户确认提交后仍由服务端权限与选择器校验。
客户端目标解析沿用旧 `/yhysm` 工具，支持可见 UUID、名称和 `@e` 的 type/sort/limit 等有限选项；
它不是完整的服务端选择器解析器。跨范围／复杂筛选的修改应使用服务端命令。
Editor 输入框的 Tab 补全复用真实目录。type 使用原生命令/注册表补全，UUID 附类型和距离。
原生查询的客户端选择器仍只支持上述有限子集。
`anim play`、`param set/get/clear`、数值选项及 `preset list/apply` 都提供候选。
候选优先来自可见目标的实际模型；不能在客户端解析目标时回退到本地目录/共享定义合集。
`YsmCommandSuggestions` 把命名 provider 注册在服务端实际参数节点上，客户端注册只读目录回调。
不能再只在客户端重复注册同名参数：Forge 合并时保留原参数节点，会丢失新补全回调。
补全不执行修改；完整选择器与权限仍由服务端原命令检查。

`/yhysm editor` 打开第三模式；配置是世界共享 SavedData，不是客户端私有绑定。
保存需要 OP 2，profile 保存按模型 revision 做冲突检查；显式本地试播不会发包。
顶栏“保存并绑定”先保存 profile，确认成功后再发原绑定请求；两个请求分别校验 revision，
绑定失败不回滚已保存的共享预设，UI 必须明确提示部分完成。“仅保存预设”/Ctrl+S 不改绑定。
Raw JSON 编辑同一个 `format=1` profile，没有另建存档 schema 或脚本 API；未应用文本保留，
与表单同时编辑时检查基线，拒绝静默覆盖。暂停与原有 Editor 一致，单人暂停、联机不暂停。

真实片段目录不混入 `cast` / `angry` 等 legacy 语义提示。
原生转盘的同一条目可以同时具有动画和配置组入口，子菜单也会单独标识。
radio 标签中的复杂表达式仅保留在指令诊断目录；普通 Editor 隐藏不能直接操作的条目，不执行或猜测其含义。
`param get` 展示帧间基础输入及 YH 请求；实际播放仍用 `/yhysm debug inspect` 核对。

## 0.28.0 场景与符卡节点（破坏性调整）

`enter_combat` 改为真实符卡第一次 tick 的进入边沿，不再把 `getTarget()!=null` 当作弹幕战开始。
`spell_switch` 在已有弹幕战中开始另一张符卡时发出；普通 phase 与并行子图不发换卡事件。
`melee_attack` 由妖怪成功的 `doHurtTarget` 发出。三个事件都使用有限时长预设，不反向改变战斗判定。

`ysm_render` 保留节点类型名，但要求 `operation` 字段；旧 animation/clear/clear_target 不兼容。

| operation | 字段 | 行为 |
| --- | --- | --- |
| model | model、texture、duration | 临时设置模型与纹理，不写永久绑定 |
| preset | model（可空）、preset、duration | 应用共享预设，不替换模型 |
| animation | clip、duration | 一个真实片段，不解析旧语义 hint |
| parameter | parameter、value、duration | 单个数值参数；组合表情用 preset |
| clear | 无 | 清除手动动画/参数和旧动画 hint，不改模型绑定 |
| reset_model | 无 | 清除临时模型/纹理，恢复原绑定或本地预览选择 |

duration=-1：预设使用定义时长，片段/参数使用配置默认时长，模型保持；0：保持；正数：tick。
preset 的 model 留空时使用当前临时模型或服务端保存的 UUID/type 绑定。
客户端资源包默认绑定不在服务器可知范围，此时请填写预设所属模型；不根据预设名称猜模型。
新节点通过 `YHModel` 共用显式请求与限制；预设缺失/尚未同步时跳过该表现操作，不中断弹幕。
旧底层 hint 字段仍供发射器等消费者使用。
符卡预览只有明确的本地模型选择或 model 节点生效后才委托渲染；法阵先渲染。
正交、透视和截图共用同一入口；预览选择及请求只修改假实体。

## 兼容和验证边界

本次没有修改 OYSM/TLM 源码、冻结外部渲染 API 或已有 KubeJS 事件。
`ysm_render` 节点有意更换旧设计，信号包改用事件表；客户端与服务端必须同时更新到 0.28.0。
内部适配基线是整合包中的 `openysm-forge-2.6.6.2-HCD-0.1.2.jar`，没有宣称支持官方 YSM 3.0。

```powershell
.\gradlew.bat compileJava organizeLang --no-daemon --console=plain
```

UI/动画开发以用户 checklist 的实机操作为准，不把大量自动测试加到每个提交前。
现有 `runModelPresentationTest [-PoysmJar=...]` 只在相关状态/数值恢复契约确实需要核实时选用；
它不启动游戏，不能证明模型渲染、异步更新、重播、多观察者或战败回归。

历史：2026-09-05 旧开发包的独立专服 smoke 通过 20 项真实 KubeJS 断言：未安装 OYSM/TLM、实际 YH 实体、
`YHModel` 全局绑定、命令／脚本请求互通、`listModels/listPresets/describePreset/profileJson`、
默认时长与命令 `applyPreset`、清理、跨 tick 到期。测试共享定义由隔离测试夹具写入 SavedData；
这不是新增了公开脚本保存 API，也不证明客户端网络保存或模型画面。
