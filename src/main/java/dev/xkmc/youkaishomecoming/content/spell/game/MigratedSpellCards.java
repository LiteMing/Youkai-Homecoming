package dev.xkmc.youkaishomecoming.content.spell.game;

import dev.xkmc.youkaishomecoming.content.entity.danmaku.DanmakuHelper;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.HitBehavior;
import dev.xkmc.youkaishomecoming.content.spell.action.*;
import dev.xkmc.youkaishomecoming.content.spell.condition.SpellConditions;
import dev.xkmc.youkaishomecoming.content.spell.definition.*;
import dev.xkmc.youkaishomecoming.content.spell.difficulty.DifficultyProfile;
import dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Data-driven SpellDefinition equivalents of legacy Java SpellCard subclasses.
 * Each method builds a SpellDefinition that replicates the legacy behavior.
 * <p>
 * After verification, the corresponding legacy class should be marked @Deprecated.
 */
public class MigratedSpellCards {

	// ============================
	// SunnySpell — 三色环形弹幕
	// ============================
	// Legacy: 每 10 tick 发射 40 发 BALL，颜色按 tick/10%3 循环 (YELLOW/ORANGE/RED)
	//         速度 = 0.5 + col*0.2，随机起始角度，lifetime=80
	public static SpellDefinition sunnyMilk() {
		var id = rl("sunny_milk");
		var mainPhase = rl("sunny_milk/main");

		// 三种颜色各用 conditional + tick_interval(30, offset) 区分
		// tick%30==0 → YELLOW (speed 0.5)
		// tick%30==10 → ORANGE (speed 0.7)
		// tick%30==20 → RED (speed 0.9)
		List<SpellAction> tickActions = List.of(
				new SpellActions.ConditionalAction(
						new SpellConditions.TickInterval(30, 0),
						List.of(fireDanmakuRing(YHDanmaku.Bullet.BALL, DyeColor.YELLOW, 40, 0.5, 80)),
						List.of()
				),
				new SpellActions.ConditionalAction(
						new SpellConditions.TickInterval(30, 10),
						List.of(fireDanmakuRing(YHDanmaku.Bullet.BALL, DyeColor.ORANGE, 40, 0.7, 80)),
						List.of()
				),
				new SpellActions.ConditionalAction(
						new SpellConditions.TickInterval(30, 20),
						List.of(fireDanmakuRing(YHDanmaku.Bullet.BALL, DyeColor.RED, 40, 0.9, 80)),
						List.of()
				)
		);

		var phase = new PhaseDefinition(mainPhase, List.of(), tickActions, List.of(), List.of(), List.of());
		return buildDefinition(id, mainPhase, phase, "touhou_little_maid:sunny_milk");
	}

	// ============================
	// LunaSpell — 间歇网格弹幕
	// ============================
	// Legacy: 每 10 tick (且 tick/10%6 < 4 时) 发射弹幕
	//         120 发中 i/4%2==1 的才发射 (60发)，角度 = (i+offset)*3
	//         随机 offset，speed 0.8，BALL YELLOW，lifetime 40
	// 简化: 用 RING 60 发，random angle offset
	public static SpellDefinition lunaChild() {
		var id = rl("luna_child");
		var mainPhase = rl("luna_child/main");

		// tick%60 in {0,10,20,30} fires (4 out of 6 cycles)
		// Use conditional: tick_interval(10, 0) AND NOT tick_interval(60, 40) AND NOT tick_interval(60, 50)
		// Simplified: fire on tick%10==0 with additional check for the 4/6 pattern
		// Legacy pattern: tick%10==0 && (tick/10%6 < 4)
		// Equivalent: tick_interval(10, 0) AND (tick%60 < 40), which means NOT (tick%60 in [40,50])
		// Use: tick_interval(10,0) AND NOT tick_interval(60,40) AND NOT tick_interval(60,50)

		List<SpellAction> tickActions = List.of(
				new SpellActions.ConditionalAction(
						new SpellConditions.AndCondition(List.of(
								new SpellConditions.TickInterval(10, 0),
								new SpellConditions.NotCondition(
										new SpellConditions.OrCondition(List.of(
												new SpellConditions.TickInterval(60, 40),
												new SpellConditions.TickInterval(60, 50)
										))
								)
						)),
						List.of(fireDanmakuRing(YHDanmaku.Bullet.BALL, DyeColor.YELLOW, 60, 0.8, 40)),
						List.of()
				)
		);

		var phase = new PhaseDefinition(mainPhase, List.of(), tickActions, List.of(), List.of(), List.of());
		return buildDefinition(id, mainPhase, phase, "touhou_little_maid:luna_child");
	}

	// ============================
	// StarSpell — 流星 + 沿途火花尾迹
	// ============================
	// Legacy: 每 10 tick (4/6 周期), 发射 1 发 MENTOS RED (方向带高斯随机偏转)
	//         ShootingStar Ticker: 主弹飞行过程中每 tick 沿轨迹生成 2 发 SPARK BLUE
	//         火花: 随机 360° 水平 + 高斯 10° 仰角, speed 0.4, lifetime 80
	public static SpellDefinition starSapphire() {
		var id = rl("star_sapphire");
		var mainPhase = rl("star_sapphire/main");

		// onTrail: 每 tick 在弹幕当前位置发射 2 发 SPARK BLUE (锥形随机扩散)
		var trailActions = List.<SpellAction>of(
				new FireDanmakuAction(
						YHDanmaku.Bullet.SPARK, ColorProvider.constant(DyeColor.BLUE),
						NumberProvider.constant(2), NumberProvider.constant(0.4),
						NumberProvider.constant(80), NumberProvider.constant(0),
						NumberProvider.constant(360), NumberProvider.constant(90),
						PatternType.RANDOM,
						OriginConfig.caster(),
						new AimMode.AimModes.CasterFacing(),
						Optional.empty(), Optional.empty(), Optional.empty(),
						Optional.empty(), 1
				)
		);

		// 主弹: 1 发 MENTOS RED, speed 0.8, lifetime 60
		// AimMode: random_angle spread 40 (模拟 nextGaussian()*20 的水平分布)
		// elevation: 10 (模拟 nextGaussian()*5 的仰角分布)
		var meteorAction = new FireDanmakuAction(
				YHDanmaku.Bullet.MENTOS, ColorProvider.constant(DyeColor.RED),
				NumberProvider.constant(1), NumberProvider.constant(0.8),
				NumberProvider.constant(60), NumberProvider.constant(0),
				NumberProvider.constant(40), NumberProvider.constant(10),
				PatternType.RANDOM,
				OriginConfig.caster(), new AimMode.AimModes.Target(),
				Optional.empty(), Optional.empty(), Optional.empty(),
				Optional.of(trailActions), 1
		);

		List<SpellAction> tickActions = List.of(
				new SpellActions.ConditionalAction(
						new SpellConditions.AndCondition(List.of(
								new SpellConditions.TickInterval(10, 0),
								new SpellConditions.NotCondition(
										new SpellConditions.OrCondition(List.of(
												new SpellConditions.TickInterval(60, 40),
												new SpellConditions.TickInterval(60, 50)
										))
								)
						)),
						List.of(meteorAction),
						List.of()
				)
		);

		var phase = new PhaseDefinition(mainPhase, List.of(), tickActions, List.of(), List.of(), List.of());
		return buildDefinition(id, mainPhase, phase, "touhou_little_maid:star_sapphire");
	}

	// ============================
	// CirnoSpell — 冰弹分裂追踪
	// ============================
	// Legacy: 每 10 tick 发射 3 发 MENTOS LIGHT_BLUE，RectMover 减速，
	//         到期后 IcePopsicle 分裂为 4 发 BALL 追踪弹
	public static SpellDefinition cirno() {
		var id = rl("cirno");
		var mainPhase = rl("cirno/main");

		// onExpiry: 从弹幕位置向目标方向发射 4 发 BALL，LINE spread 60°
		var iceTrailActions = List.<SpellAction>of(
				new FireDanmakuAction(
						YHDanmaku.Bullet.BALL, ColorProvider.constant(DyeColor.LIGHT_BLUE),
						NumberProvider.constant(4), NumberProvider.constant(1.0),
						NumberProvider.constant(50), NumberProvider.constant(0),
						NumberProvider.constant(60), NumberProvider.constant(0),
						PatternType.LINE,
						OriginConfig.caster(),
						new AimMode.AimModes.DirectionToTarget(),
						Optional.empty(), Optional.empty(), Optional.empty(),
						Optional.empty(), 1
				)
		);

		// 主弹: 3 发 MENTOS LIGHT_BLUE, RING, 背向目标发射后减速
		// angleOffset=180: 初始方向旋转180°（离开目标），模拟原始 ori.rotateDegrees(180)
		var mainFire = new FireDanmakuAction(
				YHDanmaku.Bullet.MENTOS, ColorProvider.constant(DyeColor.LIGHT_BLUE),
				NumberProvider.constant(3), NumberProvider.constant(1.2),
				NumberProvider.constant(20), NumberProvider.constant(180),
				NumberProvider.constant(360), NumberProvider.constant(0),
				PatternType.RING,
				OriginConfig.caster(), new AimMode.AimModes.Target(),
				Optional.empty(), Optional.empty(), Optional.of(iceTrailActions),
				Optional.empty(), 1
		);

		List<SpellAction> tickActions = List.of(
				new SpellActions.ConditionalAction(
						new SpellConditions.TickInterval(10, 0),
						List.of(mainFire),
						List.of()
				)
		);

		var phase = new PhaseDefinition(mainPhase, List.of(), tickActions, List.of(), List.of(), List.of());
		return buildDefinition(id, mainPhase, phase, "touhou_little_maid:cirno");
	}

	// ============================
	// MystiaSpell — 球面分布子发射器
	// ============================
	// Legacy: 每 200 tick 启动 SphereShooters Ticker
	//   每 3 tick 生成 1 个 shooter (球面随机位置, 半径3, 向外飞 speed 0.5, life 60)
	//   SubSpell: 每 tick 向目标发射 1 发 CIRCLE (GREEN 或 CYAN, speed 0.6, life 80)
	//   最多 32 个 shooter
	// 迁移: BurstAction(32, 3) × SpawnShooterAction, body = fire_danmaku direction_to_target
	public static SpellDefinition mystia() {
		var id = rl("mystia_lorelei");
		var mainPhase = rl("mystia_lorelei/main");

		// Shooter 的 body: 每 tick 向目标发射 1 发 CIRCLE
		// 颜色: random_choice 每次随机选 GREEN 或 CYAN (近似原版 per-shooter 固定色)
		var shooterBody = List.<SpellAction>of(
				new FireDanmakuAction(
						YHDanmaku.Bullet.CIRCLE,
						new ColorProvider.RandomChoice(List.of(DyeColor.GREEN, DyeColor.CYAN)),
						NumberProvider.constant(1), NumberProvider.constant(0.6),
						NumberProvider.constant(80), NumberProvider.constant(0),
						NumberProvider.constant(0), NumberProvider.constant(0),
						PatternType.AIMED,
						OriginConfig.caster(),
						new AimMode.AimModes.DirectionToTarget(),
						Optional.empty(), Optional.empty(), Optional.empty(),
						Optional.empty(), 1
				)
		);

		// 每个 shooter: 球面随机位置, 向外飞, health 40, life 60
		// OriginConfig CASTER + random offset 模拟球面 → 用 CASTER 基点 + 固定半径在 SpawnShooter 中不太准
		// 更好的方案: repeat 32 次, 每次 spawn_shooter with random velocity direction
		// 球面随机方向 → velocity_x/y/z 用 random_angle 不太方便, 但 SpawnShooterAction 的 origin 可以用 CASTER
		// 简化: shooter 从 caster 位置生成, 随机方向飞行
		var spawnShooter = new SpawnShooterAction(
				40, 4f, 60,
				OriginConfig.caster(),
				new NumberProviders.RandomRange(-0.5, 0.5),
				new NumberProviders.RandomRange(-0.5, 0.5),
				new NumberProviders.RandomRange(-0.5, 0.5),
				Optional.empty(),
				shooterBody
		);

		// 每 200 tick, burst 生成 32 个 shooter (每 3 tick 一个)
		var burstShooters = new BurstAction(32, 3, List.of(spawnShooter));

		List<SpellAction> tickActions = List.of(
				new SpellActions.ConditionalAction(
						new SpellConditions.TickInterval(200, 0),
						List.of(burstShooters),
						List.of()
				)
		);

		var phase = new PhaseDefinition(mainPhase, List.of(), tickActions, List.of(), List.of(), List.of());
		return buildDefinition(id, mainPhase, phase, "touhou_little_maid:mystia_lorelei");
	}

	// ============================
	// YoumuSpell — 二刀流斩击 + 半灵弹幕 (简化重制)
	// ============================
	// 原始 vibe-coded 版本 811 行，有 10 个 Ticker。
	// 重制保留核心幻想: 斩击弹幕 + 半灵射击，根据距离/速度/血量动态调整。
	// 分为单 phase, 用 conditional 实现不同攻击模式。
	public static SpellDefinition youmu() {
		var id = rl("konpaku_youmu");
		var mainPhase = rl("konpaku_youmu/main");

		// --- 半灵持续射击 (每 8 tick) ---
		// 2 发 CIRCLE LIGHT_BLUE, 追踪目标, 血量低时加到 4 发
		var hanreiShot = new SpellActions.ConditionalAction(
				new SpellConditions.TickInterval(8, 0),
				List.of(new FireDanmakuAction(
						YHDanmaku.Bullet.CIRCLE, ColorProvider.constant(DyeColor.LIGHT_BLUE),
						new NumberProviders.ByHealthRatio(2, 5),
						NumberProvider.constant(0.9),
						NumberProvider.constant(60),
						NumberProvider.constant(0),
						new NumberProviders.ByHealthRatio(20, 35),
						NumberProvider.constant(0),
						PatternType.LINE,
						OriginConfig.caster(),
						new AimMode.AimModes.DirectionToTarget(),
						Optional.empty(), Optional.empty(), Optional.empty(),
						Optional.empty(), 1
				)),
				List.of()
		);

		// --- 斩击 (每 20 tick) ---
		// 近距离: 12 发 MENTOS WHITE 扇形
		// 远距离: 6 发 MENTOS CYAN 高速追踪 + 加速 mover
		var closeSlash = new FireDanmakuAction(
				YHDanmaku.Bullet.MENTOS, ColorProvider.constant(DyeColor.WHITE),
				new NumberProviders.ByHealthRatio(10, 18),
				NumberProvider.constant(1.8),
				NumberProvider.constant(30),
				NumberProvider.constant(0),
				NumberProvider.constant(70),
				new NumberProviders.RandomRange(-5, 5),
				PatternType.LINE,
				OriginConfig.caster(),
				new AimMode.AimModes.DirectionToTarget(),
				Optional.empty(), Optional.empty(), Optional.empty(),
				Optional.empty(), 1
		);

		var longSlash = new FireDanmakuAction(
				YHDanmaku.Bullet.MENTOS, ColorProvider.constant(DyeColor.CYAN),
				new NumberProviders.ByHealthRatio(5, 10),
				NumberProvider.constant(2.0),
				NumberProvider.constant(80),
				NumberProvider.constant(0),
				NumberProvider.constant(20),
				new NumberProviders.RandomRange(-3, 3),
				PatternType.LINE,
				OriginConfig.caster(),
				new AimMode.AimModes.DirectionToTarget(),
				Optional.of(new MoverConfigs.AccelerationConfig(new Vec3(0, 0, 0.04))),
				Optional.empty(), Optional.empty(),
				Optional.empty(), 1
		);

		// 距离条件: < 12 近距离, >= 12 远距离
		var slashAction = new SpellActions.ConditionalAction(
				new SpellConditions.TickInterval(20, 0),
				List.of(new SpellActions.ConditionalAction(
						new SpellConditions.DistanceBelow(12),
						List.of(closeSlash),
						List.of(longSlash)
				)),
				List.of()
		);

		// --- 上升斩 (目标在地面时, 每 25 tick) ---
		// 弧形上升弹幕逼迫起跳
		var risingSlash = new SpellActions.ConditionalAction(
				new SpellConditions.AndCondition(List.of(
						new SpellConditions.TickInterval(25, 5),
						new SpellConditions.TargetOnGround()
				)),
				List.of(new FireDanmakuAction(
						YHDanmaku.Bullet.BALL, ColorProvider.constant(DyeColor.LIME),
						new NumberProviders.ByHealthRatio(12, 24),
						NumberProvider.constant(1.0),
						NumberProvider.constant(70),
						NumberProvider.constant(0),
						NumberProvider.constant(180),
						NumberProvider.constant(45),
						PatternType.LINE,
						OriginConfig.caster(),
						new AimMode.AimModes.DirectionToTarget(),
						Optional.empty(), Optional.empty(), Optional.empty(),
						Optional.empty(), 1
				)),
				List.of()
		);

		// --- 下压斩 (目标在空中时, 每 25 tick) ---
		// 从上方向下的弹幕限制空中移动
		var fallingSlash = new SpellActions.ConditionalAction(
				new SpellConditions.AndCondition(List.of(
						new SpellConditions.TickInterval(25, 5),
						new SpellConditions.NotCondition(new SpellConditions.TargetOnGround())
				)),
				List.of(new FireDanmakuAction(
						YHDanmaku.Bullet.MENTOS, ColorProvider.constant(DyeColor.PURPLE),
						new NumberProviders.ByHealthRatio(8, 16),
						NumberProvider.constant(1.5),
						NumberProvider.constant(50),
						NumberProvider.constant(0),
						NumberProvider.constant(120),
						NumberProvider.constant(-30),
						PatternType.LINE,
						OriginConfig.caster(),
						new AimMode.AimModes.DirectionToTarget(),
						Optional.empty(), Optional.empty(), Optional.empty(),
						Optional.empty(), 1
				)),
				List.of()
		);

		// --- 拦截弹幕 (高速目标, 每 30 tick) ---
		// 在目标前方生成收缩弹幕
		var interceptSlash = new SpellActions.ConditionalAction(
				new SpellConditions.AndCondition(List.of(
						new SpellConditions.TickInterval(30, 10),
						new SpellConditions.TargetSpeed(0.3, ">")
				)),
				List.of(new FireDanmakuAction(
						YHDanmaku.Bullet.BALL, ColorProvider.constant(DyeColor.RED),
						new NumberProviders.ByHealthRatio(10, 20),
						NumberProvider.constant(0.5),
						NumberProvider.constant(60),
						NumberProvider.constant(180),
						NumberProvider.constant(360),
						NumberProvider.constant(0),
						PatternType.RING,
						new OriginConfig(OriginConfig.OriginMode.TARGET, NumberProvider.constant(0),
								NumberProvider.constant(0), NumberProvider.constant(0), NumberProvider.constant(0)),
						new AimMode.AimModes.DirectionToTarget(),
						Optional.empty(), Optional.empty(), Optional.empty(),
						Optional.empty(), 1
				)),
				List.of()
		);

		// --- 樱花斩 (低血量, 每 60 tick) ---
		// 旋转花瓣状弹幕
		var sakuraSlash = new SpellActions.ConditionalAction(
				new SpellConditions.AndCondition(List.of(
						new SpellConditions.TickInterval(60, 0),
						new SpellConditions.HealthBelow(0.7f)
				)),
				List.of(new SpellActions.RepeatAction(NumberProvider.constant(5), "petal", List.of(
						new FireDanmakuAction(
								YHDanmaku.Bullet.CIRCLE,
								new ColorProvider.RandomChoice(List.of(DyeColor.PINK, DyeColor.WHITE, DyeColor.MAGENTA)),
								NumberProvider.constant(8),
								NumberProvider.constant(0.7),
								NumberProvider.constant(80),
								new NumberProviders.Mul(new NumberProviders.Variable("petal"), NumberProvider.constant(72)),
								NumberProvider.constant(50),
								new NumberProviders.Sin(new NumberProviders.Mul(
										new NumberProviders.Variable("petal"), NumberProvider.constant(72)), 10, 0),
								PatternType.LINE,
								OriginConfig.caster(),
								new AimMode.AimModes.AngleOffset(new NumberProviders.Mul(
										new NumberProviders.Variable("petal"), NumberProvider.constant(72))),
								Optional.empty(), Optional.empty(), Optional.empty(),
								Optional.empty(), 1
						)
				))),
				List.of()
		);

		// --- 回旋斩 (低血量 + 近距离, 每 40 tick) ---
		// 360 度旋转弹幕
		var spinSlash = new SpellActions.ConditionalAction(
				new SpellConditions.AndCondition(List.of(
						new SpellConditions.TickInterval(40, 20),
						new SpellConditions.HealthBelow(0.5f),
						new SpellConditions.DistanceBelow(15)
				)),
				List.of(new BurstAction(30, 1, "wave", List.of(
						new FireDanmakuAction(
								YHDanmaku.Bullet.BALL, ColorProvider.constant(DyeColor.MAGENTA),
								new NumberProviders.ByHealthRatio(3, 6),
								NumberProvider.constant(0.8),
								NumberProvider.constant(60),
								new NumberProviders.Mul(new NumberProviders.Variable("wave"), NumberProvider.constant(20)),
								NumberProvider.constant(360),
								NumberProvider.constant(0),
								PatternType.RING,
								OriginConfig.caster(),
								new AimMode.AimModes.RandomAngle(NumberProvider.constant(360)),
								Optional.empty(), Optional.empty(), Optional.empty(),
								Optional.empty(), 1
						)
				))),
				List.of()
		);

		List<SpellAction> tickActions = List.of(
				hanreiShot, slashAction, risingSlash, fallingSlash,
				interceptSlash, sakuraSlash, spinSlash
		);

		var phase = new PhaseDefinition(mainPhase, List.of(), tickActions, List.of(), List.of(), List.of());
		return buildDefinition(id, mainPhase, phase, "touhou_little_maid:konpaku_youmu");
	}

