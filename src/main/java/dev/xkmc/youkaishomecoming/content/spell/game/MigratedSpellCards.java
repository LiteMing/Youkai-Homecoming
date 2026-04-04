package dev.xkmc.youkaishomecoming.content.spell.game;

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
