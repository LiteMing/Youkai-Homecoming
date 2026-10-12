# Remilia projectile models

These projectile entries are declared in `ysm.json` and use the Remilia assembly (`remilia`).

| 用途 | model | 备注 |
|---|---|---|
| Gungnir | `remilia/trident` | 使用 `models/gungnir_projectile.json`；长轴已对齐 projectile 的 +X 方向 |
| 魔法球 | `remilia/small_fireball` | 使用 `models/magic_ball_projectile.json`；也支持 `remilia/fireball` 和 `remilia/ender_pearl` |

YH 0.32.1 起，在 `ysm_projectile.model` 或 shooter 的 `ysm.model` 中直接使用完整入口路径。
`remilia/main` 也可用于弹幕；projectile 应使用完整入口路径。
`model_scale`、`offset_forward`、`offset_right`、`offset_up` 仍由调用方按弹幕尺寸和发射点调节。

Gungnir 的角色动画 `yh.battle.gung_prepare`、`yh.battle.gung_ready`、`yh.battle.gung_shoot` 均保持 `emoji_exclamation` scale=1，覆盖整个动作片段。

角色战斗动画已归并到 `animations/extra.animation.json`，动画名称保持 `yh.battle.*`。内置模型与 run 环境使用同一份已声明资源。
