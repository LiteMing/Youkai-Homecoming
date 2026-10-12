# OYSM / YH 表现边界（0.34.0）

当前配套基线是 OYSM HCD 0.4.0 与 YH HCDRS 0.34.0；尚未适配官方 YSM 3.0。

OYSM 拥有通用模型加载、目录、渲染、动画控制器、数值租约、预览、editor、preset registry、
状态绑定配置、存储与网络同步。YH 通过公开 API 注册资源和定义、声明业务信号，
并将权威战斗状态和显式请求投影为 OYSM 的表现输入。

| 契约 | 所有者 |
| --- | --- |
| `ExternalModelPackAPI` / `ModelReference` | OYSM：模组资源来源与完整模型入口 |
| `ExternalPresetAPI` | OYSM：定义、公开 ID、语义绑定、目录、保存与同步 |
| `ExternalModelEditorAPI` | OYSM：独立原生 editor |
| `ExternalLivingPresentationAPI` / `ExternalModelRenderAPI` | OYSM：预览、参数、原生渲染与回收 |
| `YHModel` / `YsmPresentationState` | YH：实体显式请求、服务端时钟和归属 |
| `YsmPresentationSignals` / resolver | YH：战败、符卡、移动与战斗优先级 |
| `YsmOverrideData` | YH：世界 UUID/type 模型绑定 |
| spell editor 与虚拟弹幕场景 | YH：符卡玩法内容 |

YH 不保留旧 profile、通用 editor panel、供应商私有缓存反射或 YH-owned preset 文件。
对外 ID 由 OYSM 分配，YH 消费公开 registry 的结果；CreatureChat 脚本也读取同一 registry。

未来换成官方 YSM 时，需要为上述公开契约实现实际适配并进行渲染/联网验证。
官方模组 ID、Java 包和现有扩展机制与 OYSM 不同，不能仅替换包名或 mod ID 宣称兼容。
当前软依赖入口在缺少 OYSM 时跳过供应商能力，保留 YH 战斗和基础渲染。

使用和脚本契约见 [YHModel](yhmodel-script-api.md)，实机项目见
[editor 验收](../docs/ysm-editor-user-checklist.md)。
