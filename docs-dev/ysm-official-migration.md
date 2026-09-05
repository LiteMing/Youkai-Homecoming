# OYSM → 官方 YSM：0.28 表现层迁移边界

目标：现在交付可用编辑器与模型表现控制，同时使将来官方开源版成熟后能替换适配代码，
不要求重写 YH 战斗、脚本、预设存档或 Editor。
这不是官方 YSM 已适配的声明，也不要求修改第三方源码。

## 已核对的基线

- 当前运行适配：整合包的 `openysm-forge-2.6.6.2-HCD-0.1.2.jar`；模组 ID `yes_steve_model`。
- 冻结渲染入口：`rip.ysm.api.client.ExternalLivingRenderAPI`。安装 JAR 的内部实现类实际上在
  `com.elfmcys.yesstevemodel.*`，不是 `rip.ysm.client.*`。
- 官方仓库本地核对：`D:\IdeaProjects\YesSteveModel` @ `fb25a938`，
  `gradle.properties` 为 `3.0-dev-forge+mc1.20.1`，模组 ID `ysm`，Java 包 `com.elfmcys.ysm.*`。
- 官方已有 `api.rendering.v0` 的 `RenderModelEvent`、`RegisterRenderStateModifierEvent`，
  `api.model.v0.RegisterModelLocatorEvent`，以及 `@YsmExtension` 链接检查生成机制。
  当前 `TargetKind` 枚举为 PLAYER / PLAYER_LEFT_ARM / PLAYER_RIGHT_ARM / PLAYER_BACKGROUND /
  MAID / PROJECTILE / VEHICLE。**这些事件的存在，不等于已有任意 YH LivingEntity 的同步委托入口。**
- 本次在官方源码中未找到 OYSM 同名 `ExternalLivingRenderAPI` / `ExternalLivingRenderer`，
  也没有执行官方版启动/渲染验证，不能靠修改 mod ID 或替换包名前缀宣称迁移完成。
- 原生 metadata 仍能看到 `extra_animation` / `extra_animation_buttons` 等字段，但官方
  `info.ModelProperties` 使用 `extraAnimationOrderMap()` / `extraAnimationButtonsMap()` 等访问器，
  和当前 OYSM 反射探测不是可直接互换的二进制契约。

## 已经解开的部分

| 层 | 文件 | 迁移时保留的契约 |
| --- | --- | --- |
| 可携带定义 | `YsmModelProfile` | `format=1`、模型 ID、命名预设、真实 clip、数值参数、trigger ID；无 provider 类名、控制器 ID、反射方法名 |
| 世界共享数据 | `YsmProfileData`、`YsmOverrideData` | 预设按模型 revision、绑定独立 revision；服务端持久化、权限与冲突校验 |
| 显式请求 | `YsmPresentationState`、`YHModel` | 服务端绝对时钟、到期、重播序号、模型 scope；Java/KubeJS 同一入口 |
| 权威信号 | `YsmPresentationSignals`、`YsmPresentationRuntime` | 服务端移动/战斗边沿/受伤/beaten 状态；无客户端资产读取、不更改物理或战斗 |
| 优先级合成 | `YsmPresentationResolver` | 身体与逐参数分层；无 Minecraft client / OYSM 类引用，契约测试可独立执行 |
| 原生目录 DTO | `YsmModelCatalog` | clips、wheel（可同时有 clip/config）、子菜单、控件/选项/范围；不携带第三方对象 |
| 工作区 | `YsmEditorController`、`Ysm*DockPanel` | 本地预览与世界写入分离；仅消费上述定义/目录/请求，服务端保存不依赖客户端供应商 |
| 命令补全 | `YsmCommandSuggestions`、`YsmPresentationClientCommands` | 服务端实际节点的命名 provider；客户端只读目录建议，执行仍由服务器校验 |

这里的 `ysm` 名称表示模型格式与兼容领域，不是“所有类都依赖 OYSM”。
不创建尚无第二个真实消费者的 Backend/Registry/Factory；迁移真正发生时在这些窄接缝替换即可。
目录文件夹按真实模型 ID/动画名前缀分层；原生包名仅为可选显示信息，不写进 profile。
模型候选小预览复用 YH 自己的隔离假实体，不打开 OYSM Screen、不设置玩家模型。
共享 profile 仍使用同一 `format=1` 契约。Editor Raw JSON 可附带 `binding`（scope/target/model/texture/parameters），
解析后分别走既有 profile/绑定请求。绑定外观是供应商无关的数值默认值，随 UUID/type 持久化；
旧绑定无 parameters 时为空。不把 Editor 导出扩展传给共享 profile API，不新增供应商存档格式。

