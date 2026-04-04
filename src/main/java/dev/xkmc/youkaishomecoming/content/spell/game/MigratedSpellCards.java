package dev.xkmc.youkaishomecoming.content.spell.game;

import dev.xkmc.youkaishomecoming.content.spell.action.*;
import dev.xkmc.youkaishomecoming.content.spell.condition.SpellConditions;
import dev.xkmc.youkaishomecoming.content.spell.definition.*;
import dev.xkmc.youkaishomecoming.content.spell.difficulty.DifficultyProfile;
import dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;

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