	// ============================
	// LarvaSpell — 翅膀展开 + 气泡弹
	// ============================
	// Legacy: 100-tick 宏周期, 10 步 (每步 10 tick)
	//   步骤 0-2: 发射成对 Wings Ticker (20 tick, 每 tick 6 发 BALL LIME)
	//     角度 = (1 - sqrt(tick/20)) * 180 * s — sqrt 曲线，从 180° 快速衰减到 0°
	//     每发速度 0.3 + i*0.1, 生命 = rand(40,80) / vel
	//     仰角: rand(-5, 5) 小扰动
	//   步骤 5-8: 发射 5 发 BUBBLE LIME, 速度 0.65+i*0.1, 生命 = rand(40,80) / vel
	public static SpellDefinition larva() {
		var id = rl("eternity_larva");
		var mainPhase = rl("eternity_larva/main");

		// --- Wings 弹幕 ---
		// 原始 Wings Ticker:
		//   DanmakuHelper.getOrientation(dir, nor) 建立 dir-nor 平面坐标系
		//   每 tick: angle = (1-sqrt(t/20))*180*s, 然后 o.rotateDegrees(angle, rand(-5,5))
		//   6 发弹幕方向完全相同，只有速度不同 (0.3..0.8)
		//   每对翅膀启动时随机一个 ver (±45° 默认) 作为平面倾斜角
		//
		// 数据驱动还原:
		//   $ver = rand(-45, 45)            ← 每对翅膀随机一次的平面倾斜角
		//   BurstAction(20 wave) 每波开头:
		//     $elev = $ver + rand(-5,5)     ← 每 tick 随机一次仰角扰动，6 发共享
		//   RepeatAction(6, "i"):
		//     speed = 0.3 + $i * 0.1        ← 确定值，内到外递增
		//     lifetime = rand(40,80) / speed ← 每发独立随机 base，除以该发速度
		//     count=1, spread=0, AIMED       ← 同方向同 elevation，只有速度不同
		//   angleOffset = (1-sqrt($wave/20)) * 180 (正翼) / * -180 (反翼)

		// speed: 确定值 0.3 + $i * 0.1 (不是随机)
		var wingSpeed = new NumberProviders.Add(NumberProvider.constant(0.3),
				new NumberProviders.Mul(new NumberProviders.Variable("i"), NumberProvider.constant(0.1)));
		// lifetime: rand(40, 80) / speed
		var wingLife = new NumberProviders.Div(new NumberProviders.RandomRange(40, 80), wingSpeed);
		// angle: (1 - sqrt($wave / 20)) * 180
		var sqrtDecay = new NumberProviders.Mul(
				new NumberProviders.Add(NumberProvider.constant(1),
						new NumberProviders.Mul(NumberProvider.constant(-1),
								new NumberProviders.Sqrt(new NumberProviders.Div(
										new NumberProviders.Variable("wave"), NumberProvider.constant(20))))),
				NumberProvider.constant(180));
		var sqrtDecayNeg = new NumberProviders.Mul(sqrtDecay, NumberProvider.constant(-1));
		// elevation 现在是每发弹幕的小随机扰动 rand(-5,5)，
		// 而平面倾斜由 tiltAngle = $ver 处理

		// 正翼发射 (s=+1): 6 发同方向, 速度递增
		// tiltAngle = $ver → 在倾斜平面内展开扇形 (还原 getOrientation(dir, nor))
		// tiltAngle: 正翼 +$ver, 反翼 -$ver → 两翼在相反倾斜的平面上展开
		var tiltPos = Optional.of((NumberProvider) new NumberProviders.Variable("ver"));
		var tiltNeg = Optional.of((NumberProvider) new NumberProviders.Mul(
				NumberProvider.constant(-1), new NumberProviders.Variable("ver")));
		var fireRight = new SpellActions.RepeatAction(NumberProvider.constant(6), "i", List.of(
				new FireDanmakuAction(
						YHDanmaku.Bullet.BALL, ColorProvider.constant(DyeColor.LIME),
						NumberProvider.constant(1),
						wingSpeed, wingLife,
						sqrtDecay,
						NumberProvider.constant(0),
						new NumberProviders.RandomRange(-5, 5),
						PatternType.AIMED,
						OriginConfig.caster(),
						new AimMode.AimModes.Target(),
						Optional.empty(), Optional.empty(), Optional.empty(),
						Optional.empty(), 1, tiltPos
				)
		));
		// 反翼发射 (s=-1): angle 取反 + tiltAngle 取反 → 完美对称
		var fireLeft = new SpellActions.RepeatAction(NumberProvider.constant(6), "i", List.of(
				new FireDanmakuAction(
						YHDanmaku.Bullet.BALL, ColorProvider.constant(DyeColor.LIME),
						NumberProvider.constant(1),
						wingSpeed, wingLife,
						sqrtDecayNeg,
						NumberProvider.constant(0),
						new NumberProviders.RandomRange(-5, 5),
						PatternType.AIMED,
						OriginConfig.caster(),
						new AimMode.AimModes.Target(),
						Optional.empty(), Optional.empty(), Optional.empty(),
						Optional.empty(), 1, tiltNeg
				)
		));

		// 合并为单个 BurstAction: 两翼通过 tiltAngle=$ver 共享倾斜平面
		var wingPair = new BurstAction(20, 1, "wave", List.of(
				fireRight,
				fireLeft
		));

		// $ver 每对翅膀触发时随机一次，根据目标状态调整范围:
		//   目标在地面: rand(-5, 5)   — 翅膀几乎水平展开
		//   其他:       rand(-45, 45) — 默认倾斜范围
		var setVerGround = new SpellActions.SetVariable("ver", new NumberProviders.RandomRange(-5, 5));
		var setVerDefault = new SpellActions.SetVariable("ver", new NumberProviders.RandomRange(-45, 45));
		var setVer = new SpellActions.ConditionalAction(
				new SpellConditions.TargetOnGround(),
				List.of(setVerGround),
				List.of(setVerDefault)
		);

		// --- Bubble 弹幕 ---
		// 5 发 BUBBLE LIME, 速度 0.65+i*0.1, 生命 = rand(40,80) / vel
		// 原始: rotateDegrees(rand*3, rand*3) → 水平 ±3°, 仰角 ±3°
		// RANDOM 模式: spread=6 → 水平 ±3°, elevation=6 → 仰角 ±3°
		var bubbleSpeed = new NumberProviders.Add(NumberProvider.constant(0.65),
				new NumberProviders.Mul(new NumberProviders.Variable("i"), NumberProvider.constant(0.1)));
		var bubbleLife = new NumberProviders.Div(new NumberProviders.RandomRange(40, 80), bubbleSpeed);
		var bubbleFire = new SpellActions.RepeatAction(NumberProvider.constant(5), "i", List.of(
				new FireDanmakuAction(
						YHDanmaku.Bullet.BUBBLE, ColorProvider.constant(DyeColor.LIME),
						NumberProvider.constant(1),
						bubbleSpeed,
						bubbleLife,
						NumberProvider.constant(0),
						NumberProvider.constant(6),
						NumberProvider.constant(6),
						PatternType.RANDOM,
						OriginConfig.caster(),
						new AimMode.AimModes.Target(),
						Optional.empty(), Optional.empty(), Optional.empty(),
						Optional.empty(), 1
				)
		));

		// --- 组装 onTick ---
		// 步骤 0,1,2 (tick%100 == 0,10,20): Wings
		// 步骤 5,6,7,8 (tick%100 == 50,60,70,80): Bubble
		List<SpellAction> tickActions = new ArrayList<>();
		for (int step = 0; step <= 2; step++) {
			tickActions.add(new SpellActions.ConditionalAction(
					new SpellConditions.TickInterval(100, step * 10),
					List.of(setVer, wingPair),
					List.of()
			));
		}
		for (int step = 5; step <= 8; step++) {
			tickActions.add(new SpellActions.ConditionalAction(
					new SpellConditions.TickInterval(100, step * 10),
					List.of(bubbleFire),
					List.of()
			));
		}

		var phase = new PhaseDefinition(mainPhase, List.of(), tickActions, List.of(), List.of(), List.of());
		return buildDefinition(id, mainPhase, phase, "touhou_little_maid:eternity_larva");
	}

