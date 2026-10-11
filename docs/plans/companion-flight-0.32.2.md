# HCDRS 0.32.2：日常伴飞局部避障与稳定分位

本次修复只服务日常 `LEGACY + STEER` 伴飞。战斗位移继续由符卡 caster_moves 等现有机制负责，
不修改攻击优先级、PLAYER_LOCAL 身体 yaw 坐标语义、显式世界目标或位置锁定行为。
完整脚本契约见 [yhcompanion-script-api.md](../../docs-dev/yhcompanion-script-api.md)。

## 实现

- 当前维度内按玩家分组分配稳定 Slot，首个伙伴精确保留旧轨道；新增伙伴在另一侧及后续成对队列展开。
  停止和配置切换立即释放，卸载后由活动刻租约淘汰；已有伙伴不因空位重新编号。
- 日常短程运动使用完整实体 AABB 扫掠，避免只检查平移终点漏掉薄墙；前瞻至少覆盖本刻位移。
  受阻后跨 tick 试探抬升、左绕、右绕，成功方向短暂保持；全部受阻时衰减速度，保留原版碰撞裁剪。
- 原目标无效时沿玩家方向回退，仍失败再检查头顶；所有候选都检查高度、边界、已加载区块和碰撞。
  回退保持为玩家相对位置并定时恢复原目标，不覆盖脚本请求。
- 所有端点、回退、恢复与运动探测共用每实体每 tick 两次主动探测硬上限。
  不扫描世界实体、不递归寻路、不强制加载区块。参数唯一默认源为 YHModConfig，中文由 organizeLang 生成。

## 自动验证

2026-10-11，Java 17 / Minecraft 1.20.1 / Forge 编译基线 47.1.47：

```powershell
.\gradlew.bat --offline -Pll_version=2.7.0+HCDRS_0.32.2-pre-companion compileJava organizeLang runCompanionFlightMathTest runCompanionFlightSettingsTest runCompanionFlightAvoidanceTest
```

成功，耗时 3 分 15 秒；几何 104 项、真实配置解析 33 项、模拟障碍与 Slot 生命周期 13808 项检查通过。
模拟场景涵盖空场追踪、矮墙抬升、低天花板侧绕、上/左受阻后右绕保持、封闭受阻、薄墙扫掠、
高速前瞻、低速上限、回退/头顶候选/恢复、分位隔离、空位复用与卸载淘汰。
它们不构成 Minecraft 服务端性能或视觉验收。

CreatureChat Web 源工程运行以下窄范围测试，38 项通过，未修改消费者：

```powershell
node --test tests/linked_companion.test.mjs tests/reimu_companion_exit.test.mjs tests/companion_config.test.mjs
```

中文分片与生成文件一致、英文 key 完整，车万女仆相关中文固定断言保留。
正式版本在功能提交后单独更新；交付前构建 jarJar 并核对生产重映射、refmap、版本、类与语言资源。

## 部署与待验收

目标：`D:\.Minecraft\mchxx\.minecraft\versions\归家之梦ReSt\mods`。
旧目标 JAR 按整合包规则移入 `mods/backup`。运行中游戏不得直接替换；选择窗口显示后实际计时 60 秒，
允许用户选择立即正常保存退出并重启或延后替换，记录选择与部署哈希。

实机仍需验收：两名以上伙伴长期分位、矮墙和树林追踪、低天花板、墙边目标回退、
快速转身与高速移动、走路/飞行切换、战斗抢占、区块卸载重载及单人/服务器网络观感。
需要实际服务端测量确认探测耗时与 tick 负载；模拟检查和重启成功不能代替这些结果。
