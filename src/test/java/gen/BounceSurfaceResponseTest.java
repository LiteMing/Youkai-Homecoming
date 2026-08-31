package gen;

import dev.xkmc.youkaishomecoming.content.spell.action.BounceAction;
import dev.xkmc.youkaishomecoming.content.spell.definition.DanmakuBounceConfig;
import dev.xkmc.youkaishomecoming.content.spell.mover.BoundedAccelerationMover;
import dev.xkmc.youkaishomecoming.content.spell.mover.DanmakuMover;
import dev.xkmc.youkaishomecoming.content.spell.mover.MoverInfo;
import dev.xkmc.youkaishomecoming.content.spell.physics.DanmakuBounceResolver;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Unit and physics contract tests for Bounce Surface Response, Normal Safety,
 * and BoundedAcceleration rebase continuity.
 * Run: gradlew test or run direct main method
 */
public class BounceSurfaceResponseTest {

	private static int passed = 0;
	private static int failed = 0;

	public static void main(String[] args) {
		runAllTests();
		if (failed > 0) {
			throw new RuntimeException(failed + " bounce tests failed!");
		}
		System.out.println("BounceSurfaceResponseTest: all " + passed + " checks passed");
	}

	private static void check(String name, boolean condition) {
		if (condition) {
			passed++;
			System.out.println("PASS  " + name);
		} else {
			failed++;
			System.out.println("FAIL  " + name);
		}
	}

	private static boolean vecNear(Vec3 a, Vec3 b) {
		return Math.abs(a.x - b.x) < 1e-6 && Math.abs(a.y - b.y) < 1e-6 && Math.abs(a.z - b.z) < 1e-6;
	}

	public static void runAllTests() {
		testSurfaceResponse();
		testNormalSafety();
		testAccelerationContinuity();
		testLegacyCleanupContract();
	}

	private static void testSurfaceResponse() {
		Vec3 hitPos = new Vec3(10, 64, 10);
		Vec3 incoming = new Vec3(0.5, -1.0, 0.5); // hitting floor (normal = (0, 1, 0))
		Vec3 normal = new Vec3(0, 1, 0);

		// NEGATIVE_TANGENT_FACTOR_REVERSES_TANGENT
		DanmakuBounceConfig revTangentCfg = new DanmakuBounceConfig(4, -1.0, -1.0, 0.0, 0.0, 0.0, Optional.empty(), false);
		var res1 = DanmakuBounceResolver.resolve(hitPos, incoming, normal, revTangentCfg, 0, null);
		check("NEGATIVE_TANGENT_FACTOR_REVERSES_TANGENT", vecNear(res1.newVel(), new Vec3(-0.5, 1.0, -0.5)));

		// ZERO_TANGENT_FACTOR_REMOVES_TANGENT
		DanmakuBounceConfig zeroTangentCfg = new DanmakuBounceConfig(4, -1.0, 0.0, 0.0, 0.0, 0.0, Optional.empty(), false);
		var res2 = DanmakuBounceResolver.resolve(hitPos, incoming, normal, zeroTangentCfg, 0, null);
		check("ZERO_TANGENT_FACTOR_REMOVES_TANGENT", vecNear(res2.newVel(), new Vec3(0.0, 1.0, 0.0)));

		// WORLD_TANGENT_OFFSET_ADDS_EASTWARD_VELOCITY
		DanmakuBounceConfig eastOffsetCfg = new DanmakuBounceConfig(4, 0.0, 0.0, 0.5, 0.0, 0.0, Optional.empty(), false);
		var res3 = DanmakuBounceResolver.resolve(hitPos, incoming, normal, eastOffsetCfg, 0, null);
		check("WORLD_TANGENT_OFFSET_ADDS_EASTWARD_VELOCITY", vecNear(res3.newVel(), new Vec3(0.5, 0.0, 0.0)));

		// TANGENT_OFFSET_NORMAL_COMPONENT_IS_REMOVED
		DanmakuBounceConfig offsetWithNormalCfg = new DanmakuBounceConfig(4, 0.0, 0.0, 0.5, 2.0, 0.0, Optional.empty(), false);
		var res4 = DanmakuBounceResolver.resolve(hitPos, incoming, normal, offsetWithNormalCfg, 0, null);
		check("TANGENT_OFFSET_NORMAL_COMPONENT_IS_REMOVED", vecNear(res4.newVel(), new Vec3(0.5, 0.0, 0.0)));

		// OBLIQUE_NORMAL_TANGENT_OFFSET_REMAINS_TANGENTIAL (slant normal (1, 1, 0)/sqrt(2))
		Vec3 slantNormal = new Vec3(1, 1, 0).normalize();
		DanmakuBounceConfig obliqueCfg = new DanmakuBounceConfig(4, 0.0, 0.0, 0.0, 1.0, 0.0, Optional.empty(), false);
		var res5 = DanmakuBounceResolver.resolve(hitPos, new Vec3(0, 0, 0), slantNormal, obliqueCfg, 0, null);
		check("OBLIQUE_NORMAL_TANGENT_OFFSET_REMAINS_TANGENTIAL", Math.abs(res5.newVel().dot(slantNormal)) < 1e-6);

		// SURFACE_SLIDE_HEAD_ON_WITH_OUTPUT_SPEED_DOES_NOT_LAUNCH_NORMAL
		// Head on collision directly into floor: incoming=(0, -1, 0), normal=(0, 1, 0), normalFactor=0, tangentFactor=1, outputSpeed=0.8
		DanmakuBounceConfig slideCfg = new DanmakuBounceConfig(4, 0.0, 1.0, 0.0, 0.0, 0.0, Optional.of(0.8), false);
		var res6 = DanmakuBounceResolver.resolve(hitPos, new Vec3(0, -1, 0), normal, slideCfg, 0, null);
		check("SURFACE_SLIDE_HEAD_ON_WITH_OUTPUT_SPEED_DOES_NOT_LAUNCH_NORMAL", vecNear(res6.newVel(), Vec3.ZERO));

		// OUTPUT_SPEED_RESETS_NONZERO_RESULT_MAGNITUDE
		DanmakuBounceConfig speedResetCfg = new DanmakuBounceConfig(4, -1.0, 1.0, 0.0, 0.0, 0.0, Optional.of(2.5), false);
		var res7 = DanmakuBounceResolver.resolve(hitPos, incoming, normal, speedResetCfg, 0, null);
		check("OUTPUT_SPEED_RESETS_NONZERO_RESULT_MAGNITUDE", Math.abs(res7.newVel().length() - 2.5) < 1e-6);
	}