	// ============================
	// SanaeSpell — 近距離: 回転星型激光 / 遠距離: 五穀爆裂弾
	// ============================
	// Legacy: 189行, 2モード切替 (groundTime<40 + distance<35)
	//
	// near() 奇迹「客星辉煌之夜」:
	//   每10tick: 1发 CIRCLE RED 追踪弹
	//   每40tick: 生成2个RotatingStar (左右±45°偏移8格, Y=目标Y)
	//     每个Star: 40tick内每tick发射PENCIL激光, 角度=9°*tick+start
	//     两星同向旋转, start=0/180 (180°相位差)
	//     setupTime(1,10,40,1), delayedMover(4.5, 1)
	//     dir投影到水平面
	//
	// far() 神德「五谷丰穰米之浴」:
	//   每20tick → ExplosiveGrains Ticker (5子波, tick 0,2,4,6,8):
	//     o0 = getOrientation(dir).asNormal() → 垂直环
	//     5个发射点: p0 = pos + o0.rotateDegrees(i*72).scale(12) → 垂直环上12格偏移
	//     每点 → d0 = (target-p0).normalize(), o1 = getOrientation(d0).asNormal()
	//     5发 CIRCLE RED: o1.rotateDegrees(j*72+i*18, 72) → 锥形72°仰角, 方位角间隔72°
	//     speed = max(1, dist/30) + targetVel*1.5
	//     1发追踪弹: d0.scale(speed) 从p0发射
	//     onExpiry: ExplodeTrail(3, j) → 3发BALL, color=COLORS[j], nextGaussian球面分布
	public static SpellDefinition sanae() {
		var id = rl("kochiya_sanae");
		var mainPhase = rl("kochiya_sanae/main");

		// === NEAR MODE: 奇迹「客星辉煌之夜」===

		// 基础追踪弹: 每10tick, 1发CIRCLE RED, 速度0.6
		var nearBasicShot = new SpellActions.ConditionalAction(
				new SpellConditions.TickInterval(10, 0),
				List.of(new FireDanmakuAction(
						YHDanmaku.Bullet.CIRCLE, ColorProvider.constant(DyeColor.RED),
						NumberProvider.constant(1),
						NumberProvider.constant(0.6),
						NumberProvider.constant(80),
						NumberProvider.constant(0),
						NumberProvider.constant(0),
						NumberProvider.constant(0),
						PatternType.AIMED,
						OriginConfig.caster(),
						new AimMode.AimModes.Target(),
						Optional.empty(), Optional.empty(), Optional.empty(),
						Optional.empty(), 1
				)),
				List.of()
		);

		// 旋转星型激光: 每40tick, 2个卫星点
		// Legacy: pos = center + forward.horizontal投影的±45°偏移 × 8格, Y=目标Y
		// 每卫星: BurstAction(40波,1tick间隔) fire_laser
		//   角度 = 9°*$lt + start, 两星同向旋转
		//   右星 start=180 (i=+1 → (1+1)*90=180), 左星 start=0 (i=-1 → (-1+1)*90=0)
		// Origin: CASTER_FACING, offsetX=±sin(45°)*8≈5.66, offsetZ=cos(45°)*8≈5.66
		// (近似: 左星偏移 side=-5.66 forward=5.66, 右星偏移 side=+5.66 forward=5.66)
		// CasterFacing AimMode → 投影到水平面 (legacy: dir.multiply(1,0,1).normalize())
		// PENCIL激光, setupTime(1,10,40,1), delayedMover(v0=4.5, v1=1)

		double starOffset = 8 * Math.sin(Math.toRadians(45)); // ≈5.66

		var nearLaserRight = new BurstAction(40, 1, "lt", List.of(
				new FireLaserAction(
						YHDanmaku.Laser.PENCIL, DyeColor.LIGHT_BLUE,
						NumberProvider.constant(40),
						NumberProvider.constant(3),
						// 角度 = 9° * $lt + 180° (右星)
						new NumberProviders.Add(
								new NumberProviders.Mul(new NumberProviders.Variable("lt"), NumberProvider.constant(9)),
								NumberProvider.constant(180)),
						new AimMode.AimModes.CasterFacing(), // 水平投影方向
						new OriginConfig(OriginConfig.OriginMode.CASTER_FACING,
								NumberProvider.constant(starOffset), NumberProvider.constant(0),
								NumberProvider.constant(starOffset), NumberProvider.constant(0)),
						Optional.empty(),
						1, 10, 1,
						Optional.of(0.5), Optional.of(0.5)
				)
		));
		var nearLaserLeft = new BurstAction(40, 1, "lt", List.of(
				new FireLaserAction(
						YHDanmaku.Laser.PENCIL, DyeColor.LIGHT_BLUE,
						NumberProvider.constant(40),
						NumberProvider.constant(3),
						// 角度 = 9° * $lt + 0° (左星)
						new NumberProviders.Add(
								new NumberProviders.Mul(new NumberProviders.Variable("lt"), NumberProvider.constant(9)),
								NumberProvider.constant(0)),
						new AimMode.AimModes.CasterFacing(),
						new OriginConfig(OriginConfig.OriginMode.CASTER_FACING,
								NumberProvider.constant(-starOffset), NumberProvider.constant(0),
								NumberProvider.constant(starOffset), NumberProvider.constant(0)),
						Optional.empty(),
						1, 10, 1,
						Optional.of(0.5), Optional.of(0.5)
				)
		));

		var nearLasers = new SpellActions.ConditionalAction(
				new SpellConditions.TickInterval(40, 0),
				List.of(nearLaserRight, nearLaserLeft),
				List.of()
		);

		// === FAR MODE: 神德「五谷丰���米之浴」===
		// 用 RepeatAction(5, "gi") 循环5个发射点
		// 每个发射点位于垂直环上: 角度 = gi*72°
		// Legacy: o0 = getOrientation(dir).asNormal() → 垂直环
		//   p0 = pos + o0.rotateDegrees(i*72).scale(12)
		// CASTER_FACING近似: offsetY = cos(gi*72)*12, offsetX = sin(gi*72)*12
		// (asNormal旋转在side-up平面, 与CASTER_FACING的side-up偏移等价)

		// 内弹幕: 5发锥形, 72°恒定仰角, 方位角 = j*72+gi*18
		// Legacy: d0 = (target-p0).normalize(), o1 = getOrientation(d0).asNormal()
		//   o1.rotateDegrees(j*72 + i*18, 72) → 72°仰角锥面上均匀5点
		// 数据驱动: 从发射点朝向目标(DirectionToTarget), RING(5发), elevation=72°固定
		//   tiltAngle用于倾斜内环 → 不需要, 因为elevation=72°是rotateDegrees第二参数
		//   但RING pattern只有水平角! 需要elevation≠0来实现锥面.
		//   在 FireDanmakuAction.execute() 中: 当pattern=RING时, a += (360/n)*i, v=elevDeg
		//   所以 RING + elevation=72 → 5发在水平方向展开, 全部仰角72° → 这就是锥形!
		//   再加上 angleOffset = $gi*18 实现per-point错开

		// 速度: max(1, distance/30) + targetVelocity*1.5
		// 近似: distance/30 (Distance NumberProvider), 下限1.0
		// 暂用 max(1, dist/30) 近似 (无max函数, 用条件或固定值)
		// 简化: dist/20 保证中远距离效果, 近距离稍快但可接受
		var farSpeed = new NumberProviders.Add(
				NumberProvider.constant(0.5),
				new NumberProviders.Div(new NumberProviders.Distance(), NumberProvider.constant(30)));

		var farColors = List.of(DyeColor.LIGHT_BLUE, DyeColor.CYAN, DyeColor.LIME, DyeColor.YELLOW, DyeColor.LIGHT_GRAY);

		// onExpiry: 3发 BALL, SPHERE_RANDOM分布, 颜色=COLORS[$gi] (per-group确定性颜色)
		// 用 ColorProvider.ByVariable("gi", farColors) 实现 — 如果存在
		// 否则用 RandomChoice 近似 (缺陷但可接受)
		var explodeTrail = new FireDanmakuAction(
				YHDanmaku.Bullet.BALL,
				new ColorProvider.RandomChoice(farColors),
				NumberProvider.constant(3),
				NumberProvider.constant(0.7),
				new NumberProviders.RandomRange(60, 100),
				NumberProvider.constant(0),
				NumberProvider.constant(360),
				NumberProvider.constant(180),
				PatternType.SPHERE_RANDOM,
				OriginConfig.caster(), // onExpiry context: caster() = 弹幕死亡位置
				new AimMode.AimModes.RandomAngle(NumberProvider.constant(360)),
				Optional.empty(), Optional.empty(), Optional.empty(),
				Optional.empty(), 1
		);

		// 每个发射点的弹幕 (在 RepeatAction 内部)
		// 5发 CONE pattern, 以 d0(发射点→目标) 为轴心的锥面, elevation=72°
		// Legacy: o1 = getOrientation(d0).asNormal(), o1.rotateDegrees(j*72+i*18, 72)
		//   asNormal交换forward/normal → sin(72°)=0.951沿d0, cos(72°)=0.309展开
		//   即以d0为轴心、半角=acos(sin72)≈18°的锥面
		// CONE pattern: forward*sin(elev) + radial*cos(elev)
		//   elevation=72 → sin(72)≈0.95沿d0, 与legacy完全一致
		// 发射点 origin: 垂直环上12格偏移
		// offsetX = sin($gi*72°)*12, offsetY = cos($gi*72°)*12
		var emissionOrigin = new OriginConfig(OriginConfig.OriginMode.CASTER_FACING,
				new NumberProviders.Mul(
						new NumberProviders.Sin(
								new NumberProviders.Mul(new NumberProviders.Variable("gi"), NumberProvider.constant(72)),
								1, 0),
						NumberProvider.constant(12)),
				new NumberProviders.Mul(
						new NumberProviders.Cos(
								new NumberProviders.Mul(new NumberProviders.Variable("gi"), NumberProvider.constant(72)),
								1, 0),
						NumberProvider.constant(12)),
				NumberProvider.constant(0),
				NumberProvider.constant(0));

		var grainAngleOffset = new NumberProviders.Mul(
				new NumberProviders.Variable("gi"), NumberProvider.constant(18));
		var innerGrains = new FireDanmakuAction(
				YHDanmaku.Bullet.CIRCLE, ColorProvider.constant(DyeColor.RED),
				NumberProvider.constant(5),
				farSpeed,
				NumberProvider.constant(25),
				grainAngleOffset,       // per-point方位角错开
				NumberProvider.constant(360),
				NumberProvider.constant(72),  // cone angle: sin(72°)≈0.95 沿 d0 轴
				PatternType.CONE,             // 以 forward(=d0) 为轴心的锥面
				emissionOrigin,
				new AimMode.AimModes.DirectionToTarget(), // 从发射点朝向目标 (originPos-aware)
				Optional.empty(), Optional.empty(),
				Optional.of(List.of((SpellAction) explodeTrail)),
				Optional.empty(), 1
		);

		// 每个发射点的追踪弹 (1发, 同一origin, 直线朝目标)
		var homingBullet = new FireDanmakuAction(
				YHDanmaku.Bullet.CIRCLE, ColorProvider.constant(DyeColor.RED),
				NumberProvider.constant(1),
				farSpeed,
				NumberProvider.constant(60),
				NumberProvider.constant(0),
				NumberProvider.constant(0),
				NumberProvider.constant(0),
				PatternType.AIMED,
				emissionOrigin,
				new AimMode.AimModes.DirectionToTarget(),
				Optional.empty(), Optional.empty(), Optional.empty(),
				Optional.empty(), 1
		);

		// 5个发射点循环
		var grainLoop = new SpellActions.RepeatAction(NumberProvider.constant(5), "gi", List.of(
				innerGrains, homingBullet
		));

		// 5子波展开: BurstAction(5波, 间隔2tick)
		// Legacy: tick 0,2,4,6,8 各发射一组
		var farBurst = new BurstAction(5, 2, "gw", List.of(grainLoop));

		var farAction = new SpellActions.ConditionalAction(
				new SpellConditions.TickInterval(20, 0),
				List.of(farBurst),
				List.of()
		);

		// === 模式切换 ===
		// near: 目标在地面 且 距离 < 35
		// far: 其他情况
		var nearMode = new SpellActions.ConditionalAction(
				new SpellConditions.AndCondition(List.of(
						new SpellConditions.TargetOnGround(),
						new SpellConditions.DistanceBelow(35)
				)),
				List.of(nearBasicShot, nearLasers),
				List.of(farAction)
		);

		var phase = new PhaseDefinition(mainPhase, List.of(), List.of(nearMode), List.of(), List.of(), List.of());
		return buildDefinition(id, mainPhase, phase, "touhou_little_maid:kochiya_sanae");
	}

	// ============================
	// ClownSpell — 小丑弹幕 (激光+扩散弹)  [完全解耦版]
	// ============================
	// 所有参数均通过 NumberProvider/Condition 表达, 无 Java 循环或硬编码常量.
	// 使用 DynamicTickInterval, CompareNumbers, Conditional NumberProvider, RepeatAction
	// 实现单一 action tree 同时覆盖 normal/lunatic 模式.
	public static SpellDefinition clown() {
		var id = rl("clownpiece");
		var mainPhase = rl("clownpiece/main");

		// === 变量定义 ===
		var dur = new NumberProviders.Conditional(
				new SpellConditions.EntityTrait("is_lunatic"),
				NumberProvider.constant(30), NumberProvider.constant(60));
		var cycle = new NumberProviders.Mul(dur, NumberProvider.constant(2));
		var initActions = List.<SpellAction>of(
				new SpellActions.SetVariable("dur", dur),
				new SpellActions.SetVariable("cycle", cycle),
				new SpellActions.SetVariable("kind", NumberProvider.constant(0)),
				new SpellActions.SetVariable("round", NumberProvider.constant(0)));

		// === SpreadTrail onExpiry: fire_laser BLUE + 静止MENTOS ===
		// $lw 通过变量快照在弹幕创建时锁定
		// Legacy: SpreadTrail.init(o.rotateDegrees(forward, ver))
		//   forward = (-45 + tick/20*90) * s
		//   ver = (-15 + tick/20*30) * s
		//   这里 o 是 Laser ticker 的倾斜坐标系, 激光方向在那个平面内偏转
		//   关键: 激光不指向玩家, 而是沿弹幕飞行方向偏转
		//
		// 数据驱动: onExpiry context 中 TrailCardHolder.forward() = 弹幕飞行方向
		//   用 AimMode.Target (= holder.forward()) 获取弹幕飞行方向作为基础
		//   然后 angleOffset/elevation 在该方向的坐标系中偏转
		//   因为弹幕方向已含倾斜信息, 所以激光自然散布在不同平面
		var lwRatio = new NumberProviders.Div(new NumberProviders.Variable("lw"), NumberProvider.constant(20));
		var spreadLaserAngle = new NumberProviders.Mul(
				new NumberProviders.Add(NumberProvider.constant(-45),
						new NumberProviders.Mul(lwRatio, NumberProvider.constant(90))),
				new NumberProviders.Add(
						new NumberProviders.Mul(new NumberProviders.Variable("si"), NumberProvider.constant(-2)),
						NumberProvider.constant(1)));
		var spreadLaserElev = new NumberProviders.Mul(
				new NumberProviders.Add(NumberProvider.constant(-15),
						new NumberProviders.Mul(lwRatio, NumberProvider.constant(30))),
				new NumberProviders.Add(
						new NumberProviders.Mul(new NumberProviders.Variable("si"), NumberProvider.constant(-2)),
						NumberProvider.constant(1)));
		SpellAction spreadTrail = new SpellActions.SequenceAction(List.of(
				new FireLaserAction(
						YHDanmaku.Laser.LASER, DyeColor.BLUE,
						NumberProvider.constant(60),
						NumberProvider.constant(60),
						spreadLaserAngle,
						spreadLaserElev,
						new AimMode.AimModes.Target(), // = TrailCardHolder.forward() = 弹幕飞行方向
						OriginConfig.caster(), // in onExpiry context: caster() = bullet death position
						Optional.empty(),
						10, 10, 10,
						Optional.empty(), Optional.empty()
				),
				new FireDanmakuAction(
						YHDanmaku.Bullet.MENTOS, ColorProvider.constant(DyeColor.BLUE),
						NumberProvider.constant(1),
						NumberProvider.constant(0),
						NumberProvider.constant(80),
						NumberProvider.constant(0), NumberProvider.constant(0), NumberProvider.constant(0),
						PatternType.AIMED, OriginConfig.caster(),
						new AimMode.AimModes.Target(),
						Optional.empty(), Optional.empty(), Optional.empty(),
						Optional.empty(), 1
				)
		));

		// === HomingTrail onExpiry: fire_laser RED 朝向玩家 + 静止MENTOS ===
		SpellAction homingTrail = new SpellActions.SequenceAction(List.of(
				new FireLaserAction(
						YHDanmaku.Laser.LASER, DyeColor.RED,
						NumberProvider.constant(60),
						NumberProvider.constant(60),
						NumberProvider.constant(0),
						new AimMode.AimModes.DirectionToTarget(), // 从弹幕死亡位置指向玩家
						OriginConfig.caster(),
						Optional.empty(),
						10, 10, 10
				),
				new FireDanmakuAction(
						YHDanmaku.Bullet.MENTOS, ColorProvider.constant(DyeColor.RED),
						NumberProvider.constant(1),
						NumberProvider.constant(0),
						NumberProvider.constant(80),
						NumberProvider.constant(0), NumberProvider.constant(0), NumberProvider.constant(0),
						PatternType.AIMED, OriginConfig.caster(),
						new AimMode.AimModes.DirectionToTarget(),
						Optional.empty(), Optional.empty(), Optional.empty(),
						Optional.empty(), 1
				)
		));

		// === 旋转弹幕公式 ===
		// angle = (45 + $lw/20*180) * $sign, $sign = 1-2*$si (si=0→+1, si=1→-1)
		var signFromSi = new NumberProviders.Add(
				new NumberProviders.Mul(new NumberProviders.Variable("si"), NumberProvider.constant(-2)),
				NumberProvider.constant(1));
		var angleFormula = new NumberProviders.Mul(
				new NumberProviders.Add(NumberProvider.constant(45),
						new NumberProviders.Mul(lwRatio, NumberProvider.constant(180))),
				signFromSi);
		var laserBulletLife = new NumberProviders.Add(NumberProvider.constant(5),
				new NumberProviders.Mul(new NumberProviders.Variable("lw"), NumberProvider.constant(2)));

		// === kind=0: RepeatAction(3,"pair") × DelayAction($pair*10) × BurstAction(20) × RepeatAction(2,"si") ===
		// tilt = ($base_tilt + $pair*30) * $sign
		var tiltFormula = new NumberProviders.Mul(
				new NumberProviders.Add(new NumberProviders.Variable("base_tilt"),
						new NumberProviders.Mul(new NumberProviders.Variable("pair"), NumberProvider.constant(30))),
				signFromSi);
		var k0InnerBurst = new BurstAction(20, 1, "lw", List.of(
				new SpellActions.RepeatAction(NumberProvider.constant(2), "si", List.of(
						new FireDanmakuAction(YHDanmaku.Bullet.MENTOS, ColorProvider.constant(DyeColor.BLUE),
								NumberProvider.constant(1), NumberProvider.constant(0.5), laserBulletLife,
								angleFormula, NumberProvider.constant(0), NumberProvider.constant(0),
								PatternType.AIMED, OriginConfig.caster(), new AimMode.AimModes.Target(),
								Optional.empty(), Optional.empty(),
								Optional.of(List.of(spreadTrail)), Optional.empty(), 1,
								Optional.of(tiltFormula))))));
		var k0LaserPairs = new SpellActions.SequenceAction(List.of(
				new SpellActions.SetVariable("base_tilt", new NumberProviders.RandomRange(0, 20)),
				new SpellActions.RepeatAction(NumberProvider.constant(3), "pair", List.of(
						new DelayAction(
								new NumberProviders.Mul(new NumberProviders.Variable("pair"), NumberProvider.constant(10)),
								List.of(k0InnerBurst))))));

		// === kind=1: 1对, 随机大倾斜, SetVariable保证对称 ===
		var k1TiltFormula = new NumberProviders.Mul(new NumberProviders.Variable("k1t"), signFromSi);
		var k1InnerBurst = new BurstAction(20, 1, "lw", List.of(
				new SpellActions.RepeatAction(NumberProvider.constant(2), "si", List.of(
						new FireDanmakuAction(YHDanmaku.Bullet.MENTOS, ColorProvider.constant(DyeColor.RED),
								NumberProvider.constant(1), NumberProvider.constant(0.5), laserBulletLife,
								angleFormula, NumberProvider.constant(0), NumberProvider.constant(0),
								PatternType.AIMED, OriginConfig.caster(), new AimMode.AimModes.Target(),
								Optional.empty(), Optional.empty(),
								Optional.of(List.of(homingTrail)), Optional.empty(), 1,
								Optional.of(k1TiltFormula))))));
		var laserBurstK1 = new SpellActions.SequenceAction(List.of(
				new SpellActions.SetVariable("k1t", new NumberProviders.RandomRange(-60, 60)),
				k1InnerBurst));
		// === Spread弹幕: BurstAction(10波) × RepeatAction(5层) × 3发 ===
		// w = 9 * (2*($round%2) - 1) → 交替 +9/-9
		var spreadW = new NumberProviders.Mul(NumberProvider.constant(9),
				new NumberProviders.Add(
						new NumberProviders.Mul(NumberProvider.constant(2),
								new NumberProviders.Mod(new NumberProviders.Variable("round"), NumberProvider.constant(2))),
						NumberProvider.constant(-1)));
		var spreadHorAngle = new NumberProviders.Mul(
				new NumberProviders.Add(new NumberProviders.Variable("st"), NumberProvider.constant(-5)), spreadW);
		var spreadElevation = new NumberProviders.Add(
				new NumberProviders.Mul(new NumberProviders.Variable("sj"), NumberProvider.constant(15)),
				NumberProvider.constant(-30));
		// kind=0 → RED STAR, kind=1 → BLUE SPARK (conditional action branch)
		var spreadInner = new SpellActions.RepeatAction(NumberProvider.constant(5), "sj", List.of(
				new SpellActions.ConditionalAction(
						new SpellConditions.VariableCheck("kind", "==", 0),
						List.of(new FireDanmakuAction(YHDanmaku.Bullet.STAR, ColorProvider.constant(DyeColor.RED),
								NumberProvider.constant(3), NumberProvider.constant(0.8),
								new NumberProviders.RandomRange(50, 75), spreadHorAngle,
								NumberProvider.constant(6),
								new NumberProviders.Add(spreadElevation, new NumberProviders.RandomRange(-3, 3)),
								PatternType.RANDOM, OriginConfig.caster(), new AimMode.AimModes.Target(),
								Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1)),
						List.of(new FireDanmakuAction(YHDanmaku.Bullet.SPARK, ColorProvider.constant(DyeColor.BLUE),
								NumberProvider.constant(3), NumberProvider.constant(0.8),
								new NumberProviders.RandomRange(50, 75), spreadHorAngle,
								NumberProvider.constant(6),
								new NumberProviders.Add(spreadElevation, new NumberProviders.RandomRange(-3, 3)),
								PatternType.RANDOM, OriginConfig.caster(), new AimMode.AimModes.Target(),
								Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1))
				)));
		var spreadBurst = new SpellActions.SequenceAction(List.of(
				new SpellActions.AddVariable("round", 1),
				new BurstAction(10, 1, "st", List.of(spreadInner))));

		// === onTick: 统一 action tree (normal + lunatic 通过 $dur/$cycle 自动切换) ===
		List<SpellAction> tickActions = new ArrayList<>();
		tickActions.add(new SpellActions.SetVariable("dur", dur));
		tickActions.add(new SpellActions.SetVariable("cycle", cycle));

		// 激光触发: phaseTick % $cycle == 0 → kind0, phaseTick % $cycle == $dur → kind1
		tickActions.add(new SpellActions.ConditionalAction(
				new SpellConditions.DynamicTickInterval(new NumberProviders.Variable("cycle"), NumberProvider.constant(0)),
				List.of(new SpellActions.SetVariable("kind", NumberProvider.constant(0)), k0LaserPairs),
				List.of()));
		tickActions.add(new SpellActions.ConditionalAction(
				new SpellConditions.DynamicTickInterval(new NumberProviders.Variable("cycle"), new NumberProviders.Variable("dur")),
				List.of(new SpellActions.SetVariable("kind", NumberProvider.constant(1)), laserBurstK1),
				List.of()));

		// Spread触发: 每10tick, 且 phaseTick%$dur < $dur*2/3 (前4/6步)
		tickActions.add(new SpellActions.ConditionalAction(
				new SpellConditions.AndCondition(List.of(
						new SpellConditions.TickInterval(10, 0),
						new SpellConditions.CompareNumbers(
								new NumberProviders.Mod(new NumberProviders.PhaseTick(), new NumberProviders.Variable("dur")),
								"<",
								new NumberProviders.Mul(new NumberProviders.Variable("dur"), NumberProvider.constant(2.0 / 3.0)))
				)),
				List.of(spreadBurst),
				List.of()));

		var phase = new PhaseDefinition(mainPhase, initActions, tickActions, List.of(), List.of(), List.of());
		return buildDefinition(id, mainPhase, phase, "touhou_little_maid:clownpiece");
	}

