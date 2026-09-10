# Remilia projectile models

These projectile entries are declared in `ysm.json` and use the Remilia assembly (`YH内置/remilia`).

| 用途 | model | slot | 备注 |
|---|---|---|---|
| Gungnir | `YH内置/remilia` | `minecraft:trident` | 使用 `models/gungnir_projectile.json`；长轴已对齐 projectile 的 +X 方向 |
| 魔法球 | `YH内置/remilia` | `minecraft:small_fireball` | 使用 `models/magic_ball_projectile.json`；也支持 `minecraft:fireball` 和 `minecraft:ender_pearl` |

在 YH 的 `ysm_projectile` 配置中，将 `model` 设为 `YH内置/remilia`，将 `slot` 设为上表对应值即可。`model_scale`、`offset_forward`、`offset_right`、`offset_up` 仍由调用方按弹幕尺寸和发射点调节。

Gungnir 的角色动画 `yh.battle.gung_prepare`、`yh.battle.gung_ready`、`yh.battle.gung_shoot` 均保持 `emoji_exclamation` scale=1，覆盖整个动作片段。

角色战斗动画已归并到 `animations/extra.animation.json`，动画名称保持 `yh.battle.*`。内置模型与 run 环境使用同一份已声明资源。