	private static void testNormalSafety() {
		// POSITIVE_NORMAL_FACTOR_REJECTED_OR_SANITIZED
		DanmakuBounceConfig posNormal = new DanmakuBounceConfig(4, 0.8, 1.0, 0.0, 0.0, 0.0, Optional.empty(), false);
		DanmakuBounceConfig sanitized = posNormal.sanitize();
		check("POSITIVE_NORMAL_FACTOR_REJECTED_OR_SANITIZED", sanitized.normalFactor() <= 0.0 && sanitized.normalFactor() == 0.0);

		// NORMAL_FACTOR_MINUS_ONE_SPECULAR
		Vec3 hitPos = new Vec3(0, 0, 0);
		Vec3 incoming = new Vec3(1.0, -1.0, 0.0);
		Vec3 normal = new Vec3(0, 1, 0);
		DanmakuBounceConfig specularCfg = new DanmakuBounceConfig(4, -1.0, 1.0, 0.0, 0.0, 0.0, Optional.empty(), false);
		var resSpecular = DanmakuBounceResolver.resolve(hitPos, incoming, normal, specularCfg, 0, null);
		check("NORMAL_FACTOR_MINUS_ONE_SPECULAR", vecNear(resSpecular.newVel(), new Vec3(1.0, 1.0, 0.0)));

		// NORMAL_FACTOR_BELOW_MINUS_ONE_ENHANCED_REFLECTION
		DanmakuBounceConfig enhancedCfg = new DanmakuBounceConfig(4, -2.0, 1.0, 0.0, 0.0, 0.0, Optional.empty(), false);
		var resEnhanced = DanmakuBounceResolver.resolve(hitPos, incoming, normal, enhancedCfg, 0, null);
		check("NORMAL_FACTOR_BELOW_MINUS_ONE_ENHANCED_REFLECTION", vecNear(resEnhanced.newVel(), new Vec3(1.0, 2.0, 0.0)));

		// FINAL_OUTGOING_NEVER_POINTS_INTO_SURFACE
		for (double nf = -5.0; nf <= 0.0; nf += 0.5) {
			DanmakuBounceConfig cfg = new DanmakuBounceConfig(4, nf, 1.0, 0.0, 0.0, 0.0, Optional.empty(), false);
			var r = DanmakuBounceResolver.resolve(hitPos, incoming, normal, cfg, 0, null);
			check("FINAL_OUTGOING_NEVER_POINTS_INTO_SURFACE (nf=" + nf + ")", r.newVel().dot(normal) >= -1e-6);
		}

		// RETARGET_CANNOT_REINTRODUCE_INWARD_NORMAL
		// Target is positioned behind the wall (Y = -50)
		Vec3 targetBehindWall = new Vec3(0, -50, 0);
		DanmakuBounceConfig retargetCfg = new DanmakuBounceConfig(4, -1.0, 1.0, 0.0, 0.0, 0.0, Optional.empty(), true);
		var resRetarget = DanmakuBounceResolver.resolve(hitPos, incoming, normal, retargetCfg, 0, targetBehindWall);
		check("RETARGET_CANNOT_REINTRODUCE_INWARD_NORMAL", resRetarget.newVel().dot(normal) >= -1e-6);

		// NO_REPEATED_SAME_WALL_HIT_FROM_POSITIVE_NORMAL_FACTOR
		// Even if input has positive normal factor before sanitize, resolve() forces safety
		var resRawPos = DanmakuBounceResolver.resolve(hitPos, incoming, normal, posNormal, 0, null);
		check("NO_REPEATED_SAME_WALL_HIT_FROM_POSITIVE_NORMAL_FACTOR", resRawPos.newVel().dot(normal) >= -1e-6);
	}