	// ============================
	// Helper methods
	// ============================

	private static FireDanmakuAction fireDanmakuRing(YHDanmaku.Bullet bullet, DyeColor color,
													  int count, double speed, int lifetime) {
		return new FireDanmakuAction(
				bullet, ColorProvider.constant(color),
				NumberProvider.constant(count), NumberProvider.constant(speed),
				NumberProvider.constant(lifetime), NumberProvider.constant(0),
				NumberProvider.constant(360), NumberProvider.constant(0),
				PatternType.RING,
				OriginConfig.caster(),
				new AimMode.AimModes.RandomAngle(NumberProvider.constant(360)),
				Optional.empty(), Optional.empty(), Optional.empty(),
				Optional.empty(), 1
		);
	}

	// ============================
	// SakuyaSpell — 十六夜咲夜 (飞刀弹幕, 3阶段)
	// ============================
	// Stage 0 (100%-67%): KnifeRing (3层旋转飞刀环) + SpiralKnife (螺旋弹幕)
	// Stage 1 (67%-33%): TimeStopKnife (扩张→冻结→追踪) + KnifeSweep (扇形扫射) + SpiralKnife
	// Stage 2 (33%-0%): TimeStopKnife(强化) + 双SpiralKnife + KnifeStorm (暴风) + CrossLaser (十字激光)
	public static SpellDefinition sakuya() {
		var id = rl("izayoi_sakuya");
		var phase0id = rl("izayoi_sakuya/stage0");
		var phase1id = rl("izayoi_sakuya/stage1");
		var phase2id = rl("izayoi_sakuya/stage2");

		// === Shared: rotationOffset += 3 每tick ===
		SpellAction rotIncrement = new SpellActions.AddVariable("rot", 3);

		// === KnifeRing: 多层旋转飞刀环 ===
		// 3层: GRAY/KNIFE(1.0x), LIGHT_GRAY/KUNAI(0.75x), WHITE/KNIFE(0.5x)
		// 每波: count发, 角度 = 360/count*i + rot + tick*4 + layerOffset
		// speed = clamp(dist/25, 0.8, 2.5), life = dist*1.5+25
		// 5波, 间隔4tick
		var ringSpeed = new NumberProviders.Add(NumberProvider.constant(0.8),
				new NumberProviders.Div(new NumberProviders.Distance(), NumberProvider.constant(50)));
		var ringLife = new NumberProviders.Add(
				new NumberProviders.Mul(new NumberProviders.Distance(), NumberProvider.constant(1.5)),
				NumberProvider.constant(25));
		var ringAngle = new NumberProviders.Add(new NumberProviders.Variable("rot"),
				new NumberProviders.Mul(new NumberProviders.PhaseTick(), NumberProvider.constant(4)));

		// 层0: GRAY KNIFE, speedMod=1.0
		var ringLayer0 = new FireDanmakuAction(
				YHDanmaku.Bullet.KNIFE, ColorProvider.constant(DyeColor.GRAY),
				NumberProvider.constant(24), ringSpeed, ringLife,
				ringAngle, NumberProvider.constant(360),
				NumberProvider.constant(0), PatternType.RING,
				OriginConfig.caster(), new AimMode.AimModes.Target(),
				Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1);
		// 层1: LIGHT_GRAY KUNAI, speedMod=0.75, offset=5°
		var ringLayer1 = new FireDanmakuAction(
				YHDanmaku.Bullet.KUNAI, ColorProvider.constant(DyeColor.LIGHT_GRAY),
				NumberProvider.constant(24),
				new NumberProviders.Mul(ringSpeed, NumberProvider.constant(0.75)),
				new NumberProviders.Div(ringLife, NumberProvider.constant(0.75)),
				new NumberProviders.Add(ringAngle, NumberProvider.constant(5)),
				NumberProvider.constant(360), NumberProvider.constant(0), PatternType.RING,
				OriginConfig.caster(), new AimMode.AimModes.Target(),
				Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1);
		// 层2: WHITE KNIFE, speedMod=0.5, offset=10°
		var ringLayer2 = new FireDanmakuAction(
				YHDanmaku.Bullet.KNIFE, ColorProvider.constant(DyeColor.WHITE),
				NumberProvider.constant(24),
				new NumberProviders.Mul(ringSpeed, NumberProvider.constant(0.5)),
				new NumberProviders.Div(ringLife, NumberProvider.constant(0.5)),
				new NumberProviders.Add(ringAngle, NumberProvider.constant(10)),
				NumberProvider.constant(360), NumberProvider.constant(0), PatternType.RING,
				OriginConfig.caster(), new AimMode.AimModes.Target(),
				Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1);
		// KnifeRing: 5波, 4tick间隔, 每波3层
		var knifeRing = new BurstAction(5, 4, "krw", List.of(ringLayer0, ringLayer1, ringLayer2));

		// === TimeStopKnife: 扩张→冻结→追踪 ===
		// Normal: expandTime=15, dist=6, 2层, homing speed=1.6/life=60
		// Intense: expandTime=12, dist=10, 3层, homing speed=2.2/life=50
		// 弹幕以expandSpeed飞出, 减速停住, freezeTime后追踪弹朝玩家
		// onExpiry chain: KNIFE扩张 → 静止CIRCLE标记(freezeTime) → 追踪MENTOS

		// 追踪弹 (HomingKnife equivalent): 从弹幕死亡位置指向目标
		var homingNormal = new FireDanmakuAction(
				YHDanmaku.Bullet.MENTOS, ColorProvider.constant(DyeColor.GRAY),
				NumberProvider.constant(1), NumberProvider.constant(1.6), NumberProvider.constant(60),
				NumberProvider.constant(0), NumberProvider.constant(0), NumberProvider.constant(0),
				PatternType.AIMED, OriginConfig.caster(),
				new AimMode.AimModes.DirectionToTarget(),
				Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1);

		// 冻结标记 (DelayedHomingKnife): 静止标记弹 → onExpiry → 追踪弹
		var freezeMarkerNormal = new FireDanmakuAction(
				YHDanmaku.Bullet.CIRCLE, ColorProvider.constant(DyeColor.WHITE),
				NumberProvider.constant(1), NumberProvider.constant(0), NumberProvider.constant(15),
				NumberProvider.constant(0), NumberProvider.constant(0), NumberProvider.constant(0),
				PatternType.AIMED, OriginConfig.caster(), new AimMode.AimModes.Target(),
				Optional.of(new MoverConfigs.ZeroMoverConfig()), Optional.empty(),
				Optional.of(List.of((SpellAction) homingNormal)),
				Optional.empty(), 1);

		// 扩张飞刀 Normal: 2层×24发, 减速mover
		// decel factor = 0.9/expandTime = 0.06
		var timeStopNormal = new SpellActions.RepeatAction(NumberProvider.constant(2), "tsl", List.of(
				new FireDanmakuAction(
						YHDanmaku.Bullet.KNIFE,
						new ColorProvider.ByVariable("tsl", List.of(DyeColor.RED, DyeColor.GRAY)),
						NumberProvider.constant(24), NumberProvider.constant(0.4), NumberProvider.constant(15),
						new NumberProviders.Add(new NumberProviders.Variable("rot"),
								new NumberProviders.Mul(new NumberProviders.Variable("tsl"), NumberProvider.constant(5))),
						NumberProvider.constant(360), NumberProvider.constant(0),
						PatternType.RING, OriginConfig.caster(), new AimMode.AimModes.Target(),
						Optional.of(new MoverConfigs.DecelerationConfig(0.06)),
						Optional.empty(),
						Optional.of(List.of((SpellAction) freezeMarkerNormal)),
						Optional.empty(), 1)
		));

		// Intense版本: 3层×36发, 更快追踪
		var homingIntense = new FireDanmakuAction(
				YHDanmaku.Bullet.MENTOS, ColorProvider.constant(DyeColor.GRAY),
				NumberProvider.constant(1), NumberProvider.constant(2.2), NumberProvider.constant(50),
				NumberProvider.constant(0), NumberProvider.constant(0), NumberProvider.constant(0),
				PatternType.AIMED, OriginConfig.caster(),
				new AimMode.AimModes.DirectionToTarget(),
				Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1);
		var freezeMarkerIntense = new FireDanmakuAction(
				YHDanmaku.Bullet.CIRCLE, ColorProvider.constant(DyeColor.WHITE),
				NumberProvider.constant(1), NumberProvider.constant(0), NumberProvider.constant(13),
				NumberProvider.constant(0), NumberProvider.constant(0), NumberProvider.constant(0),
				PatternType.AIMED, OriginConfig.caster(), new AimMode.AimModes.Target(),
				Optional.of(new MoverConfigs.ZeroMoverConfig()), Optional.empty(),
				Optional.of(List.of((SpellAction) homingIntense)),
				Optional.empty(), 1);
		var timeStopIntense = new SpellActions.RepeatAction(NumberProvider.constant(3), "tsl", List.of(
				new FireDanmakuAction(
						YHDanmaku.Bullet.KNIFE,
						new ColorProvider.ByVariable("tsl", List.of(DyeColor.RED, DyeColor.GRAY, DyeColor.LIGHT_GRAY)),
						NumberProvider.constant(36), NumberProvider.constant(0.83), NumberProvider.constant(12),
						new NumberProviders.Add(new NumberProviders.Variable("rot"),
								new NumberProviders.Mul(new NumberProviders.Variable("tsl"), NumberProvider.constant(3.3))),
						NumberProvider.constant(360), NumberProvider.constant(0),
						PatternType.RING, OriginConfig.caster(), new AimMode.AimModes.Target(),
						Optional.of(new MoverConfigs.DecelerationConfig(0.075)),
						Optional.empty(),
						Optional.of(List.of((SpellAction) freezeMarkerIntense)),
						Optional.empty(), 1)
		));

		// === SpiralKnife: 持续螺旋弹幕 ===
		// 每tick: knifePerTick发, 角度 = rotDir * $sw * 18 + i * 360/k
		// tilt = sin($sw * 0.12 度) * 25 (正弦振荡)
		// 3种子弹: MENTOS(主), BALL(中速), CIRCLE(慢速, 每2tick一次)
		// duration ticks via BurstAction
		var spiralSpeed = new NumberProviders.Add(NumberProvider.constant(0.7),
				new NumberProviders.Div(new NumberProviders.Distance(), NumberProvider.constant(40)));
		var spiralLife = new NumberProviders.Add(
				new NumberProviders.Mul(new NumberProviders.Distance(), NumberProvider.constant(2)),
				NumberProvider.constant(35));
		// tilt = sin(sw * 6.875°) * 25  (legacy: sin(tick*0.12 rad) → 0.12 rad = 6.875°)
		var spiralTilt = new NumberProviders.Mul(
				new NumberProviders.Sin(
						new NumberProviders.Mul(new NumberProviders.Variable("sw"), NumberProvider.constant(6.875)),
						1, 0),
				NumberProvider.constant(25));

		// 辅助: 创建一个螺旋BurstAction
		// rotDir=+1 或 -1, knifePerTick, duration
		java.util.function.Function<Object[], SpellAction> mkSpiral = args -> {
			int rotDir = (int) args[0];
			int kpt = (int) args[1];
			int dur = (int) args[2];
			var spiralAngle = new NumberProviders.Mul(
					new NumberProviders.Variable("sw"), NumberProvider.constant(rotDir * 18));
			// 主弹: MENTOS GRAY
			var mainBullet = new FireDanmakuAction(
					YHDanmaku.Bullet.MENTOS, ColorProvider.constant(DyeColor.GRAY),
					NumberProvider.constant(kpt), spiralSpeed, spiralLife,
					spiralAngle, NumberProvider.constant(360), spiralTilt,
					PatternType.RING, OriginConfig.caster(), new AimMode.AimModes.Target(),
					Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1);
			// 中弹: BALL LIGHT_GRAY, 半速
			var midBullet = new FireDanmakuAction(
					YHDanmaku.Bullet.BALL, ColorProvider.constant(DyeColor.LIGHT_GRAY),
					NumberProvider.constant(kpt),
					new NumberProviders.Mul(spiralSpeed, NumberProvider.constant(0.55)),
					new NumberProviders.Add(spiralLife, NumberProvider.constant(15)),
					new NumberProviders.Add(spiralAngle, NumberProvider.constant(15)),
					NumberProvider.constant(360),
					new NumberProviders.Mul(spiralTilt, NumberProvider.constant(-0.5)),
					PatternType.RING, OriginConfig.caster(), new AimMode.AimModes.Target(),
					Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1);
			// 慢弹: CIRCLE WHITE, 1/3速, 每2tick (conditional)
			var slowBullet = new SpellActions.ConditionalAction(
					new SpellConditions.TickInterval(2, 0),
					List.of(new FireDanmakuAction(
							YHDanmaku.Bullet.CIRCLE, ColorProvider.constant(DyeColor.WHITE),
							NumberProvider.constant(kpt),
							new NumberProviders.Mul(spiralSpeed, NumberProvider.constant(0.35)),
							new NumberProviders.Add(spiralLife, NumberProvider.constant(25)),
							new NumberProviders.Add(spiralAngle, NumberProvider.constant(180)),
							NumberProvider.constant(360),
							new NumberProviders.Mul(spiralTilt, NumberProvider.constant(-1)),
							PatternType.RING, OriginConfig.caster(), new AimMode.AimModes.Target(),
							Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1)),
					List.of());
			return new BurstAction(dur, 1, "sw", List.of(mainBullet, midBullet, slowBullet));
		};

		// === KnifeSweep: 旋转扫射 ===
		// 20tick, 每tick countPerTick发, 角度线性旋转360°
		// 2种: MENTOS GRAY (快), BALL LIGHT_GRAY (慢)
		var sweepSpeed = new NumberProviders.Add(NumberProvider.constant(0.8),
				new NumberProviders.Div(new NumberProviders.Distance(), NumberProvider.constant(40)));
		var sweepLife = new NumberProviders.Add(
				new NumberProviders.Mul(new NumberProviders.Distance(), NumberProvider.constant(2)),
				NumberProvider.constant(60));
		var sweepAngle = new NumberProviders.Add(new NumberProviders.Variable("rot"),
				new NumberProviders.Mul(new NumberProviders.Variable("swp"), NumberProvider.constant(18))); // 360/20
		var knifeSweep = new BurstAction(20, 1, "swp", List.of(
				new FireDanmakuAction(
						YHDanmaku.Bullet.MENTOS, ColorProvider.constant(DyeColor.GRAY),
						NumberProvider.constant(8), sweepSpeed, sweepLife,
						sweepAngle, NumberProvider.constant(30), NumberProvider.constant(0),
						PatternType.RANDOM, OriginConfig.caster(), new AimMode.AimModes.Target(),
						Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1),
				new FireDanmakuAction(
						YHDanmaku.Bullet.BALL, ColorProvider.constant(DyeColor.LIGHT_GRAY),
						NumberProvider.constant(8),
						new NumberProviders.Mul(sweepSpeed, NumberProvider.constant(0.6)),
						new NumberProviders.Mul(sweepLife, NumberProvider.constant(1.2)),
						sweepAngle, NumberProvider.constant(30), NumberProvider.constant(0),
						PatternType.RANDOM, OriginConfig.caster(), new AimMode.AimModes.Target(),
						Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1)
		));

		// === KnifeStorm: 大量随机飞刀 ===
		// 10tick, 每tick ~10发, 随机方向+随机颜色+随机子弹类型
		var stormSpeed = new NumberProviders.Add(NumberProvider.constant(1.5),
				new NumberProviders.RandomRange(0, 1.0));
		var stormLife = new NumberProviders.Add(
				new NumberProviders.Mul(new NumberProviders.Distance(), NumberProvider.constant(1.5)),
				new NumberProviders.Add(NumberProvider.constant(30), new NumberProviders.RandomRange(0, 20)));
		var knifeStorm = new BurstAction(10, 1, "stm", List.of(
				new FireDanmakuAction(
						YHDanmaku.Bullet.KNIFE,
						new ColorProvider.RandomChoice(List.of(DyeColor.RED, DyeColor.GRAY, DyeColor.LIGHT_GRAY)),
						NumberProvider.constant(10), stormSpeed, stormLife,
						NumberProvider.constant(0), NumberProvider.constant(45), NumberProvider.constant(30),
						PatternType.RANDOM, OriginConfig.caster(), new AimMode.AimModes.Target(),
						Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1)
		));

		// === CrossLaser: 8方向激光 ===
		var crossLaser = new SpellActions.RepeatAction(NumberProvider.constant(8), "cl", List.of(
				new FireLaserAction(
						YHDanmaku.Laser.LASER, DyeColor.LIGHT_BLUE,
						NumberProvider.constant(100), NumberProvider.constant(50),
						new NumberProviders.Add(new NumberProviders.Variable("rot"),
								new NumberProviders.Mul(new NumberProviders.Variable("cl"), NumberProvider.constant(45))),
						new AimMode.AimModes.Target(),
						OriginConfig.caster(), Optional.empty(),
						15, 10, 10)
		));

		// === Phase 0: 100%-67% — KnifeRing 每30tick + SpiralKnife(+1) 每80tick ===
		var p0Tick = List.<SpellAction>of(
				rotIncrement,
				new SpellActions.ConditionalAction(
						new SpellConditions.TickInterval(30, 0),
						List.of(knifeRing),
						List.of()),
				new SpellActions.ConditionalAction(
						new SpellConditions.TickInterval(80, 0),
						List.of(mkSpiral.apply(new Object[]{1, 4, 60})),
						List.of())
		);

		// === Phase 1: 67%-33% — TimeStopNormal 每35tick + Sweep 每40tick + Spiral(±1) 每35tick ===
		var p1Tick = List.<SpellAction>of(
				rotIncrement,
				new SpellActions.ConditionalAction(
						new SpellConditions.TickInterval(35, 0),
						List.of(timeStopNormal,
								mkSpiral.apply(new Object[]{
										1, 3, 50})),   // random ±1 简化为交替
						List.of()),
				new SpellActions.ConditionalAction(
						new SpellConditions.TickInterval(40, 0),
						List.of(knifeSweep),
						List.of())
		);

		// === Phase 2: 33%-0% — TimeStopIntense + 双Spiral + Storm 每60tick + CrossLaser 每100tick ===
		var p2Tick = List.<SpellAction>of(
				rotIncrement,
				new SpellActions.ConditionalAction(
						new SpellConditions.TickInterval(25, 0),
						List.of(timeStopIntense,
								mkSpiral.apply(new Object[]{-1, 5, 50}),
								mkSpiral.apply(new Object[]{1, 5, 50})),
						List.of()),
				new SpellActions.ConditionalAction(
						new SpellConditions.TickInterval(60, 0),
						List.of(knifeStorm),
						List.of()),
				new SpellActions.ConditionalAction(
						new SpellConditions.TickInterval(100, 0),
						List.of(crossLaser),
						List.of())
		);

		// === Phase definitions with transitions ===
		var phase0 = new PhaseDefinition(phase0id, List.of(), p0Tick, List.of(new SpellActions.ClearScreen()), List.of(),
				List.of(new Transition(new SpellConditions.HealthBelow(0.67f), phase1id, TransitionMode.IMMEDIATE)));
		var phase1 = new PhaseDefinition(phase1id, List.of(), p1Tick, List.of(new SpellActions.ClearScreen()), List.of(),
				List.of(new Transition(new SpellConditions.HealthBelow(0.33f), phase2id, TransitionMode.IMMEDIATE)));
		var phase2 = new PhaseDefinition(phase2id, List.of(), p2Tick, List.of(), List.of(), List.of());

		SpellDisplay display = new SpellDisplay(
				id.toLanguageKey("spell") + ".name",
				id.toLanguageKey("spell") + ".desc",
				Optional.empty(),
				Optional.of(new ResourceLocation("touhou_little_maid", "izayoi_sakuya"))
		);
		return new SpellDefinition(id, display, SpellItemForm.NONE,
				phase0id, Map.of(phase0id, phase0, phase1id, phase1, phase2id, phase2),
				DifficultyProfile.DEFAULT);
	}

