# OYSM 原生 editor 与 YH 联动验收（0.34.0 / 0.4.0）

构建和无图形契约检查不等于下列实机项目通过。

1. `/openysm editor` 与 `/yhysm editor` 打开同一 OYSM 界面；仅装 OYSM 时也能使用前者。
2. 模型页搜索并选择 `TUMC/东方Project/「博丽灵梦」.ysm/main` 或 `touhou/reimu2.0.5.ysm/main`。
3. 打开模型参数，切换 checkbox、slider、radio，核对隐藏物件与原生预览一致。
4. 打开动画轮盘，核对子菜单与参数组，选择动画后返回 editor；世界玩家和 NPC 不被本地试播改动。
5. 捕获参数，新建本地 key，保存得到完整 `editor:<key>`；重开界面、重进世界和第二客户端均可读取。
6. 保存源定义时保留 OYSM 分配的 `yhysm:<key>`；同时编辑时冲突提示应拒绝覆盖更新。
7. 在状态绑定页将当前模型 preset 绑定到来源声明的信号，测试状态变化；解除绑定不会恢复默认绑定。
8. 选择 arm、arrow、trident 和 vehicle 入口，核对各自几何、动画目录与 preset scope；发射器可用 projectile 模型。
9. `/yhysm preset apply` 与 CChat 普通对话动作、World Director 应用同一 ID；退出节点只释放该 owner 的覆盖。
10. 测试战败三阶段、治疗、区块卸载/重载、重新连接与无 OYSM 的基础降级。

实体模型/纹理的 UUID/type 绑定通过 YH `/yhysm set` 与 `/yhysm bind` 设置；
符卡编辑与弹幕预览仍在 YH spell editor。
