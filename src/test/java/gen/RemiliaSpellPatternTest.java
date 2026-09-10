package gen;

import com.mojang.serialization.JsonOps;
import dev.xkmc.youkaishomecoming.content.spell.action.PatternEmitter;
import dev.xkmc.youkaishomecoming.content.spell.definition.*;
import dev.xkmc.youkaishomecoming.content.spell.difficulty.DifficultyModifiers;
import dev.xkmc.youkaishomecoming.content.spell.feedback.NoopFeedbackSink;
import dev.xkmc.youkaishomecoming.content.spell.game.RemiliaSpellGeometry;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.CardHolder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Geometry contracts using the built-in spell's parameters, without Forge registration or a level. */
public final class RemiliaSpellPatternTest {
	private static int checks;

	public static void main(String[] args) {
		net.minecraft.SharedConstants.tryDetectVersion();
		net.minecraft.server.Bootstrap.bootStrap();
		var ctx = context(new Vec3(120, 64, -35), new Vec3(-40, 90, 77));
		ctx.setVariable("laser_length", 32);
		ctx.setVariable("laser_spin", 17);
		for (double pitch : new double[]{-85, -45, 0, 45, 85}) {
			for (int yaw = 0; yaw < 360; yaw += 45) {
				ctx.setVariable("laser_pitch", pitch);
				ctx.setVariable("laser_yaw", yaw);
				Vec3 trunk = direction(ctx, false);
				Vec3 end = RemiliaSpellGeometry.laserOrigin(false).resolve(ctx).add(trunk.scale(32));
				List<Vec3> radial = new ArrayList<>();
				for (int j = 0; j < 3; j++) {
					ctx.setVariable("lb", j);
					Vec3 branch = direction(ctx, true);
					check("branch starts at trunk endpoint", end.distanceTo(RemiliaSpellGeometry.laserOrigin(true).resolve(ctx)) < 1e-5);
					check("branch cone is 45 degrees", Math.abs(branch.dot(trunk) - Math.sqrt(0.5)) < 1e-6);
					radial.add(branch.subtract(trunk.scale(branch.dot(trunk))).normalize());
				}
				check("branches are 120 degrees apart", Math.abs(radial.get(0).dot(radial.get(1)) + 0.5) < 1e-6
						&& Math.abs(radial.get(1).dot(radial.get(2)) + 0.5) < 1e-6);
			}
		}
		var encoded = OriginConfig.CODEC.encodeStart(JsonOps.INSTANCE, RemiliaSpellGeometry.midpoint()).getOrThrow(false, s -> {});
		var destination = OriginConfig.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow(false, s -> {});
		for (Vec3 target : List.of(new Vec3(-40, 90, 77), new Vec3(200, -30, -90), new Vec3(120, 160, -35))) {
			Vec3 caster = new Vec3(120, 64, -35);
			ctx = context(caster, target);
			Vec3 midpoint = destination.resolve(ctx);
			check("teleport codec retains target minus caster on all world axes", midpoint.distanceTo(caster.lerp(target, 0.5)) < 1e-6);
			ctx = context(midpoint, target);
			Vec3 targetDirection = target.subtract(midpoint).normalize();
			for (int i : new int[]{0, 40, 79}) {
				ctx.setVariable("si", i);
				Vec3 origin = RemiliaSpellGeometry.towardTarget(RemiliaSpellGeometry.number("$si / 80"), NumberProvider.constant(0)).resolve(ctx);
				check("spear nodes follow the target line", origin.distanceTo(midpoint.lerp(target, i / 80.0)) < 1e-6);
				check("spear flies toward target independently of caster facing",
						new AimMode.AimModes.DirectionToTarget().getBaseDirection(ctx, origin).dot(targetDirection) > 0.999999);
			}
		}
		System.out.println("RemiliaSpellPatternTest: all " + checks + " checks passed");
	}

	private static Vec3 direction(SpellContext ctx, boolean branch) {
		Vec3[] result = new Vec3[1];
		var origin = RemiliaSpellGeometry.laserOrigin(branch);
		PatternEmitter.emit(ctx, origin.resolve(ctx), new PatternEmitter.Settings(
				NumberProvider.constant(1), NumberProvider.constant(1),
				branch ? RemiliaSpellGeometry.number("$laser_spin + $lb * 120") : NumberProvider.constant(0),
				NumberProvider.constant(0), NumberProvider.constant(branch ? -45 : 0), PatternType.AIMED,
				new AimMode.AimModes.FixedDirection(new Vec3(0, 0, 1)), origin.rotation(),
				Optional.empty(), Optional.empty(), Optional.of(RemiliaSpellGeometry.laserRotation(branch)), false),
				(v, base, index, spread) -> result[0] = v.normalize());
		return result[0];
	}

	private static SpellContext context(Vec3 caster, Vec3 target) {
		RandomSource random = RandomSource.create(1);
		CardHolder holder = new CardHolder() {
			@Override public Vec3 center() { return caster; }
			@Override public Vec3 target() { return target; }
			@Override public Vec3 forward() { return target.subtract(caster).normalize().scale(-1); }
			@Override public RandomSource random() { return random; }
			@Override public net.minecraft.world.entity.LivingEntity self() { throw new AssertionError("Caster facing must not be read"); }
			@Override public Vec3 targetVelocity() { return Vec3.ZERO; }
			@Override public net.minecraft.world.entity.LivingEntity targetEntity() { return null; }
			@Override public float getDamage(dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku.IDanmakuType type) { return 0; }
			@Override public void shoot(net.minecraft.world.entity.Entity entity) { throw new AssertionError("No level needed"); }
			@Override public dev.xkmc.youkaishomecoming.content.entity.danmaku.ItemDanmakuEntity prepareDanmaku(int life, Vec3 velocity,
					dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku.Bullet type,
					dev.xkmc.youkaishomecoming.content.spell.definition.DanmakuColor color) { throw new AssertionError("No entities needed"); }
			@Override public dev.xkmc.youkaishomecoming.content.entity.danmaku.ItemLaserEntity prepareLaser(int life, Vec3 pos, Vec3 velocity,
					float length, dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku.Laser type, net.minecraft.world.item.DyeColor color) { throw new AssertionError("No entities needed"); }
			@Override public dev.xkmc.youkaishomecoming.content.entity.danmaku.TextDanmakuEntity prepareTextDanmaku(int life, Vec3 pos,
					Vec3 direction, float size, String text, int color) { throw new AssertionError("No entities needed"); }
			@Override public dev.xkmc.youkaishomecoming.content.spell.shooter.ShooterEntity prepareShooter(
					dev.xkmc.youkaishomecoming.content.spell.shooter.ShooterData data,
					dev.xkmc.youkaishomecoming.content.spell.spellcard.SpellCard spell) { throw new AssertionError("No entities needed"); }
		};
		return new SpellContext(holder, null, null, DifficultyModifiers.DEFAULT, null, NoopFeedbackSink.INSTANCE) {
			private final Map<String, Double> variables = new HashMap<>();
			@Override public double getVariable(String key) { return variables.getOrDefault(key, 0.0); }
			@Override public void setVariable(String key, double value) { variables.put(key, value); }
		};
	}

	private static void check(String label, boolean condition) {
		if (!condition) throw new AssertionError(label);
		checks++;
	}
}