	// ============================
	// KisinSpell — 鬼神正邪 (3阶段, shooter弾幕+翼激光+延迟追踪)
	// ============================
	public static SpellDefinition kisin() {
		var id = rl("kisin_sagume");
		var p0id = rl("kisin_sagume/stage0");
		var p1id = rl("kisin_sagume/stage1");
		var p2id = rl("kisin_sagume/stage2");

		// === Phase 0 (100%-67%): SummonNear (Shooter持续发射双向弹幕) ===
		var nearShooterBody = List.<SpellAction>of(
				new FireDanmakuAction(YHDanmaku.Bullet.CIRCLE, ColorProvider.constant(DyeColor.YELLOW),
						NumberProvider.constant(1), NumberProvider.constant(0.8), NumberProvider.constant(40),
						NumberProvider.constant(0), NumberProvider.constant(0), NumberProvider.constant(0),
						PatternType.AIMED, OriginConfig.caster(),
						new AimMode.AimModes.CasterFacing(),
						Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1),
				new FireDanmakuAction(YHDanmaku.Bullet.CIRCLE, ColorProvider.constant(DyeColor.ORANGE),
						NumberProvider.constant(1), NumberProvider.constant(0.3), NumberProvider.constant(40),
						NumberProvider.constant(180), NumberProvider.constant(0), NumberProvider.constant(0),
						PatternType.AIMED, OriginConfig.caster(),
						new AimMode.AimModes.CasterFacing(),
						Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1));
		var nearShooter = new SpawnShooterAction(40, 4f, 60,
				new OriginConfig(OriginConfig.OriginMode.CASTER,
						NumberProvider.constant(0),
						new NumberProviders.RandomRange(-30, 30),
						NumberProvider.constant(0),
						new NumberProviders.RandomRange(-30, 30)),
				NumberProvider.constant(0), NumberProvider.constant(0), NumberProvider.constant(0.5),
				Optional.empty(), nearShooterBody);
		var summonNear = new BurstAction(20, 2, "sn", List.of(nearShooter));
		var p0Action = new SpellActions.ConditionalAction(
				new SpellConditions.TickInterval(60, 0), List.of(summonNear), List.of());

		// === Phase 1 (67%-33%): Wing ===
		var wingLasers = new SpellActions.RepeatAction(NumberProvider.constant(3), "wl", List.of(
				new FireLaserAction(
						YHDanmaku.Laser.LASER, DyeColor.YELLOW,
						NumberProvider.constant(80), NumberProvider.constant(80),
						new NumberProviders.Add(
								new NumberProviders.Mul(
										new NumberProviders.Add(new NumberProviders.Variable("wl"), NumberProvider.constant(-1)),
										NumberProvider.constant(30)),
								new NumberProviders.GaussianRandom(0, 5)),
						new NumberProviders.GaussianRandom(0, 5),
						new AimMode.AimModes.DirectionToTarget(),
						new OriginConfig(OriginConfig.OriginMode.CASTER_FACING,
								new NumberProviders.Add(
										new NumberProviders.Mul(new NumberProviders.Variable("wt"), NumberProvider.constant(0.7)),
										new NumberProviders.GaussianRandom(0, 1)),
								new NumberProviders.GaussianRandom(0, 1),
								new NumberProviders.GaussianRandom(0, 1),
								NumberProvider.constant(0)),
						Optional.empty(), 5, 5, 10,
						Optional.empty(), Optional.empty())
		));
		var wing = new BurstAction(40, 1, "wt", List.of(wingLasers));
		var p1Action = new SpellActions.ConditionalAction(
				new SpellConditions.TickInterval(60, 0), List.of(wing), List.of());

		// === Phase 2 (33%-0%): SummonFar (下落Shooter, 每tick放标记弹→延迟追踪) ===
		var farChase = new FireDanmakuAction(YHDanmaku.Bullet.CIRCLE,
				new ColorProvider.RandomChoice(List.of(DyeColor.MAGENTA, DyeColor.BLUE)),
				NumberProvider.constant(1), NumberProvider.constant(1), NumberProvider.constant(40),
				NumberProvider.constant(0), NumberProvider.constant(0), NumberProvider.constant(0),
				PatternType.AIMED, OriginConfig.caster(),
				new AimMode.AimModes.DirectionToTarget(),
				Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1);
		var farMarker = new FireDanmakuAction(YHDanmaku.Bullet.CIRCLE,
				new ColorProvider.RandomChoice(List.of(DyeColor.MAGENTA, DyeColor.BLUE)),
				NumberProvider.constant(1), NumberProvider.constant(0), NumberProvider.constant(40),
				NumberProvider.constant(0), NumberProvider.constant(0), NumberProvider.constant(0),
				PatternType.AIMED, OriginConfig.caster(),
				new AimMode.AimModes.Target(),
				Optional.of(new MoverConfigs.ZeroMoverConfig()), Optional.empty(),
				Optional.of(List.of((SpellAction) farChase)),
				Optional.empty(), 1);
		var farShooter = new SpawnShooterAction(40, 4f, 40,
				new OriginConfig(OriginConfig.OriginMode.ABSOLUTE,
						new NumberProviders.Add(new NumberProviders.TargetX(), new NumberProviders.GaussianRandom(0, 20)),
						new NumberProviders.Add(new NumberProviders.TargetY(), NumberProvider.constant(20)),
						new NumberProviders.Add(new NumberProviders.TargetZ(), new NumberProviders.GaussianRandom(0, 20)),
						NumberProvider.constant(0)),
				NumberProvider.constant(0), NumberProvider.constant(-0.5), NumberProvider.constant(0),
				Optional.empty(), List.of(farMarker));
		var summonFar = new BurstAction(40, 1, "sf", List.of(farShooter));
		var p2Action = new SpellActions.ConditionalAction(
				new SpellConditions.TickInterval(80, 0), List.of(summonFar), List.of());

		// === Phase definitions ===
		var phase0 = new PhaseDefinition(p0id, List.of(), List.of(p0Action),
				List.of(new SpellActions.ClearScreen()), List.of(),
				List.of(new Transition(new SpellConditions.HealthBelow(0.67f), p1id, TransitionMode.IMMEDIATE)));
		var phase1 = new PhaseDefinition(p1id, List.of(), List.of(p1Action),
				List.of(new SpellActions.ClearScreen()), List.of(),
				List.of(new Transition(new SpellConditions.HealthBelow(0.33f), p2id, TransitionMode.IMMEDIATE)));
		var phase2 = new PhaseDefinition(p2id, List.of(), List.of(p2Action),
				List.of(), List.of(), List.of());

		SpellDisplay display = new SpellDisplay(
				id.toLanguageKey("spell") + ".name", id.toLanguageKey("spell") + ".desc",
				Optional.empty(), Optional.of(new ResourceLocation("touhou_little_maid", "kisin_sagume")));
		return new SpellDefinition(id, display, SpellItemForm.NONE,
				p0id, Map.of(p0id, phase0, p1id, phase1, p2id, phase2), DifficultyProfile.DEFAULT);
	}