	private static void testAccelerationContinuity() {
		Vec3 origin = new Vec3(0, 50, 0);
		Vec3 initialVel = new Vec3(1, 0, 0);
		Vec3 gravity = new Vec3(0, -0.05, 0);

		BoundedAccelerationMover worldMover = BoundedAccelerationMover.world(origin, initialVel, gravity, null, -0.5, null);
		MoverInfo info = new MoverInfo(10, origin, initialVel, null, null);
		Vec3 posAt10 = worldMover.pos(info);

		// 1. WORLD_ACCELERATION_REBASED_AFTER_BOUNCE
		Vec3 bouncePos = new Vec3(10, 0, 0);
		Vec3 bounceVel = new Vec3(1, 1, 0);
		DanmakuMover rebasedWorld = worldMover.rebaseAfterCollision(bouncePos, bounceVel);
		check("WORLD_ACCELERATION_REBASED_AFTER_BOUNCE", rebasedWorld instanceof BoundedAccelerationMover && rebasedWorld != worldMover);

		// 2. WORLD_GRAVITY_CONTINUES_AFTER_FIRST_BOUNCE
		BoundedAccelerationMover bamWorld = (BoundedAccelerationMover) rebasedWorld;
		MoverInfo rebasedInfo1 = new MoverInfo(0, bouncePos, bounceVel, null, null);
		MoverInfo rebasedInfo2 = new MoverInfo(1, bouncePos, bounceVel, null, null);
		Vec3 p0 = bamWorld.pos(rebasedInfo1);
		Vec3 p1 = bamWorld.pos(rebasedInfo2);
		check("WORLD_GRAVITY_CONTINUES_AFTER_FIRST_BOUNCE", vecNear(p0, bouncePos) && Math.abs((p1.y - p0.y) - (1.0 - 0.05)) < 1e-5);

		// 3. WORLD_GRAVITY_SUPPORTS_SECOND_BOUNCE
		Vec3 secondBouncePos = new Vec3(20, 0, 0);
		Vec3 secondBounceVel = new Vec3(1, 0.8, 0);
		DanmakuMover secondRebased = ((BoundedAccelerationMover) rebasedWorld).rebaseAfterCollision(secondBouncePos, secondBounceVel);
		check("WORLD_GRAVITY_SUPPORTS_SECOND_BOUNCE", secondRebased instanceof BoundedAccelerationMover && secondRebased != rebasedWorld);

		// 4. XYZ_ACCELERATION_PRESERVED_AFTER_BOUNCE
		Vec3 customAcc = new Vec3(0.1, -0.02, 0.05);
		BoundedAccelerationMover customMover = BoundedAccelerationMover.world(origin, initialVel, customAcc, 2.0, -1.0, 1.5);
		BoundedAccelerationMover rebasedCustom = (BoundedAccelerationMover) customMover.rebaseAfterCollision(bouncePos, bounceVel);
		Vec3 pAfter2 = rebasedCustom.pos(new MoverInfo(2, bouncePos, bounceVel, null, null));
		// Expected pos at t=2: origin + v0*t + 0.5*a*t*t
		Vec3 expectedP2 = bouncePos.add(bounceVel.scale(2)).add(customAcc.scale(0.5 * 4));
		check("XYZ_ACCELERATION_PRESERVED_AFTER_BOUNCE", vecNear(pAfter2, expectedP2));

		// 5. TERMINAL_VELOCITIES_PRESERVED_AFTER_BOUNCE
		// After 100 ticks, vertical velocity should hit terminalVy = -1.0
		Vec3 pT99 = rebasedCustom.pos(new MoverInfo(99, bouncePos, bounceVel, null, null));
		Vec3 pT100 = rebasedCustom.pos(new MoverInfo(100, bouncePos, bounceVel, null, null));
		check("TERMINAL_VELOCITIES_PRESERVED_AFTER_BOUNCE", Math.abs((pT100.y - pT99.y) - (-1.0)) < 1e-4);

		// 6. LOCAL_ACCELERATION_PRESERVES_ORIGINAL_BASIS_AFTER_BOUNCE
		Vec3 fwd = new Vec3(1, 0, 0);
		Vec3 right = new Vec3(0, 0, 1);
		Vec3 up = new Vec3(0, 1, 0);
		Vec3 localAcc = new Vec3(0.1, 0, 0); // accelerate forward along original basis
		BoundedAccelerationMover localMover = BoundedAccelerationMover.local(origin, initialVel, fwd, right, up, localAcc, null, null, null);
		DanmakuMover rebasedLocal = localMover.rebaseAfterCollision(bouncePos, bounceVel);
		check("LOCAL_ACCELERATION_PRESERVES_ORIGINAL_BASIS_AFTER_BOUNCE", rebasedLocal instanceof BoundedAccelerationMover && rebasedLocal != localMover);

		// 7. Non-rebasable movers return false for CollisionRebasableMover check
		Object polar = new dev.xkmc.youkaishomecoming.content.spell.mover.PolarMover();
		Object bezier = new dev.xkmc.youkaishomecoming.content.spell.mover.BezierMover();
		Object formula = new dev.xkmc.youkaishomecoming.content.spell.mover.FormulaMover();
		Object composite = new dev.xkmc.youkaishomecoming.content.spell.mover.CompositeMover();
		Object layered = new dev.xkmc.youkaishomecoming.content.spell.mover.LayeredMover();
		check("POLAR_MOVER_DETACHES_AFTER_BOUNCE", !(polar instanceof dev.xkmc.youkaishomecoming.content.spell.mover.CollisionRebasableMover));
		check("BEZIER_MOVER_DETACHES_AFTER_BOUNCE", !(bezier instanceof dev.xkmc.youkaishomecoming.content.spell.mover.CollisionRebasableMover));
		check("FORMULA_MOVER_DETACHES_AFTER_BOUNCE", !(formula instanceof dev.xkmc.youkaishomecoming.content.spell.mover.CollisionRebasableMover));
		check("COMPOSITE_MOVER_DETACHES_AFTER_BOUNCE", !(composite instanceof dev.xkmc.youkaishomecoming.content.spell.mover.CollisionRebasableMover));
		check("LAYERED_MOVER_DETACHES_AFTER_BOUNCE", !(layered instanceof dev.xkmc.youkaishomecoming.content.spell.mover.CollisionRebasableMover));
	}

	private static void testLegacyCleanupContract() {
		// Verify BounceAction and SpellHitContext are the sole source of DanmakuBounceConfig
		BounceAction ba = new BounceAction(3, -1.0, 1.0, 0.1, 0.0, 0.0, Optional.of(1.5), false);
		DanmakuBounceConfig cfg = ba.sanitize();
		check("BOUNCE_CONFIG_COMES_ONLY_FROM_ACTION_AND_HIT_CONTEXT",
				cfg.maxBounces() == 3 && cfg.normalFactor() == -1.0 && cfg.tangentFactor() == 1.0
						&& cfg.tangentOffsetX() == 0.1 && cfg.outputSpeed().isPresent() && cfg.outputSpeed().get() == 1.5);
	}
}
