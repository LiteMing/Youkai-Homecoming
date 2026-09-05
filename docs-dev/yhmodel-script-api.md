# YHModel — 0.28 开发中的模型表现接口

状态：表现请求、共享预设、服务端权威信号与同步已接入。
本提交仅提供供应商无关的请求与数据契约；原生播放适配、目录和第三种 Editor 分开接入，
不把请求被接受当成模型已经播放，也不是 0.28.0 发布声明。

## 公共契约登记

- Java：`dev.xkmc.youkaishomecoming.compat.ysm.YHModel`。
- KubeJS：同一个类注册为全局 `YHModel`，没有第二套实现或客户端执行权限。
- 目标参数类型：`YsmRenderOverrideTarget`；实际世界消费者为 YH 妖怪和发射器。
- 世界修改必须在服务端线程执行；客户端真实实体、已移除实体会拒绝修改。
- `PreviewCardHolder` 接入独立内存状态和模拟时钟；预览请求不向真实世界写入。
- 接口提交的是表现请求。专用服务器无须安装 OYSM，也无须读取客户端模型资产；
  请求被接受不代表每个观察者都拥有相同模型、动画或控件。

| 方法 | 含义 |
|---|---|
| `supports(target)` | 对象是否实现当前表现目标接口；不表示模型已绑定或客户端资源存在 |
| `defaultDuration()` | 配置中的默认 tick 数，并受最大持续时间限制 |
| `play(target, clip, ticks)` | 请求一个真实动画片段，当前仅 LOOP；重复调用会生成新的重播序号 |
| `stop(target)` | 清除新动画请求，恢复现行 legacy `ysm_render`／日常表现，不清模型绑定或参数 |
| `setParameter(target, name, value, ticks)` | 设置一个数值参数的临时覆盖 |
| `clearParameter(target, name)` | 清除此参数的新覆盖 |
| `clearParameters(target)` | 清除全部新参数覆盖，不停止动画 |
| `clear(target)` | 停止新动画并清除新参数，不修改模型、纹理绑定和旧 `ysm_render` |
| `getAnimation(target)` | 当前请求的片段名；无请求为 `""`，不是客户端实际选中结果 |
| `getAnimationTicksRemaining(target)` | `0` 无请求，`-1` 保持，其余为剩余 tick |
| `getParameter(target, name)` | 当前请求的数值覆盖；无覆盖为 `null`，不是模型的基础值 |
| `listModels(target)` | 列出当前服务端世界已保存配置的模型 ID；不等于客户端已安装模型目录 |
| `listPresets(target, model)` | 列出该模型在服务端登记的稳定预设 ID，排序后的只读 Java List |
| `describePreset(target, model, id)` | 已登记预设的说明；不存在则抛出 IllegalArgumentException |
| `profileJson(target, model)` | 该模型共享定义的 format=1 JSON（不包含 revision）；未登记为合法空定义 |
| `applyPreset(target, model, id)` | 原子应用模型限定的预设，使用其默认 ticks |
| `applyPreset(target, model, id, ticks)` | 原子应用指定持续时间；0 保持，内部 -1 表示使用定义时长 |

预设查询和应用需要服务端线程上的真实 YH 实体；客户端通过 `YsmClientProfiles` 读取同步副本。
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

## 参数与合成边界

- 只接受有限数值与 `v.name` / `v.roaming.name`；等价 `variable.` 前缀会规范化。
  不接受任意表达式、函数或数组。数量、时长、数值上限来自 `YHModConfig`。
- 动画和参数分别保留自己的截止时间，新请求不会被旧请求的到期清理撤销。
- 身体优先级：战败 > 显式请求 > 旧符卡提示 > 受伤 > 进入战斗 > 日常。
  参数逐名合成；战败未覆盖的显式参数继续有效，表现不驱动战斗或物理状态。
- 世界共享配置使用 `format=1` JSON；profile 按模型 revision，实体绑定独立 revision。
  写入需要 OP 2，冲突或权限失败不覆盖其他玩家的数据。
- 原生模型目录、数值作用域恢复及最终动画播放属于客户端适配，不要求专服加载供应商。

## 服务端命令

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

## 兼容与验证

新增内部实体同步字段及 profile/override 包字段，客户端与服务端须使用同一份 YH 构建。
既有 KubeJS 事件、`YHStgApi` 与 `ysm_render` 语义保持不变，不提前修改正式版本号。

```powershell
.\gradlew.bat compileJava organizeLang --no-daemon --console=plain
```

原生动画、表情、界面、双客户端或战败画面需要实机验收；本轮不追加 UI 自动测试。
命令参数预留命名补全 provider；原生目录回调随客户端适配一起接入。