	// ============================
	// RemiliaSpell — 蕾米莉亚 (5步循环: 3×扫射 + 1×激光 + 1×枪突)
	// ============================
	public static SpellDefinition remilia() {
		var id = rl("remilia_scarlet");
		var mainPhase = rl("remilia_scarlet/main");

		var stepVar = new NumberProviders.Mod(
				new NumberProviders.Div(new NumberProviders.PhaseTick(), NumberProvider.constant(20)),
				NumberProvider.constant(5));

		// === Sweep (step < 3): 旋转锥形扫射 ===
		var sweepSpeed = new NumberProviders.Clamp(
				new NumberProviders.Div(new NumberProviders.Distance(), NumberProvider.constant(20)),
				NumberProvider.constant(1), NumberProvider.constant(3));
		var sweepLife = new NumberProviders.Max(NumberProvider.constant(60),
				new NumberProviders.Mul(new NumberProviders.Distance(), NumberProvider.constant(2)));
		var sweepAngle = new NumberProviders.Add(
				new NumberProviders.Variable("sweep_base"),
				new NumberProviders.Mul(new NumberProviders.Variable("swt"), NumberProvider.constant(18)));
		var sweep = new BurstAction(20, 1, "swt", List.of(
				// BUBBLE: 快层 speed * [0.8, 1.2] (legacy: lowSpeed ~ highSpeed)
				new FireDanmakuAction(YHDanmaku.Bullet.BUBBLE, ColorProvider.constant(DyeColor.RED),
						NumberProvider.constant(12),
						new NumberProviders.Mul(sweepSpeed, new NumberProviders.RandomRange(0.8, 1.2)),
						sweepLife,
						sweepAngle, NumberProvider.constant(15),
						new NumberProviders.GaussianRandom(0, 20),
						PatternType.RANDOM, OriginConfig.caster(), new AimMode.AimModes.Target(),
						Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1),
				// MENTOS: 中层 speed * [0.4, 0.7] (legacy: mid=0.6+0.3*rand of v0)
				new FireDanmakuAction(YHDanmaku.Bullet.MENTOS, ColorProvider.constant(DyeColor.RED),
						NumberProvider.constant(12),
						new NumberProviders.Mul(sweepSpeed, new NumberProviders.RandomRange(0.4, 0.7)),
						new NumberProviders.Mul(sweepLife, NumberProvider.constant(1.2)),
						sweepAngle, NumberProvider.constant(15),
						new NumberProviders.GaussianRandom(0, 20),
						PatternType.RANDOM, OriginConfig.caster(), new AimMode.AimModes.Target(),
						Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1),
				// BALL: 慢层 speed * [0.15, 0.45] (legacy: low=0.3+0.3*rand of v0)
				new FireDanmakuAction(YHDanmaku.Bullet.BALL, ColorProvider.constant(DyeColor.RED),
						NumberProvider.constant(12),
						new NumberProviders.Mul(sweepSpeed, new NumberProviders.RandomRange(0.15, 0.45)),
						new NumberProviders.Mul(sweepLife, NumberProvider.constant(1.5)),
						sweepAngle, NumberProvider.constant(15),
						new NumberProviders.GaussianRandom(0, 20),
						PatternType.RANDOM, OriginConfig.caster(), new AimMode.AimModes.Target(),
						Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1)
		));
		var sweepInit = new SpellActions.SequenceAction(List.of(
				new SpellActions.SetVariable("sweep_base", new NumberProviders.RandomRange(0, 360)),
				sweep));
		var sweepAction = new SpellActions.ConditionalAction(
				new SpellConditions.AndCondition(List.of(
						new SpellConditions.TickInterval(20, 0),
						new SpellConditions.CompareNumbers(stepVar, "<", NumberProvider.constant(3)))),
				List.of(sweepInit), List.of());

		// === Lasers (step == 3): 80组预生成树状激光 (20tick × 4组/tick) ===
		// 每组: 1主(random sphere) + 3分叉(endpoint, 120°@45°) = 4条
		// 方向在Java中预计算, 避免NumberProvider无法做相对方向旋转的限制
		var laserGroups = new ArrayList<SpellAction>();
		for (int g = 0; g < 80; g++) {
			laserGroups.add(buildRemiliaLaserGroup());
		}
		// 每tick发射4组 (laserGroups按顺序消耗, 通过变量$lt索引)
		var laserBurst = new BurstAction(20, 1, "lt", List.<SpellAction>of(
				new SpellActions.SequenceAction(List.of(
						laserGroups.get(0), laserGroups.get(1), laserGroups.get(2), laserGroups.get(3)
				))));
		// 上面只用了4个, 改为: 用BurstAction变量$lt来索引, 但SequenceAction不支持动态索引
		// 简化: 直接把80组打平成20-tick burst, 每tick body含4个SequenceAction
		var perTickActions = new ArrayList<SpellAction>();
		for (int t = 0; t < 20; t++) {
			var tickGroup = new ArrayList<SpellAction>();
			for (int i = 0; i < 4; i++) {
				tickGroup.add(laserGroups.get(t * 4 + i));
			}
			perTickActions.add(new SpellActions.SequenceAction(tickGroup));
		}
		// 用DelayAction逐tick触发
		var laserAllActions = new ArrayList<SpellAction>();
		for (int t = 0; t < 20; t++) {
			if (t == 0) {
				laserAllActions.add(perTickActions.get(0));
			} else {
				laserAllActions.add(new DelayAction(NumberProvider.constant(t), List.of(perTickActions.get(t))));
			}
		}
		var laserAction = new SpellActions.ConditionalAction(
				new SpellConditions.AndCondition(List.of(
						new SpellConditions.TickInterval(20, 0),
						new SpellConditions.CompareNumbers(stepVar, "==", NumberProvider.constant(3)),
						new SpellConditions.TargetIsFallFlying())),
				List.of(new SpellActions.SequenceAction(laserAllActions)), List.of());

		// === 冈格尼尔 (Gungnir) 蓄力系统 ===
		// 每tick: dist>=40 → gung+1, dist<40 → gung-2, clamp 0~100
		var gungCharge = new SpellActions.ConditionalAction(
				new SpellConditions.DistanceAbove(40),
				List.of(new SpellActions.SetVariable("gung",
						new NumberProviders.Clamp(
								new NumberProviders.Add(new NumberProviders.Variable("gung"), NumberProvider.constant(1)),
								NumberProvider.constant(0), NumberProvider.constant(100)))),
				List.of(new SpellActions.SetVariable("gung",
						new NumberProviders.Clamp(
								new NumberProviders.Add(new NumberProviders.Variable("gung"), NumberProvider.constant(-2)),
								NumberProvider.constant(0), NumberProvider.constant(100)))));

		// === 冈格尼尔视觉 ===
		// 共用参数: 数量=gung/3 (最大30发), 长度=gung/100*12 (最大12格)
		// 梭形: sin(t*pi)*0.25 横向抖动
		var gungCount = new NumberProviders.Clamp(
				new NumberProviders.Div(new NumberProviders.Variable("gung"), NumberProvider.constant(3)),
				NumberProvider.constant(1), NumberProvider.constant(30));
		var gungLen = new NumberProviders.Mul(
				new NumberProviders.Div(new NumberProviders.Variable("gung"), NumberProvider.constant(100)),
				NumberProvider.constant(12));
		// t = gi/count (0~1), 梭形宽度 = sin(t*180°) * 0.25
		var shuttleWidth = new NumberProviders.Mul(
				new NumberProviders.Sin(
						new NumberProviders.Mul(
								new NumberProviders.Div(new NumberProviders.Variable("gi"), gungCount),
								NumberProvider.constant(180)), 1, 0),
				NumberProvider.constant(0.25));

		// 阶段A (gung 1~89): 竖直向上, RED, boss头顶2格起向上延伸
		var gungVisualVertical = new SpellActions.ConditionalAction(
				new SpellConditions.AndCondition(List.of(
						new SpellConditions.TickInterval(4, 0),
						new SpellConditions.CompareNumbers(new NumberProviders.Variable("gung"), ">", NumberProvider.constant(0)),
						new SpellConditions.CompareNumbers(new NumberProviders.Variable("gung"), "<", NumberProvider.constant(90)))),
				List.of(new SpellActions.RepeatAction(gungCount, "gi", List.of(
						new FireDanmakuAction(YHDanmaku.Bullet.MENTOS, ColorProvider.constant(DyeColor.RED),
								NumberProvider.constant(1), NumberProvider.constant(0), NumberProvider.constant(8),
								NumberProvider.constant(0), NumberProvider.constant(0), NumberProvider.constant(0),
								PatternType.AIMED,
								new OriginConfig(OriginConfig.OriginMode.CASTER,
										new NumberProviders.Mul(shuttleWidth, new NumberProviders.GaussianRandom(0, 1)),
										new NumberProviders.Add(NumberProvider.constant(2),
												new NumberProviders.Mul(
														new NumberProviders.Div(new NumberProviders.Variable("gi"), gungCount),
														gungLen)),
										new NumberProviders.Mul(shuttleWidth, new NumberProviders.GaussianRandom(0, 1)),
										NumberProvider.constant(0)),
								new AimMode.AimModes.FixedDirection(new Vec3(0, 1, 0)),
								Optional.of(new MoverConfigs.ZeroMoverConfig()),
								Optional.empty(), Optional.empty(), Optional.empty(), 1)
				))), List.of());

		// 阶段B (gung 90~99): 快速转向target, MAGENTA, CASTER_FACING延伸
		// 从头顶2格处沿forward方向延伸, 颜色品红
		var gungVisualAim = new SpellActions.ConditionalAction(
				new SpellConditions.AndCondition(List.of(
						new SpellConditions.TickInterval(4, 0),
						new SpellConditions.CompareNumbers(new NumberProviders.Variable("gung"), ">=", NumberProvider.constant(90)),
						new SpellConditions.CompareNumbers(new NumberProviders.Variable("gung"), "<", NumberProvider.constant(100)))),
				List.of(new SpellActions.RepeatAction(gungCount, "gi", List.of(
						new FireDanmakuAction(YHDanmaku.Bullet.MENTOS, ColorProvider.constant(DyeColor.MAGENTA),
								NumberProvider.constant(1), NumberProvider.constant(0), NumberProvider.constant(8),
								NumberProvider.constant(0), NumberProvider.constant(0), NumberProvider.constant(0),
								PatternType.AIMED,
								new OriginConfig(OriginConfig.OriginMode.CASTER_FACING,
										new NumberProviders.Mul(shuttleWidth, new NumberProviders.GaussianRandom(0, 1)),
										NumberProvider.constant(2),
										new NumberProviders.Mul(
												new NumberProviders.Div(new NumberProviders.Variable("gi"), gungCount),
												gungLen),
										NumberProvider.constant(0)),
								new AimMode.AimModes.DirectionToTarget(),
								Optional.of(new MoverConfigs.ZeroMoverConfig()),
								Optional.empty(), Optional.empty(), Optional.empty(), 1)
				))), List.of());

		// 发射: gung>=100 → teleport到半程 + 发射梭形弹幕柱 + 重置gung
		var spearTrail = new SpellActions.RepeatAction(NumberProvider.constant(80), "si", List.of(
				new FireDanmakuAction(YHDanmaku.Bullet.MENTOS, ColorProvider.constant(DyeColor.RED),
						NumberProvider.constant(1), NumberProvider.constant(3), NumberProvider.constant(30),
						new NumberProviders.GaussianRandom(0, 1), NumberProvider.constant(0),
						new NumberProviders.GaussianRandom(0, 1), PatternType.AIMED,
						new OriginConfig(OriginConfig.OriginMode.CASTER_FACING,
								new NumberProviders.GaussianRandom(0, 0.15),
								new NumberProviders.GaussianRandom(0, 0.15),
								new NumberProviders.Mul(
										new NumberProviders.Div(new NumberProviders.Variable("si"), NumberProvider.constant(80)),
										new NumberProviders.Distance()),
								NumberProvider.constant(0)),
						new AimMode.AimModes.DirectionToTarget(),
						Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1)
		));
		var spearFire = new SpellActions.ConditionalAction(
				new SpellConditions.CompareNumbers(new NumberProviders.Variable("gung"), ">=", NumberProvider.constant(100)),
				List.of(new SpellActions.SequenceAction(List.of(
						new TeleportAction(
								new OriginConfig(OriginConfig.OriginMode.CASTER_FACING,
										NumberProvider.constant(0), NumberProvider.constant(0),
										new NumberProviders.Div(new NumberProviders.Distance(), NumberProvider.constant(2)),
										NumberProvider.constant(0)),
								true),
						spearTrail,
						new SpellActions.SetVariable("gung", new NumberProviders.Constant(0))))),
				List.of());

		var phase = new PhaseDefinition(mainPhase, List.of(),
				List.of(sweepAction, laserAction, gungCharge, gungVisualVertical, gungVisualAim, spearFire),
				List.of(), List.of(), List.of());
		return buildDefinition(id, mainPhase, phase, "touhou_little_maid:remilia_scarlet");
	}

	// ============================
	// DoremiSpell — 梦子 (Maze迷宫+Madness狂乱 状态机)
	// ============================
	public static SpellDefinition doremi() {
		var id = rl("doremy_sweet");
		var mainPhase = rl("doremy_sweet/main");

		// === 状态变量维护 (每tick) ===
		var updateGt = new SpellActions.ConditionalAction(
				new SpellConditions.TargetOnGround(),
				List.of(new SpellActions.SetVariable("gt",
						new NumberProviders.Clamp(
								new NumberProviders.Add(new NumberProviders.Variable("gt"), NumberProvider.constant(1)),
								NumberProvider.constant(0), NumberProvider.constant(20)))),
				List.of(new SpellActions.SetVariable("gt",
						new NumberProviders.Clamp(
								new NumberProviders.Add(new NumberProviders.Variable("gt"), NumberProvider.constant(-1)),
								NumberProvider.constant(0), NumberProvider.constant(20)))));
		SpellAction decCd = new SpellActions.ConditionalAction(
				new SpellConditions.CompareNumbers(new NumberProviders.Variable("cd"), ">", NumberProvider.constant(0)),
				List.of(new SpellActions.AddVariable("cd", -1)), List.of());
		SpellAction decMzt = new SpellActions.ConditionalAction(
				new SpellConditions.CompareNumbers(new NumberProviders.Variable("mzt"), ">", NumberProvider.constant(0)),
				List.of(new SpellActions.AddVariable("mzt", -1)), List.of());
		SpellAction decMdt = new SpellActions.ConditionalAction(
				new SpellConditions.CompareNumbers(new NumberProviders.Variable("mdt"), ">", NumberProvider.constant(0)),
				List.of(new SpellActions.AddVariable("mdt", -1)), List.of());

		// === Maze 激光阵列 ===
		var mazeLasers = new SpellActions.RepeatAction(NumberProvider.constant(2), "ms", List.of(
				new SpellActions.RepeatAction(NumberProvider.constant(8), "mi", List.of(
						new SpellActions.RepeatAction(NumberProvider.constant(12), "mj", List.of(
								new FireLaserAction(YHDanmaku.Laser.LASER, DyeColor.RED,
										NumberProvider.constant(120), NumberProvider.constant(40),
										new NumberProviders.Add(
												new NumberProviders.Mul(new NumberProviders.Variable("mi"), NumberProvider.constant(45)),
												new NumberProviders.Add(
														new NumberProviders.Mul(new NumberProviders.Variable("mj"), NumberProvider.constant(30)),
														new NumberProviders.RandomRange(0, 360))),
										new AimMode.AimModes.FixedDirection(new Vec3(1, 0, 0)),
										new OriginConfig(OriginConfig.OriginMode.ABSOLUTE,
												new NumberProviders.Add(new NumberProviders.Variable("maze_x"),
														new NumberProviders.Mul(
																new NumberProviders.Cos(new NumberProviders.Mul(new NumberProviders.Variable("mi"), NumberProvider.constant(45)), 1, 0),
																NumberProvider.constant(6))),
												new NumberProviders.Add(new NumberProviders.Variable("maze_y"),
														new NumberProviders.Mul(
																new NumberProviders.Add(new NumberProviders.Variable("ms"), NumberProvider.constant(-0.5)),
																NumberProvider.constant(4))),
												new NumberProviders.Add(new NumberProviders.Variable("maze_z"),
														new NumberProviders.Mul(
																new NumberProviders.Sin(new NumberProviders.Mul(new NumberProviders.Variable("mi"), NumberProvider.constant(45)), 1, 0),
																NumberProvider.constant(6))),
												NumberProvider.constant(0)),
										Optional.of(new MoverConfigs.RotateConfig(3)),
										0, 0, 20, Optional.empty(), Optional.empty())
						))
				))
		));

		// === Maze 弹幕螺旋 (环形辐射, 10发/tick) ===
		var mazeSpiral = new BurstAction(80, 1, "mt", List.of(
				new FireDanmakuAction(YHDanmaku.Bullet.BALL,
						new ColorProvider.Cycle(List.of(DyeColor.YELLOW, DyeColor.ORANGE), 1),
						NumberProvider.constant(10), NumberProvider.constant(0.05), NumberProvider.constant(80),
						new NumberProviders.Add(
								new NumberProviders.Variable("maze_init"),
								new NumberProviders.Mul(new NumberProviders.Variable("mt"), NumberProvider.constant(9))),
						NumberProvider.constant(360), NumberProvider.constant(0),
						PatternType.RING,
						new OriginConfig(OriginConfig.OriginMode.ABSOLUTE,
								new NumberProviders.Add(new NumberProviders.Variable("maze_x"),
										new NumberProviders.Mul(
												new NumberProviders.Cos(new NumberProviders.Add(
														new NumberProviders.Variable("maze_init"),
														new NumberProviders.Mul(new NumberProviders.Variable("mt"), NumberProvider.constant(9))), 1, 0),
												NumberProvider.constant(8))),
								new NumberProviders.Variable("maze_y"),
								new NumberProviders.Add(new NumberProviders.Variable("maze_z"),
										new NumberProviders.Mul(
												new NumberProviders.Sin(new NumberProviders.Add(
														new NumberProviders.Variable("maze_init"),
														new NumberProviders.Mul(new NumberProviders.Variable("mt"), NumberProvider.constant(9))), 1, 0),
												NumberProvider.constant(8))),
								NumberProvider.constant(0)),
						new AimMode.AimModes.Target(),
						Optional.of(new MoverConfigs.DecelerationConfig(-0.035)),
						Optional.empty(), Optional.empty(), Optional.empty(), 1)
		));

		// === Maze 触发 ===
		var mazeTrigger = new SpellActions.ConditionalAction(
				new SpellConditions.AndCondition(List.of(
						new SpellConditions.CompareNumbers(new NumberProviders.Variable("cd"), "<=", NumberProvider.constant(100)),
						new SpellConditions.CompareNumbers(new NumberProviders.Variable("mzt"), "<=", NumberProvider.constant(0)),
						new SpellConditions.VariableCheck("gt", "==", 20))),
				List.of(new SpellActions.SequenceAction(List.of(
						new SpellActions.SetVariable("maze_x", new NumberProviders.TargetX()),
						new SpellActions.SetVariable("maze_y", new NumberProviders.Add(new NumberProviders.TargetY(), NumberProvider.constant(-0.3))),
						new SpellActions.SetVariable("maze_z", new NumberProviders.TargetZ()),
						new SpellActions.SetVariable("maze_init", new NumberProviders.RandomRange(0, 360)),
						new SpellActions.SetVariable("mzt", new NumberProviders.Constant(100)),
						new SpellActions.SetVariable("mdt", new NumberProviders.Constant(160)),
						new SpellActions.AddVariable("cd", 180),
						mazeLasers, mazeSpiral))),
				List.of());

		// === Madness 初始化42变量 + 7发射器 ===
		var madInit = new ArrayList<SpellAction>();
		for (int i = 0; i < 7; i++) {
			String s = String.valueOf(i);
			madInit.add(new SpellActions.SetVariable("mst" + s, new NumberProviders.RandomRange(0, 360)));
			madInit.add(new SpellActions.SetVariable("msp" + s, new NumberProviders.RandomRange(2, 4)));
			madInit.add(new SpellActions.SetVariable("mam" + s, new NumberProviders.RandomRange(0, 90)));
			madInit.add(new SpellActions.SetVariable("mfr" + s,
					new NumberProviders.Div(
							new NumberProviders.Add(new NumberProviders.RandomRange(0, 20), NumberProvider.constant(10)),
							new NumberProviders.Max(
									new NumberProviders.Div(new NumberProviders.Variable("mam" + s), NumberProvider.constant(30)),
									NumberProvider.constant(1)))));
			madInit.add(new SpellActions.SetVariable("mro" + s, new NumberProviders.RandomRange(10, 20)));
			madInit.add(new SpellActions.SetVariable("mr0" + s, new NumberProviders.RandomRange(0, 360)));
		}

		var madEmitters = new ArrayList<SpellAction>();
		for (int i = 0; i < 7; i++) {
			madEmitters.add(buildMadnessEmitter(i));
		}
		var madBurst = new BurstAction(100, 1, "mdt_t", madEmitters);

		var madTrigger = new SpellActions.SequenceAction(List.of(
				new SpellActions.SetVariable("do_mad", new NumberProviders.Constant(0)),
				new SpellActions.ConditionalAction(
						new SpellConditions.AndCondition(List.of(
								new SpellConditions.CompareNumbers(new NumberProviders.Variable("mdt"), "<=", NumberProvider.constant(0)),
								new SpellConditions.VariableCheck("gt", "==", 0))),
						List.of(new SpellActions.SetVariable("do_mad", new NumberProviders.Constant(1))), List.of()),
				new SpellActions.ConditionalAction(
						new SpellConditions.AndCondition(List.of(
								new SpellConditions.CompareNumbers(new NumberProviders.Variable("mdt"), "<=", NumberProvider.constant(0)),
								new SpellConditions.CompareNumbers(new NumberProviders.Variable("mzt"), "<=", NumberProvider.constant(0)))),
						List.of(new SpellActions.SetVariable("do_mad", new NumberProviders.Constant(1))), List.of()),
				new SpellActions.ConditionalAction(
						new SpellConditions.VariableCheck("do_mad", "==", 1),
						List.of(new SpellActions.SequenceAction(buildMadnessFullAction(madInit, madBurst))),
						List.of())
		));

		var phase = new PhaseDefinition(mainPhase, List.of(),
				List.of(updateGt, decCd, decMzt, decMdt, mazeTrigger, madTrigger),
				List.of(), List.of(), List.of());
		return buildDefinition(id, mainPhase, phase, "touhou_little_maid:doremy_sweet");
	}