## 0.28.0 场景化与符卡预览

主流程现在围绕目标、模型和场景：弹幕战开启、换卡、近战命中及切换现有模型预设；
动作与表情预设是独立 Dock 标签，场景配置直接进入该标签；原生素材目录作为辅助页，素材试听不套用
运行时 preset ticks。新增场景仍只使用 `YsmModelProfile.Trigger` 和普通事件时间/序号，
没有在预设或信号包中引入 OYSM 类名、回调或播放器控制器。

`ysm_render` 有意替换为必填 operation 的节点设计（完整字段见脚本契约文档），无旧设计兼容层。
它与脚本共用 `YHModel`；`YsmRenderOverrideTarget.ysmProfile` 的真实实体实现读取服务端 SavedData，
隔离预览实现读取同步 DTO，不将 Minecraft client 类引用引入 common 接口。
同名预设不用于猜模型；服务器不知道客户端资源默认绑定时，节点显式指定预设所属模型。

符卡正交、透视和截图共用 `OrthographicViewport.renderPreviewCaster`，只在假施法者
存在明确模型选择/临时模型覆盖时调用原有 `YSMClientCompat` 委托，法阵顺序不变。
关闭或重置预览时释放供应商缓存；不设置玩家模型、不写真实实体绑定。
迁移官方版仍替换下面的窄接缝，无需重写场景 UI、节点操作或服务端战斗事件。

## 仍然专属于 OYSM 的接缝

### 1. `YSMClientCompat`

保留现有模组存在检查、反射 `ExternalLivingRenderAPI`、模型/纹理列表、绑定优先级与
旧符卡 hint 兼容。`special=clip+fallback...` 是**当前 OYSM 控制器的语法**，不是服务器预设格式。

迁移要重新实现：任意 living entity 的委托入口、模型和纹理索引、clip 请求与 native 播放状态诊断。
`GeneralYoukaiRenderer` 委托顺序仍固定为法阵 → YSM → TLM → 原版，不在本任务添加渲染后端注册表。
官方若没有通用 living entity 入口，应通过本 fork 自有适配/事件/mixin评估；不能要求第三方改源码。

### 2. `YsmClientPresentationBridge`

这是新增内部 API 反射的唯一落点，各能力独立探测、失败缓存，渲染循环不刷日志：

| 能力 | 当前 OYSM 依赖 |
| --- | --- |
| 元数据目录 | `ClientModelManager` → `ModelAssembly` → animation bundle / ModelProperties / config forms |
| 模型文件夹标签 | 可选 `ClientModelManager.getModelPackMap()` / `ModelPackData.getPath/getName()`；缺失时使用真实 ID 路径 |
| 运行时对象 | `RendererManager.getExternalLivingRenderer()`、`getCachedAnimatable(LivingEntity)`、model readiness / assembly identity |
| 数值覆盖 | animatable evaluation context / public variable storage、StringPool、`getScoped/setScoped`、roaming Struct property |
| 重播 | `AnimationData` 中 `player.cap` 控制器、`PredicateBasedController.clearAnimation()` |
| 生命周期 | render 前 join 已提交的异步工作；数值租约跨到下一次异步求值完成；assembly 身份变化失效；断线/换世界恢复并清缓存；实体只弱引用 |
| 隔离预览回收 | 在关闭/重建时对 OYSM `ExternalLivingRenderer.cache` 做一次可选字段探测，join 后仅移除 YH 标记的假实体；不遍历或删除真实实体缓存 |

这里没有执行任意用户 Molang，也没有导出内部对象供脚本缓存。
未来官方 adapter 必须返回同一个 `YsmModelCatalog`，消费同一个 `Resolved`；
适配器必须明确异步求值发生在 render 前还是 render 内。当前 OYSM 基线在 render 前提交任务，
所以数值租约跨帧交接并在 join 后恢复；不能退回只包住同步 render 的假作用域，也不能照搬变量地址。