	/** Madness: 构建单个发射器的 per-tick 发射逻辑 */
	private static SpellAction buildMadnessEmitter(int i) {
		String s = String.valueOf(i);
		var angle = new NumberProviders.Add(new NumberProviders.Variable("mst" + s),
				new NumberProviders.Mul(new NumberProviders.Variable("msp" + s), new NumberProviders.Variable("mdt_t")));
		var tilt = new NumberProviders.Mul(new NumberProviders.Variable("mam" + s),
				new NumberProviders.Sin(
						new NumberProviders.Mul(new NumberProviders.Variable("mfr" + s), new NumberProviders.Variable("mdt_t")),
						0.5, 0.5));
		var cosA = new NumberProviders.Cos(angle, 1, 0);
		var sinA = new NumberProviders.Sin(angle, 1, 0);
		var cosT = new NumberProviders.Cos(tilt, 1, 0);
		var sinT = new NumberProviders.Sin(tilt, 1, 0);
		var px = new NumberProviders.Add(new NumberProviders.TargetX(), new NumberProviders.Mul(new NumberProviders.Mul(cosA, cosT), NumberProvider.constant(16)));
		var py = new NumberProviders.Add(new NumberProviders.TargetY(), new NumberProviders.Mul(sinT, NumberProvider.constant(16)));
		var pz = new NumberProviders.Add(new NumberProviders.TargetZ(), new NumberProviders.Mul(new NumberProviders.Mul(sinA, cosT), NumberProvider.constant(16)));
		var ringRot = new NumberProviders.Add(
				new NumberProviders.Mul(new NumberProviders.Variable("mro" + s), new NumberProviders.Variable("mdt_t")),
				new NumberProviders.Variable("mr0" + s));
		var origin = new OriginConfig(OriginConfig.OriginMode.ABSOLUTE, px, py, pz, NumberProvider.constant(0));

		return new SpellActions.SequenceAction(List.of(
				new FireDanmakuAction(YHDanmaku.Bullet.BALL, ColorProvider.constant(DyeColor.BLUE),
						NumberProvider.constant(1), NumberProvider.constant(0.3), NumberProvider.constant(100),
						ringRot, NumberProvider.constant(0), NumberProvider.constant(0),
						PatternType.AIMED, origin, new AimMode.AimModes.DirectionToTarget(),
						Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1),
				new FireDanmakuAction(YHDanmaku.Bullet.BALL, ColorProvider.constant(DyeColor.MAGENTA),
						NumberProvider.constant(1), NumberProvider.constant(0.3), NumberProvider.constant(100),
						new NumberProviders.Add(ringRot, NumberProvider.constant(180)),
						NumberProvider.constant(0), NumberProvider.constant(0),
						PatternType.AIMED, origin, new AimMode.AimModes.DirectionToTarget(),
						Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1)
		));
	}

	/** Madness: 组装完整的初始化+发射 action 列表 */
	private static List<SpellAction> buildMadnessFullAction(List<SpellAction> init, SpellAction burst) {
		var list = new ArrayList<>(init);
		list.add(new SpellActions.SetVariable("mdt", new NumberProviders.Constant(100)));
		list.add(new SpellActions.SetVariable("mzt", new NumberProviders.Constant(160)));
		list.add(burst);
		return list;
	}

	// ============================
	// KoishiSpell — 恋 (Lissajous激光 + 追踪弹 + 边界限制)
	// ============================
	public static SpellDefinition koishi() {
		var id = rl("komeiji_koishi");
		var mainPhase = rl("komeiji_koishi/main");

		// === Lissajous 激光 (每tick 10条) ===
		var tDeg = new NumberProviders.Add(
				new NumberProviders.Mul(new NumberProviders.PhaseTick(), NumberProvider.constant(1.375)),
				new NumberProviders.Mul(new NumberProviders.Variable("li"), NumberProvider.constant(974.03)));
		var cosInner = new NumberProviders.Cos(new NumberProviders.Mul(tDeg, NumberProvider.constant(1.47)), 1, 0);
		var lissX = new NumberProviders.Add(new NumberProviders.CasterX(),
				new NumberProviders.Mul(new NumberProviders.Mul(cosInner, new NumberProviders.Cos(tDeg, 1, 0)), NumberProvider.constant(32)));
		var lissZ = new NumberProviders.Add(new NumberProviders.CasterZ(),
				new NumberProviders.Mul(new NumberProviders.Mul(cosInner, new NumberProviders.Sin(tDeg, 1, 0)), NumberProvider.constant(32)));
		var lissY = new NumberProviders.Min(
				new NumberProviders.Add(new NumberProviders.CasterY(), NumberProvider.constant(-15)),
				new NumberProviders.Add(new NumberProviders.TargetY(), NumberProvider.constant(-10)));
		var laserSwing = new NumberProviders.Cos(
				new NumberProviders.Mul(tDeg, NumberProvider.constant(4)), 18, 0);
		var lissajous = new SpellActions.RepeatAction(NumberProvider.constant(10), "li", List.of(
				new FireLaserAction(YHDanmaku.Laser.LASER, DyeColor.BLUE,
						NumberProvider.constant(40), NumberProvider.constant(60),
						laserSwing,
						NumberProvider.constant(75),
						new AimMode.AimModes.FixedDirection(new Vec3(0, 1, 0)),
						new OriginConfig(OriginConfig.OriginMode.ABSOLUTE, lissX, lissY, lissZ, NumberProvider.constant(0)),
						Optional.empty(), 10, 4, 4, Optional.empty(), Optional.empty())
		));

		// === Homing 弹幕 ===
		var homingChase = new FireDanmakuAction(YHDanmaku.Bullet.MENTOS, ColorProvider.constant(DyeColor.RED),
				NumberProvider.constant(1), NumberProvider.constant(1), NumberProvider.constant(60),
				NumberProvider.constant(0), NumberProvider.constant(0), NumberProvider.constant(0),
				PatternType.AIMED, OriginConfig.caster(),
				new AimMode.AimModes.DirectionToTarget(),
				Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1);
		var homingExpand = new FireDanmakuAction(YHDanmaku.Bullet.MENTOS, ColorProvider.constant(DyeColor.RED),
				NumberProvider.constant(1), NumberProvider.constant(0.4), NumberProvider.constant(20),
				new NumberProviders.Mul(new NumberProviders.PhaseTick(), NumberProvider.constant(9)),
				NumberProvider.constant(0), NumberProvider.constant(0),
				PatternType.AIMED, OriginConfig.caster(), new AimMode.AimModes.Target(),
				Optional.of(new MoverConfigs.DecelerationConfig(0.04)),
				Optional.empty(),
				Optional.of(List.of((SpellAction) homingChase)),
				Optional.empty(), 1);
		var stateChange = new SpellActions.ConditionalAction(
				new SpellConditions.TickInterval(4, 0), List.of(homingExpand), List.of());

		// 每10tick: 24发 RING BALL RED
		var homingRing = new SpellActions.ConditionalAction(
				new SpellConditions.TickInterval(10, 0),
				List.of(new FireDanmakuAction(YHDanmaku.Bullet.BALL, ColorProvider.constant(DyeColor.RED),
						NumberProvider.constant(24),
						new NumberProviders.Max(NumberProvider.constant(0.6),
								new NumberProviders.Div(new NumberProviders.Distance(), NumberProvider.constant(40))),
						NumberProvider.constant(40),
						new NumberProviders.RandomRange(0, 360), NumberProvider.constant(360), NumberProvider.constant(0),
						PatternType.RING, OriginConfig.caster(), new AimMode.AimModes.Target(),
						Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1)),
				List.of());

		// === Border 预测弹 (dist>26时, 在目标后方32~40格处) ===
		var border = new SpellActions.ConditionalAction(
				new SpellConditions.DistanceAbove(26),
				List.of(new FireDanmakuAction(YHDanmaku.Bullet.CIRCLE, ColorProvider.constant(DyeColor.PINK),
						NumberProvider.constant(4), NumberProvider.constant(0.1), NumberProvider.constant(40),
						new NumberProviders.RandomRange(0, 360), NumberProvider.constant(360),
						NumberProvider.constant(-30),
						PatternType.RANDOM,
						new OriginConfig(OriginConfig.OriginMode.CASTER_FACING,
								NumberProvider.constant(0), NumberProvider.constant(0),
								new NumberProviders.Add(NumberProvider.constant(32),
										new NumberProviders.RandomRange(0, 8)),
								NumberProvider.constant(0)),
						new AimMode.AimModes.Target(),
						Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1)),
				List.of());

		// === 边界限制 ===
		var confine = new ConfineTargetAction(32, 1.0);

		var phase = new PhaseDefinition(mainPhase, List.of(),
				List.of(lissajous, stateChange, homingRing, border, confine),
				List.of(), List.of(), List.of());
		return buildDefinition(id, mainPhase, phase, "touhou_little_maid:komeiji_koishi");
	}

	/**
	 * Remilia: 构建单组激光 (1主 + 3一级分叉 = 4条/组)
	 * 方向在Java中用随机数直接计算, 传入FixedDirection, 避免NumberProvider无法做相对方向旋转的限制
	 * Legacy: dir = Gaussian(3).normalize(), len=rand(25,40), 3 branches at endpoint via asNormal().rotateDegrees
	 */
	private static SpellAction buildRemiliaLaserGroup() {
		var rand = new java.util.Random();
		// Primary direction: uniform sphere via Gaussian normalization
		Vec3 dir = new Vec3(rand.nextGaussian(), rand.nextGaussian(), rand.nextGaussian()).normalize();
		if (dir.lengthSqr() < 0.5) dir = new Vec3(0, 1, 0); // fallback
		int len = 25 + rand.nextInt(16); // 25-40

		// Primary laser
		var primary = new FireLaserAction(YHDanmaku.Laser.LASER, DyeColor.LIGHT_BLUE,
				NumberProvider.constant(140), NumberProvider.constant(len),
				NumberProvider.constant(0), NumberProvider.constant(0),
				new AimMode.AimModes.FixedDirection(dir), OriginConfig.caster(),
				Optional.empty(), 10, 10, 10, Optional.empty(), Optional.empty());

		// Endpoint
		Vec3 endpoint = dir.scale(len); // relative to caster center
		var endOrigin = new OriginConfig(OriginConfig.OriginMode.CASTER,
				NumberProvider.constant(endpoint.x), NumberProvider.constant(endpoint.y),
				NumberProvider.constant(endpoint.z), NumberProvider.constant(0));

		// 3 branches: perpendicular to primary, 120° apart, 45° cone angle
		DanmakuHelper.Orientation ori = DanmakuHelper.getOrientation(dir).asNormal();
		double baseAngle = rand.nextDouble() * 360;
		var actions = new ArrayList<SpellAction>();
		actions.add(primary);
		for (int j = 0; j < 3; j++) {
			double angle = (baseAngle + j * 120) / 180.0 * Math.PI;
			double ver = 45.0 / 180.0 * Math.PI;
			Vec3 branchDir = ori.rotate(angle, ver);
			actions.add(new FireLaserAction(YHDanmaku.Laser.LASER, DyeColor.LIGHT_BLUE,
					NumberProvider.constant(140), NumberProvider.constant(80),
					NumberProvider.constant(0), NumberProvider.constant(0),
					new AimMode.AimModes.FixedDirection(branchDir), endOrigin,
					Optional.empty(), 20, 10, 10, Optional.empty(), Optional.empty()));
		}
		return new SpellActions.SequenceAction(actions);
	}

	// ============================
	// ReimuSpell — 三段追踪弹 + 拦截传送 + 边界环 + 受伤序列
	// ============================
	// Legacy: 5步周期(3×shoot + 2×intercept), shoot: 20发环→减速→重新瞄准→追踪
	//         intercept: 传送到目标前方 + 8×8旋转弹, border: 每tick 8发BALL YELLOW
	//         on_hurt: 激活border + abyss + 延迟sequence弹幕
	//         tick > 2400: 设置abyssal flag
	public static SpellDefinition reimu() {
		var id = rl("hakurei_reimu");
		var mainPhase = rl("hakurei_reimu/main");

		// === Distance-adaptive parameters (perc = clamp((dist-16)/24, 0, 1)) ===
		var dist = new NumberProviders.Distance();
		var perc = new NumberProviders.Clamp(
				new NumberProviders.Div(new NumberProviders.Add(dist, NumberProvider.constant(-16)), NumberProvider.constant(24)),
				NumberProvider.constant(0), NumberProvider.constant(1));
		// r0 = lerp(perc, 6, 20), t0 = lerp(perc, 20, 10), termSpeed = lerp(perc, 1, 3)
		var r0 = new NumberProviders.Add(NumberProvider.constant(6), new NumberProviders.Mul(perc, NumberProvider.constant(14)));
		var t0 = new NumberProviders.Add(NumberProvider.constant(20), new NumberProviders.Mul(perc, NumberProvider.constant(-10)));
		var termSpeed = new NumberProviders.Add(NumberProvider.constant(1), new NumberProviders.Mul(perc, NumberProvider.constant(2)));

		// === HomingTrail chain: 3-stage projectile lifecycle ===
		// Stage 3 (final): fire 1 aimed at target, speed = termSpeed, life = 40+random(20)
		// Color depends on abyss flag: BLUE if abyssal, RED if not
		// Use ByVariable with "$abyss_color" set on entity_flag condition
		var finalHomingLife = new NumberProviders.Add(NumberProvider.constant(40), new NumberProviders.RandomRange(0, 20));
		var finalHomingRed = new FireDanmakuAction(
				YHDanmaku.Bullet.CIRCLE, ColorProvider.constant(DyeColor.RED),
				NumberProvider.constant(1), termSpeed, finalHomingLife,
				NumberProvider.constant(0), NumberProvider.constant(0), NumberProvider.constant(0),
				PatternType.AIMED, OriginConfig.caster(),
				new AimMode.AimModes.DirectionToTarget(),
				Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1);
		var finalHomingBlue = new FireDanmakuAction(
				YHDanmaku.Bullet.CIRCLE, ColorProvider.constant(DyeColor.BLUE),
				NumberProvider.constant(1), termSpeed, finalHomingLife,
				NumberProvider.constant(0), NumberProvider.constant(0), NumberProvider.constant(0),
				PatternType.AIMED, OriginConfig.caster(),
				new AimMode.AimModes.DirectionToTarget(),
				Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1);
		SpellAction finalHoming = new SpellActions.ConditionalAction(
				new SpellConditions.EntityFlagCondition(4),
				List.of(finalHomingBlue), List.of(finalHomingRed));

		// Stage 2 (re-aim homing): 1 CIRCLE PURPLE aimed at target, speed = r0*2/t0/t0*t0 ≈ r0*2/t0
		// Simplified: speed accelerates from small to termSpeed. Use acceleration mover.
		var homingTrail = new FireDanmakuAction(
				YHDanmaku.Bullet.CIRCLE, ColorProvider.constant(DyeColor.PURPLE),
				NumberProvider.constant(1),
				new NumberProviders.Div(new NumberProviders.Mul(r0, NumberProvider.constant(2)), t0),
				t0,
				NumberProvider.constant(0), NumberProvider.constant(0), NumberProvider.constant(0),
				PatternType.AIMED, OriginConfig.caster(),
				new AimMode.AimModes.DirectionToTarget(),
				Optional.empty(), Optional.empty(),
				Optional.of(List.of((SpellAction) finalHoming)),
				Optional.empty(), 1);

		// Stage 1 (expanding ring): 20 CIRCLE LIGHT_GRAY, tilted ring, deceleration mover, onExpiry → homingTrail
		// Legacy: init = getOrientation(dir).rotateDegrees(90, rand*120-30), ring in tilted plane
		// Data-driven: use tilt_angle to tilt the ring plane ~60° off horizontal
		var expandRing = new FireDanmakuAction(
				YHDanmaku.Bullet.CIRCLE, ColorProvider.constant(DyeColor.LIGHT_GRAY),
				NumberProvider.constant(20),
				new NumberProviders.Div(new NumberProviders.Mul(r0, NumberProvider.constant(2)), t0),
				t0,
				new NumberProviders.RandomRange(0, 360), NumberProvider.constant(360), NumberProvider.constant(0),
				PatternType.RING, OriginConfig.caster(), new AimMode.AimModes.Target(),
				Optional.of(new MoverConfigs.DecelerationConfig(0.1)),
				Optional.empty(),
				Optional.of(List.of((SpellAction) homingTrail)),
				Optional.empty(), 1,
				Optional.of(new NumberProviders.RandomRange(30, 90)));

		// === shoot() — steps 0-2 of 5-step cycle (every 10 ticks) ===
		// tick_interval(10, 0) AND (tick/10 % 5 < 3 → tick%50 < 30)
		var shootCondition = new SpellConditions.AndCondition(List.of(
				new SpellConditions.TickInterval(10, 0),
				new SpellConditions.CompareNumbers(
						new NumberProviders.Mod(new NumberProviders.PhaseTick(), NumberProvider.constant(50)),
						"<", NumberProvider.constant(30))
		));
		var shootAction = new SpellActions.ConditionalAction(shootCondition, List.of(expandRing), List.of());

		// === intercept() — steps 3-4 when dist > 40 ===
		// Simplified: teleport toward target + burst 8×8 BUBBLE YELLOW
		// Teleport destination: target + direction_to_caster * 24 (behind target from caster's perspective)
		var interceptCondition = new SpellConditions.AndCondition(List.of(
				new SpellConditions.TickInterval(10, 0),
				new SpellConditions.CompareNumbers(
						new NumberProviders.Mod(new NumberProviders.PhaseTick(), NumberProvider.constant(50)),
						">=", NumberProvider.constant(30)),
				new SpellConditions.DistanceAbove(40)
		));
		// Teleport to 24 blocks ahead of target (along caster→target direction)
		var interceptTeleport = new TeleportAction(
				new OriginConfig(OriginConfig.OriginMode.CASTER_FACING,
						NumberProvider.constant(0), NumberProvider.constant(0),
						new NumberProviders.Max(NumberProvider.constant(24), dist), NumberProvider.constant(0)),
				true);
		// Intercept: legacy is a Ticker running 80 ticks, 8 positions × 8 spinning bullets per tick.
		// Simplified: single burst of BUBBLE YELLOW in SPHERE pattern for spherical coverage.
		// 8 positions × 8 directions = 64 per tick × 80 ticks = 5120 total in legacy.
		// Data-driven: fire a sphere of ~60 bullets per intercept to approximate the visual effect
		// without the extreme entity count.
		var interceptBullets = new FireDanmakuAction(
				YHDanmaku.Bullet.BUBBLE, ColorProvider.constant(DyeColor.YELLOW),
				NumberProvider.constant(60), NumberProvider.constant(2),
				NumberProvider.constant(40),
				NumberProvider.constant(0), NumberProvider.constant(360), NumberProvider.constant(180),
				PatternType.SPHERE, OriginConfig.caster(), new AimMode.AimModes.Target(),
				Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1,
				Optional.empty(), Optional.empty(), Optional.empty(),
				HitBehavior.DISCARD, HitBehavior.DISCARD, Optional.of(DanmakuDamageType.ABYSSAL));
		var interceptAction = new SpellActions.ConditionalAction(interceptCondition,
				List.of(interceptTeleport, interceptBullets), List.of());

		// === border() — 8 BALL YELLOW every tick when border flag is set ===
		// border is activated on_hurt. Use variable "$border" = 1
		var borderSpeed = new NumberProviders.Clamp(
				new NumberProviders.Div(dist, NumberProvider.constant(30)),
				NumberProvider.constant(1.5), NumberProvider.constant(3));
		var borderAction = new SpellActions.ConditionalAction(
				new SpellConditions.CompareNumbers(new NumberProviders.Variable("border"),
						">", NumberProvider.constant(0)),
				List.of(new FireDanmakuAction(
						YHDanmaku.Bullet.BALL, ColorProvider.constant(DyeColor.YELLOW),
						NumberProvider.constant(8), borderSpeed,
						NumberProvider.constant(40),
						new NumberProviders.RandomRange(0, 360), NumberProvider.constant(360), NumberProvider.constant(0),
						PatternType.RING, OriginConfig.caster(), new AimMode.AimModes.Target(),
						Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1,
						Optional.empty(), Optional.empty(), Optional.empty(),
						HitBehavior.DISCARD, HitBehavior.DISCARD, Optional.of(DanmakuDamageType.ABYSSAL))),
				List.of());

		// === tick > 2400: set abyssal flag ===
		var abyssalTimer = new SpellActions.ConditionalAction(
				new SpellConditions.AndCondition(List.of(
						new SpellConditions.TickElapsed(2400),
						new SpellConditions.NotCondition(new SpellConditions.EntityFlagCondition(4))
				)),
				List.of(new SetEntityFlagAction(4, true)),
				List.of());

		// === on_hurt: activate border + abyss check + sequence ===
		// Set $border = 1 (activation flag)
		var hurtSetBorder = new SpellActions.SetVariable("border", 1);
		// If HP < 50%: set abyssal flag
		var hurtAbyss = new SpellActions.ConditionalAction(
				new SpellConditions.HealthBelow(0.5f),
				List.of(new SetEntityFlagAction(4, true)),
				List.of());
		// Abyss mode: 3 rotating BUBBLE BLUE sequences (each 6-step burst, delayed)
		// Non-abyss: 1 BUBBLE sequence
		// Use burst for the delayed steps: 6 steps, 2-tick delay each
		var seqBubbleNorm = new BurstAction(5, 2, "sq", List.of(
				new FireDanmakuAction(
						YHDanmaku.Bullet.BUBBLE, ColorProvider.constant(DyeColor.LIGHT_GRAY),
						NumberProvider.constant(8), NumberProvider.constant(1.0),
						NumberProvider.constant(40),
						new NumberProviders.Add(
								new NumberProviders.RandomRange(0, 360),
								new NumberProviders.Mul(new NumberProviders.Variable("sq"), NumberProvider.constant(9))),
						NumberProvider.constant(360), NumberProvider.constant(0),
						PatternType.RING, OriginConfig.caster(), new AimMode.AimModes.Target(),
						Optional.of(new MoverConfigs.DecelerationConfig(0.08)),
						Optional.empty(),
						Optional.of(List.of((SpellAction) new FireDanmakuAction(
								YHDanmaku.Bullet.BUBBLE, ColorProvider.constant(DyeColor.PURPLE),
								NumberProvider.constant(1), NumberProvider.constant(1.5),
								NumberProvider.constant(40),
								NumberProvider.constant(0), NumberProvider.constant(0), NumberProvider.constant(0),
								PatternType.AIMED, OriginConfig.caster(),
								new AimMode.AimModes.DirectionToTarget(),
								Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1))),
						Optional.empty(), 1)
		));
		var seqBubbleAbyss = new SpellActions.RepeatAction(NumberProvider.constant(3), "ai", List.of(
				new BurstAction(5, 2, "sqb", List.of(
						new FireDanmakuAction(
								YHDanmaku.Bullet.BUBBLE, ColorProvider.constant(DyeColor.BLUE),
								NumberProvider.constant(6), NumberProvider.constant(1.0),
								NumberProvider.constant(40),
								new NumberProviders.Add(
										new NumberProviders.Mul(new NumberProviders.Variable("ai"), NumberProvider.constant(120)),
										new NumberProviders.Mul(new NumberProviders.Variable("sqb"), NumberProvider.constant(12))),
								NumberProvider.constant(360), NumberProvider.constant(0),
								PatternType.RING, OriginConfig.caster(), new AimMode.AimModes.Target(),
								Optional.of(new MoverConfigs.DecelerationConfig(0.08)),
								Optional.empty(),
								Optional.of(List.of((SpellAction) new FireDanmakuAction(
										YHDanmaku.Bullet.BUBBLE, ColorProvider.constant(DyeColor.BLUE),
										NumberProvider.constant(1), NumberProvider.constant(1.5),
										NumberProvider.constant(40),
										NumberProvider.constant(0), NumberProvider.constant(0), NumberProvider.constant(0),
										PatternType.AIMED, OriginConfig.caster(),
										new AimMode.AimModes.DirectionToTarget(),
										Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1,
										Optional.empty(), Optional.empty(), Optional.empty(),
										HitBehavior.DISCARD, HitBehavior.DISCARD, Optional.of(DanmakuDamageType.ABYSSAL)))),
								Optional.empty(), 1,
								Optional.empty(), Optional.empty(), Optional.empty(),
								HitBehavior.DISCARD, HitBehavior.DISCARD, Optional.of(DanmakuDamageType.ABYSSAL))
				))
		));
		var hurtSequence = new SpellActions.ConditionalAction(
				new SpellConditions.EntityFlagCondition(4),
				List.of((SpellAction) seqBubbleAbyss),
				List.of((SpellAction) seqBubbleNorm));

		var onDamageActions = List.<SpellAction>of(
				hurtSetBorder, hurtAbyss, hurtSequence);

		var phase = new PhaseDefinition(mainPhase, List.of(),
				List.of(abyssalTimer, shootAction, interceptAction, borderAction),
				List.of(), onDamageActions, List.of());
		return buildDefinition(id, mainPhase, phase, "touhou_little_maid:hakurei_reimu");
	}