`YsmParameterOverlay` 本身是供应商无关的数值作用域恢复工具，但它**不是**全局模型脚本沙盒：
模型在作用域内主动写入不同的新值时保留写入；动画自身其他脚本副作用不回滚。

### 3. `YsmModelPackInstaller`

当前只在 `yes_steve_model` 存在时安装 OYSM 目录格式的内置模型。
官方格式、安装路径、manifest 白名单、纹理默认名、模型 ID 命名/加密加载规则必须重新核实。
不要在未支持官方时把检查改为 `old || new`，否则会在错误目录安装不兼容资产。

## 数据与公开 API 的迁移策略

1. **保留 YH 预设 schema 与 `YHModel` 脚本契约。** 原生模型包格式变化不直接渗入 SavedData。
2. 模型/clip/变量 ID 若可保持原值，则预设无需转换。若官方确实改名，做显式、可审阅的导出 JSON 迁移；
   不根据 `extraN` 序号、文件夹路径或中文显示名自动猜映射。
3. raw `play/setParameter` 故意跟随当前模型；`applyPreset` 带模型 scope。scope 不一致时暂停该覆盖，
   时间仍正常流逝，不能在新模型上随便改同名变量。
4. 模型原生目录是客户端能力；专服脚本只能枚举服务端保存的组。保持这条边界，不引入专服渲染类。
5. body 和参数优先级、持续状态和瞬时事件语义不得因为更换供应商而改变。
6. 如果正式公开 API 需要扩展 once/hold 等能力，先验证播放器语义再登记新字段/格式版本；
   不用循环+定时器冒充单次或保持末帧，不静默重解释 `ticks=0`。
7. 同一个发布包是否双支持 OYSM/官方是后续明确选择。两者共存时必须指定行为并测试重入，
   本次不隐式选择优先级，也不添加未测试的双后端模式。

## 建议迁移执行顺序

- 固定官方 commit/JAR 与目标 Minecraft/Forge 版本；先证明任意 YH living entity 能渲染。
- 写目录适配，比较真实 clip、转盘、同时存在的配置能力、数字选项和不可执行表达式。
- 打通单实体 LOOP/重播/停止；再处理数值作用域与异步线程安全。
- 将 `YsmPresentationResolver.Resolved` 接到官方适配，保持 common/entity/editor 文件不新增官方类型 import。
- 核实内置资产安装与白名单，只改本 fork 自有资源；外部模型由整合包作者迁移。
- 同步更新本指南、脚本契约和用户 checklist；完成下面矩阵后才切换默认支持目标。

## 迁移验收矩阵

- [ ] 不装模型模组 / 不装 TLM 都能启动、打开 Editor、进行可读降级。
- [ ] OYSM 基线无回归，或维护者明确同意停止支持该基线（不能静默删除）。
- [ ] 官方：普通 YH 妖怪、Boss、发射器、隔离假实体均可委托；原渲染顺序和重入保护不变。
- [ ] 至少两份不同骨架/表情结构模型；缺 clip/控件/资源按能力降级。
- [ ] 同片段重播、参数到期与非零基础恢复、模型脚本主动写入、换模型、资源重载、异步模式。
- [ ] 双观察者、晚跟踪、断线/换世界、预设保存冲突、无权限请求和专服无模型资产。
- [ ] beaten 三相位、保持倒地、治疗恢复；不使用 SWIMMING，不压碰撞箱，不更改 AI/伤害。
- [ ] 导出同一份 `format=1` JSON，在新适配上重导入；不引入 provider 字段或改写公共脚本名称。

确实需要检查数值存储/序列化等代码契约时可选用 `runModelPresentationTest` 和 `-PoysmJar=...`；
不把它作为每个 UI 提交的前置步骤，更不是实机渲染/交互/联机矩阵的替代品。

## 上游合并复核登记