	// ============================
	// YukariSpell — 传送+激光阵+蝴蝶螺旋+受伤反击
	// ============================
	public static SpellDefinition yukari() {
		var id = rl("yukari_yakumo");
		var mainPhase = rl("yukari_yakumo/main");
		var dist = new NumberProviders.Distance();

		// === hidden() pattern: 6 lasers + 6 bubbles + 105 butterflies ===
		var hiddenLasers = new SpellActions.RepeatAction(NumberProvider.constant(6), "hl", List.of(
				(SpellAction) new FireLaserAction(YHDanmaku.Laser.LASER, DyeColor.MAGENTA,
						NumberProvider.constant(120), NumberProvider.constant(80),
						new NumberProviders.Mul(new NumberProviders.Variable("hl"), NumberProvider.constant(60)),
						NumberProvider.constant(0),
						new AimMode.AimModes.Target(), OriginConfig.caster(),
						Optional.<MoverConfig>empty(), 2, 8, 10, Optional.<Double>empty(), Optional.<Double>empty(), Optional.of(DanmakuDamageType.ABYSSAL))
		));
		var hiddenBubbles = new SpellActions.RepeatAction(NumberProvider.constant(6), "hb", List.of(
				(SpellAction) new FireDanmakuAction(
						YHDanmaku.Bullet.BUBBLE, ColorProvider.constant(DyeColor.PURPLE),
						NumberProvider.constant(1), NumberProvider.constant(2), NumberProvider.constant(40),
						new NumberProviders.Mul(new NumberProviders.Variable("hb"), NumberProvider.constant(60)),
						NumberProvider.constant(0), NumberProvider.constant(0),
						PatternType.AIMED, OriginConfig.caster(), new AimMode.AimModes.Target(),
						Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1)
		));
		var hiddenButterflies = new SpellActions.RepeatAction(NumberProvider.constant(3), "bsp", List.of(
				(SpellAction) new FireDanmakuAction(
						YHDanmaku.Bullet.BUTTERFLY, ColorProvider.constant(DyeColor.PURPLE),
						NumberProvider.constant(35),
						new NumberProviders.Add(NumberProvider.constant(1.4),
								new NumberProviders.Mul(new NumberProviders.Variable("bsp"), NumberProvider.constant(0.2))),
						NumberProvider.constant(40), NumberProvider.constant(0),
						NumberProvider.constant(60), NumberProvider.constant(40),
						PatternType.GRID, OriginConfig.caster(), new AimMode.AimModes.Target(),
						Optional.<MoverConfig>empty(), Optional.of(NumberProvider.constant(5)),
						Optional.<List<SpellAction>>empty(), Optional.<List<SpellAction>>empty(), 1)
		));
		SpellAction hiddenFull = new SpellActions.SequenceAction(List.of(
				hiddenLasers, hiddenBubbles, hiddenButterflies));

		// === Teleport + hidden when dist > 40, every 5 ticks ===
		var teleportFar = new TeleportAction(
				new OriginConfig(OriginConfig.OriginMode.CASTER_FACING,
						NumberProvider.constant(0), NumberProvider.constant(0),
						NumberProvider.constant(32), NumberProvider.constant(0)),
				true);
		var teleportHiddenAction = new SpellActions.ConditionalAction(
				new SpellConditions.AndCondition(List.of(
						new SpellConditions.DistanceAbove(40),
						new SpellConditions.TickInterval(5, 0))),
				List.of(teleportFar, hiddenFull), List.of());

		// === Butterfly: 100 per color, CompositeMover, when dist < 20 ===
		var butterflyMover = new MoverConfigs.CompositeMoverConfig(List.of(
				new MoverConfigs.CompositeMoverConfig.Segment(40, new MoverConfigs.DecelerationConfig(0.05)),
				new MoverConfigs.CompositeMoverConfig.Segment(10, new MoverConfigs.ZeroMoverConfig()),
				new MoverConfigs.CompositeMoverConfig.Segment(40, new MoverConfigs.PolarMoverConfig(12, 0, 0, 0, 8, 0.2)),
				new MoverConfigs.CompositeMoverConfig.Segment(40, new MoverConfigs.AccelerationConfig(Vec3.ZERO))));
		var butterflyMoverRev = new MoverConfigs.CompositeMoverConfig(List.of(
				new MoverConfigs.CompositeMoverConfig.Segment(40, new MoverConfigs.DecelerationConfig(0.05)),
				new MoverConfigs.CompositeMoverConfig.Segment(10, new MoverConfigs.ZeroMoverConfig()),
				new MoverConfigs.CompositeMoverConfig.Segment(40, new MoverConfigs.PolarMoverConfig(12, 0, 0, 0, -8, -0.2)),
				new MoverConfigs.CompositeMoverConfig.Segment(40, new MoverConfigs.AccelerationConfig(Vec3.ZERO))));
		var butterflyCyan = new FireDanmakuAction(
				YHDanmaku.Bullet.BUTTERFLY, ColorProvider.constant(DyeColor.CYAN),
				NumberProvider.constant(100), NumberProvider.constant(1.6),
				new NumberProviders.Add(NumberProvider.constant(130), new NumberProviders.RandomRange(0, 40)),
				new NumberProviders.RandomRange(0, 360), NumberProvider.constant(360),
				new NumberProviders.RandomRange(-45, 45),
				PatternType.RING, OriginConfig.caster(), new AimMode.AimModes.CasterFacing(),
				Optional.of(butterflyMover), Optional.empty(), Optional.empty(), Optional.empty(), 1);
		var butterflyMagenta = new FireDanmakuAction(
				YHDanmaku.Bullet.BUTTERFLY, ColorProvider.constant(DyeColor.MAGENTA),
				NumberProvider.constant(100), NumberProvider.constant(1.6),
				new NumberProviders.Add(NumberProvider.constant(130), new NumberProviders.RandomRange(0, 40)),
				new NumberProviders.RandomRange(0, 360), NumberProvider.constant(360),
				new NumberProviders.RandomRange(-45, 45),
				PatternType.RING, OriginConfig.caster(), new AimMode.AimModes.CasterFacing(),
				Optional.of(butterflyMoverRev), Optional.empty(), Optional.empty(), Optional.empty(), 1);
		var butterflyAction = new SpellActions.ConditionalAction(
				new SpellConditions.AndCondition(List.of(
						new SpellConditions.DistanceBelow(20),
						new SpellConditions.CompareNumbers(new NumberProviders.Variable("cd"), "<=", NumberProvider.constant(0)))),
				List.of(butterflyCyan, butterflyMagenta,
						new SpellActions.SetVariable("cd", 60)),
				List.of());

		// === LaserAdder: spiral lasers 120 ticks, when 20 < dist < 40 ===
		// At lt==20: shootGroup RED (5 BUBBLE + spread), at lt==40: shootGroup BLUE
		var shootGroupRed = new SpellActions.ConditionalAction(
				new SpellConditions.CompareNumbers(new NumberProviders.Variable("lt"), "==", NumberProvider.constant(20)),
				List.of(new FireDanmakuAction(
						YHDanmaku.Bullet.BUBBLE, ColorProvider.constant(DyeColor.RED),
						NumberProvider.constant(5), NumberProvider.constant(0.8), NumberProvider.constant(70),
						NumberProvider.constant(0), NumberProvider.constant(30), NumberProvider.constant(30),
						PatternType.RANDOM, OriginConfig.caster(), new AimMode.AimModes.CasterFacing(),
						Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1)),
				List.of());
		var shootGroupBlue = new SpellActions.ConditionalAction(
				new SpellConditions.CompareNumbers(new NumberProviders.Variable("lt"), "==", NumberProvider.constant(40)),
				List.of(new FireDanmakuAction(
						YHDanmaku.Bullet.MENTOS, ColorProvider.constant(DyeColor.BLUE),
						NumberProvider.constant(50), NumberProvider.constant(0.7), NumberProvider.constant(70),
						NumberProvider.constant(0), NumberProvider.constant(30), NumberProvider.constant(30),
						PatternType.RANDOM, OriginConfig.caster(), new AimMode.AimModes.CasterFacing(),
						Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1)),
				List.of());
		var spiralLasers = new BurstAction(120, 1, "lt", List.<SpellAction>of(
				new FireLaserAction(YHDanmaku.Laser.LASER, DyeColor.RED,
						NumberProvider.constant(100), NumberProvider.constant(80),
						new NumberProviders.Add(NumberProvider.constant(-45),
								new NumberProviders.Mul(new NumberProviders.Variable("lt"), NumberProvider.constant(3))),
						NumberProvider.constant(0),
						new AimMode.AimModes.CasterFacing(), OriginConfig.caster(),
						Optional.<MoverConfig>empty(), 0, 0, 0, Optional.<Double>empty(), Optional.<Double>empty(), Optional.of(DanmakuDamageType.ABYSSAL)),
				new FireLaserAction(YHDanmaku.Laser.LASER, DyeColor.BLUE,
						NumberProvider.constant(100), NumberProvider.constant(80),
						new NumberProviders.Add(NumberProvider.constant(45),
								new NumberProviders.Mul(new NumberProviders.Variable("lt"), NumberProvider.constant(3))),
						NumberProvider.constant(0),
						new AimMode.AimModes.CasterFacing(), OriginConfig.caster(),
						Optional.<MoverConfig>empty(), 0, 0, 0, Optional.<Double>empty(), Optional.<Double>empty(), Optional.of(DanmakuDamageType.ABYSSAL)),
				shootGroupRed, shootGroupBlue
		));
		var laserAction = new SpellActions.ConditionalAction(
				new SpellConditions.AndCondition(List.of(
						new SpellConditions.DistanceAbove(20),
						new SpellConditions.DistanceBelow(40),
						new SpellConditions.CompareNumbers(new NumberProviders.Variable("cd"), "<=", NumberProvider.constant(0)))),
				List.of(spiralLasers, new SpellActions.SetVariable("cd", 120)),
				List.of());

		// === Cooldown decrement ===
		var cdDecrement = new SpellActions.ConditionalAction(
				new SpellConditions.CompareNumbers(new NumberProviders.Variable("cd"), ">", NumberProvider.constant(0)),
				List.of(new SpellActions.AddVariable("cd", -1)), List.of());

		// === on_hurt: teleport random + hidden ===
		var onDamageActions = List.<SpellAction>of(
				new TeleportRandomAction(32, 0.8, 0.4, 16, true, true), hiddenFull);

		var phase = new PhaseDefinition(mainPhase, List.of(),
				List.of(cdDecrement, teleportHiddenAction, butterflyAction, laserAction),
				List.of(), onDamageActions, List.of());
		return buildDefinition(id, mainPhase, phase, "touhou_little_maid:yukari_yakumo");
	}

	private static SpellDefinition buildDefinition(ResourceLocation id, ResourceLocation mainPhase,
												   PhaseDefinition phase, String modelId) {
		SpellDisplay display = new SpellDisplay(
				id.toLanguageKey("spell") + ".name",
				id.toLanguageKey("spell") + ".desc",
				Optional.empty(),
				Optional.of(new ResourceLocation(modelId))
		);

		return new SpellDefinition(
				id, display, SpellItemForm.NONE,
				mainPhase, Map.of(mainPhase, phase),
				DifficultyProfile.DEFAULT
		);
	}

	private static ResourceLocation rl(String path) {
		return new ResourceLocation("touhou_little_maid", path);
	}
}