- `YoukaisHomecoming.java`：增加两种 profile 包注册及命名补全 provider 初始化，保留全部上游注册/监听器。
- `GeneralEventHandlers.java`：登录时多发送共享 profile 快照，保留其他登录处理。
- `GeneralYoukaiEntity` / `ShooterEntity`：表现快照随实体 NBT 持久化，行为信号仍为瞬态；近战表现只观察 `doHurtTarget` 的成功结果，不改伤害、AI 或 beaten 状态机。
- `SpellRuntime` / `SpellCardWrapper`：真实首次 tick 发出进入/换卡事件；并行子图不发换卡事件。信号包改为事件表，两端同版本更新。
- `OrthographicViewport` / `SpellSnapshotRenderer`：共享有显式模型时才运行的预览委托；未选择模型时保留原行为。
- `SpellPreviewScreen` / `EditorMode`：第三模式局部分支，独立停靠布局与输入分发；缩放保留模式内快照，确认后切模式返回真实 Screen；不把模型表塞进符卡 schema。
- `RawJsonDockPanel`：复用原多行 widget 的构造、撤销和缩放时的光标/历史恢复；不改符卡/魔法阵解析逻辑。
- `YSMCompatConfig.RenderBinding` / `YsmOverrideData`：新增绑定数值默认外观，旧存档缺省为空；绑定包同步更新两端，公开脚本 API 保持原契约。
- `PreviewCardHolder`：仅隔离模型预览更新移动、步幅与飞行输入，以驱动模型原生状态机；不 tick 世界或修改真实实体物理。
- `build.gradle`：定向测试任务与隔离专服／客户端 smoke 参数，仅显式启用时生效；datagen 仍禁用。`gradle.properties` 按维护者要求在最后提升到 0.28.0。
- 中文新分片经 organizeLang 生成；英文运行时文件手工维护；未改上游生成器或配置脚手架。

## 历史隔离验证环境（非本轮 UI 验收）

以下环境与结果属于 2026-09-05 旧开发包；2026-09-06 UI 修订只做必要编译/生成/打包。
旧客户端 smoke 的“不暂停”和旧成功消息断言已经过时，不应原样重跑并当作当前实机验收。

测试任务会排除运行目录默认 OYSM shadow JAR，防止“无 OYSM”测试误含后端，或显式 `oysmJar`
被开发 shadow JAR 抢先加载。实际 JAR 测试同时核对类的 CodeSource 路径。
隔离专服启动可用 `runServer -Pysm_smoke_run_dir=<独立测试目录> -Pysm_no_tlm=true -Popenysm_compat_jar=`；
默认 run 目录和客户端依赖配置不变。该隔离专服配置会额外省略开发客户端的 Just Enough Characters
和 Lightspeed RE：前者在 DEDICATED_SERVER 构造阶段加载客户端 `SuffixArray`；后者的
`resources.VanillaPackResourcesMixin` 在服务端引用 `Minecraft` 客户端类。两者都实测在世界启动前失败，
与 YH 表现层无关；不修改这些第三方源码，也不从默认客户端依赖中删除它们。
只在测试目录准备自己的 server.properties，勿使用玩家存档做 smoke test。

隔离客户端使用以下显式参数；不设置时仍使用原来的 `run` 目录与默认启动配置：

```powershell
.\gradlew.bat runClient '-Pysm_smoke_client_dir=build/ysm-client-smoke-20260905' '-Pysm_no_tlm=true' '-Popenysm_compat_jar=' '-Dorg.gradle.jvmargs=-Xmx768m' --no-daemon --console=plain
```

该配置以 1280×720 窗口、测试用户名 `YsmEditorSmoke`，快速进入测试目录中
`saves/ysm-editor-smoke-world`。先在独立目录准备自有世界与 KubeJS smoke 脚本；
参数本身不创建世界/测试脚本、不执行完整 UI 操作，也不应指向玩家整合包或存档目录。

2026-09-05 当时已在该实例确认两供应商缺失、旧第三模式不暂停集成服务器、共享 profile 同步、
profile 保存及自身 revision、UUID 绑定保存、已保存预设应用确认，共 9 项真实运行检查；
随后正常保存并退出。临时脚本、`logs/latest.log` 的 `YH_EDITOR_SMOKE` 标记与
`screenshots/ysm-editor-*.png` 原图保留在该测试目录，不打入开发 JAR。
界面由真实 Screen 渲染，保存流程由另一个真实 `YsmEditorController` 驱动；
这不是逐项键鼠表单验收，也没有执行 OYSM 原生渲染或双客户端测试。
不要用这次无供应商结果代填未来官方适配的迁移矩阵。
