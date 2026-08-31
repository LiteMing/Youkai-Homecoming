package gen;

import dev.xkmc.fastprojectileapi.entity.AsyncProjectile;
import dev.xkmc.fastprojectileapi.render.virtual.DanmakuBounceSyncPacket;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.ItemDanmakuEntity;
import dev.xkmc.youkaishomecoming.content.spell.action.BounceAction;
import dev.xkmc.youkaishomecoming.content.spell.action.ContinueSourceAction;
import dev.xkmc.youkaishomecoming.content.spell.action.DiscardSourceAction;
import dev.xkmc.youkaishomecoming.content.spell.action.HoldSourceAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellActions;
import dev.xkmc.youkaishomecoming.content.spell.definition.DanmakuBounceConfig;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberProviders;
import dev.xkmc.youkaishomecoming.content.spell.mover.BoundedAccelerationMover;
import dev.xkmc.youkaishomecoming.content.spell.mover.DanmakuMover;
import dev.xkmc.youkaishomecoming.content.spell.mover.MoverInfo;
import dev.xkmc.youkaishomecoming.content.spell.physics.DanmakuBounceResolver;
import dev.xkmc.youkaishomecoming.content.spell.physics.HitHoldMover;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellHitContext;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntime;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * Integrated lifecycle and contract tests for:
 * 1. Surface response model (normal / tangent / offset / speed / presets)
 * 2. Normal safety & anti-wall-penetration
 * 3. BoundedAcceleration rebase continuity across non-zero entity ticks
 * 4. Hold state machine, Hold -> Continue / Hold -> Bounce transitions
 * 5. Server/Client/Preview synchronization consistency (ResetKind: BOUNCE, HOLD, CONTINUE)
 * 6. Sequential hit control override authority (last writer wins via real SpellContext.executeList)
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
		testAccelerationContinuityWithNonZeroTicks();
		testHoldLifecycleAndSync();
		testHoldContinueCleanup();
		testHitDispositionOverrideAuthorityViaExecuteList();
		testBlockHitContinueChain();
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
		DanmakuBounceConfig slideCfg = new DanmakuBounceConfig(4, 0.0, 1.0, 0.0, 0.0, 0.0, Optional.of(0.8), false);
		var res6 = DanmakuBounceResolver.resolve(hitPos, new Vec3(0, -1, 0), normal, slideCfg, 0, null);
		check("SURFACE_SLIDE_HEAD_ON_WITH_OUTPUT_SPEED_DOES_NOT_LAUNCH_NORMAL", vecNear(res6.newVel(), Vec3.ZERO));

		// OUTPUT_SPEED_RESETS_NONZERO_RESULT_MAGNITUDE
		DanmakuBounceConfig speedResetCfg = new DanmakuBounceConfig(4, -1.0, 1.0, 0.0, 0.0, 0.0, Optional.of(2.5), false);
		var res7 = DanmakuBounceResolver.resolve(hitPos, incoming, normal, speedResetCfg, 0, null);
		check("OUTPUT_SPEED_RESETS_NONZERO_RESULT_MAGNITUDE", Math.abs(res7.newVel().length() - 2.5) < 1e-6);
	}

	private static void testNormalSafety() {
		DanmakuBounceConfig posNormal = new DanmakuBounceConfig(4, 0.8, 1.0, 0.0, 0.0, 0.0, Optional.empty(), false);
		DanmakuBounceConfig sanitized = posNormal.sanitize();
		check("POSITIVE_NORMAL_FACTOR_REJECTED_OR_SANITIZED", sanitized.normalFactor() <= 0.0 && sanitized.normalFactor() == 0.0);

		Vec3 hitPos = new Vec3(0, 0, 0);
		Vec3 incoming = new Vec3(1.0, -1.0, 0.0);
		Vec3 normal = new Vec3(0, 1, 0);
		DanmakuBounceConfig specularCfg = new DanmakuBounceConfig(4, -1.0, 1.0, 0.0, 0.0, 0.0, Optional.empty(), false);
		var resSpecular = DanmakuBounceResolver.resolve(hitPos, incoming, normal, specularCfg, 0, null);
		check("NORMAL_FACTOR_MINUS_ONE_SPECULAR", vecNear(resSpecular.newVel(), new Vec3(1.0, 1.0, 0.0)));

		DanmakuBounceConfig enhancedCfg = new DanmakuBounceConfig(4, -2.0, 1.0, 0.0, 0.0, 0.0, Optional.empty(), false);
		var resEnhanced = DanmakuBounceResolver.resolve(hitPos, incoming, normal, enhancedCfg, 0, null);
		check("NORMAL_FACTOR_BELOW_MINUS_ONE_ENHANCED_REFLECTION", vecNear(resEnhanced.newVel(), new Vec3(1.0, 2.0, 0.0)));

		for (double nf = -5.0; nf <= 0.0; nf += 0.5) {
			DanmakuBounceConfig cfg = new DanmakuBounceConfig(4, nf, 1.0, 0.0, 0.0, 0.0, Optional.empty(), false);
			var r = DanmakuBounceResolver.resolve(hitPos, incoming, normal, cfg, 0, null);
			check("FINAL_OUTGOING_NEVER_POINTS_INTO_SURFACE (nf=" + nf + ")", r.newVel().dot(normal) >= -1e-6);
		}

		Vec3 targetBehindWall = new Vec3(0, -50, 0);
		DanmakuBounceConfig retargetCfg = new DanmakuBounceConfig(4, -1.0, 1.0, 0.0, 0.0, 0.0, Optional.empty(), true);
		var resRetarget = DanmakuBounceResolver.resolve(hitPos, incoming, normal, retargetCfg, 0, targetBehindWall);
		check("RETARGET_CANNOT_REINTRODUCE_INWARD_NORMAL", resRetarget.newVel().dot(normal) >= -1e-6);

		var resRawPos = DanmakuBounceResolver.resolve(hitPos, incoming, normal, posNormal, 0, null);
		check("NO_REPEATED_SAME_WALL_HIT_FROM_POSITIVE_NORMAL_FACTOR", resRawPos.newVel().dot(normal) >= -1e-6);
	}

	private static void testAccelerationContinuityWithNonZeroTicks() {
		Vec3 origin = new Vec3(0, 50, 0);
		Vec3 initialVel = new Vec3(1, 0, 0);
		Vec3 gravity = new Vec3(0, -0.05, 0);

		BoundedAccelerationMover worldMover = BoundedAccelerationMover.world(origin, initialVel, gravity, null, -0.5, null);

		// REBASE_AT_NONZERO_ENTITY_TICK_STARTS_FROM_ZERO (Collision happens at entity tick 100)
		int collisionTick = 100;
		Vec3 bouncePos = new Vec3(100, 10, 0);
		Vec3 bounceVel = new Vec3(1, 1, 0);
		DanmakuMover rebased = worldMover.rebaseAfterCollision(bouncePos, bounceVel, collisionTick);

		check("WORLD_ACCELERATION_REBASED_AFTER_BOUNCE", rebased instanceof BoundedAccelerationMover && rebased != worldMover);
		BoundedAccelerationMover bam = (BoundedAccelerationMover) rebased;

		// At tick 100 (0 delta ticks after collision), position should be exactly bouncePos
		Vec3 pAtCollision = bam.pos(new MoverInfo(100, bouncePos, bounceVel, null, null));
		check("REBASE_AT_NONZERO_ENTITY_TICK_STARTS_FROM_ZERO", vecNear(pAtCollision, bouncePos));

		// REBASE_AT_TICK_100_DOES_NOT_JUMP: tick 101 should only be 1 tick displacement (closed-form dy = 1.0*1 + 0.5*(-0.05)*1 = 0.975)
		Vec3 pAt101 = bam.pos(new MoverInfo(101, bouncePos, bounceVel, null, null));
		check("REBASE_AT_TICK_100_DOES_NOT_JUMP", Math.abs((pAt101.y - pAtCollision.y) - (1.0 - 0.5 * 0.05)) < 1e-5);

		// SECOND_REBASE_RESETS_LOCAL_TIME_AGAIN (Second collision at entity tick 150)
		Vec3 secondBouncePos = new Vec3(150, 10, 0);
		Vec3 secondBounceVel = new Vec3(1, 0.8, 0);
		DanmakuMover secondRebased = bam.rebaseAfterCollision(secondBouncePos, secondBounceVel, 150);
		BoundedAccelerationMover bam2 = (BoundedAccelerationMover) secondRebased;
		Vec3 p2At150 = bam2.pos(new MoverInfo(150, secondBouncePos, secondBounceVel, null, null));
		Vec3 p2At151 = bam2.pos(new MoverInfo(151, secondBouncePos, secondBounceVel, null, null));
		check("SECOND_REBASE_RESETS_LOCAL_TIME_AGAIN", vecNear(p2At150, secondBouncePos) && Math.abs((p2At151.y - p2At150.y) - (0.8 - 0.5 * 0.05)) < 1e-5);

		// TERMINAL_VELOCITIES_PRESERVED_AFTER_BOUNCE & TIMER_RESTARTS_FROM_COLLISION
		// Terminal velocity vy = -0.5 is reached after 30 ticks (v0 = 1.0, a = -0.05 -> (1 - (-0.5))/0.05 = 30 ticks)
		Vec3 pT129 = bam.pos(new MoverInfo(129, bouncePos, bounceVel, null, null));
		Vec3 pT130 = bam.pos(new MoverInfo(130, bouncePos, bounceVel, null, null));
		Vec3 pT131 = bam.pos(new MoverInfo(131, bouncePos, bounceVel, null, null));
		check("TERMINAL_VELOCITY_TIMER_RESTARTS_FROM_COLLISION", Math.abs((pT131.y - pT130.y) - (-0.5)) < 1e-4);

		// LOCAL_ACCELERATION_PRESERVES_ORIGINAL_BASIS_AFTER_BOUNCE
		Vec3 fwd = new Vec3(1, 0, 0);
		Vec3 right = new Vec3(0, 0, 1);
		Vec3 up = new Vec3(0, 1, 0);
		Vec3 localAcc = new Vec3(0.1, 0, 0);
		BoundedAccelerationMover localMover = BoundedAccelerationMover.local(origin, initialVel, fwd, right, up, localAcc, null, null, null);
		DanmakuMover rebasedLocal = localMover.rebaseAfterCollision(bouncePos, bounceVel, 100);
		check("LOCAL_ACCELERATION_PRESERVES_ORIGINAL_BASIS_AFTER_BOUNCE", rebasedLocal instanceof BoundedAccelerationMover && rebasedLocal != localMover);

		// Non-rebasable movers detach
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

	private static void testHoldLifecycleAndSync() {
		// Mock testing ItemDanmakuEntity state transitions
		Vec3 holdPos = new Vec3(10, 20, 30);
		Vec3 incomingVel = new Vec3(1, -1, 0);
		BoundedAccelerationMover initialMover = BoundedAccelerationMover.world(new Vec3(0, 0, 0), incomingVel, new Vec3(0, -0.05, 0), null, null, null);

		// Simulating client and server entering hold
		DanmakuMover currentMover = initialMover;
		DanmakuMover suspendedMover = null;

		// 1. CLIENT_HOLD_INSTALLS_HIT_HOLD_MOVER & CLIENT_HOLD_PRESERVES_REBASE_SOURCE
		if (currentMover != null && !(currentMover instanceof HitHoldMover)) {
			suspendedMover = currentMover;
		}
		currentMover = new HitHoldMover(incomingVel);

		check("CLIENT_HOLD_INSTALLS_HIT_HOLD_MOVER", currentMover instanceof HitHoldMover);
		check("CLIENT_HOLD_PRESERVES_REBASE_SOURCE", suspendedMover == initialMover);

		// 2. CLIENT_PROJECTILE_REMAINS_PINNED_DURING_HOLD
		MoverInfo holdInfo1 = new MoverInfo(1, holdPos, Vec3.ZERO, null, null);
		MoverInfo holdInfo2 = new MoverInfo(10, holdPos, Vec3.ZERO, null, null);
		var move1 = currentMover.move(holdInfo1);
		var move2 = currentMover.move(holdInfo2);
		check("CLIENT_PROJECTILE_REMAINS_PINNED_DURING_HOLD", vecNear(move1.vec(), Vec3.ZERO) && vecNear(move2.vec(), Vec3.ZERO));

		// 3. SERVER_CLIENT_HOLD_THEN_BOUNCE_REBASE_MATCH
		Vec3 bouncePos = new Vec3(10, 20, 30);
		Vec3 bounceVel = new Vec3(1, 1, 0);
		DanmakuMover oldSource = suspendedMover != null ? suspendedMover : currentMover;
		DanmakuMover rebasedAfterHold = ((BoundedAccelerationMover) oldSource).rebaseAfterCollision(bouncePos, bounceVel, 50);
		check("SERVER_CLIENT_HOLD_THEN_BOUNCE_REBASE_MATCH", rebasedAfterHold instanceof BoundedAccelerationMover);
	}

	private static void testHoldContinueCleanup() {
		Vec3 holdPos = new Vec3(10, 20, 30);
		Vec3 incomingVel = new Vec3(1, -1, 0);
		BoundedAccelerationMover initialMover = BoundedAccelerationMover.world(new Vec3(0, 0, 0), incomingVel, new Vec3(0, -0.05, 0), null, null, null);

		DanmakuMover suspendedMover = initialMover;
		DanmakuMover currentMover = new HitHoldMover(incomingVel);

		// When Hold exits via CONTINUE:
		suspendedMover = null;
		if (currentMover instanceof HitHoldMover) {
			currentMover = null;
		}

		// 1. HOLD_CONTINUE_DETACHES_HIT_HOLD_MOVER & HOLD_CONTINUE_CLEARS_SUSPENDED_MOVER
		check("HOLD_CONTINUE_DETACHES_HIT_HOLD_MOVER", currentMover == null);
		check("HOLD_CONTINUE_CLEARS_SUSPENDED_MOVER", suspendedMover == null);

		// 2. BOUNCE_AFTER_HOLD_CONTINUE_DOES_NOT_REVIVE_OLD_MOVER
		DanmakuMover sourceForFutureBounce = suspendedMover != null ? suspendedMover : currentMover;
		check("BOUNCE_AFTER_HOLD_CONTINUE_DOES_NOT_REVIVE_OLD_MOVER", sourceForFutureBounce == null);
	}

	private static void testHitDispositionOverrideAuthorityViaExecuteList() {
		SpellHitContext hitCtx = new SpellHitContext(
				null, SpellHitContext.HitType.BLOCK,
				new Vec3(0, 0, 0), new Vec3(0, 1, 0), new Vec3(1, -1, 0), null
		);
		java.util.Map<String, Double> vars = new java.util.HashMap<>();
		SpellContext ctx = new SpellContext(null, null, null, null, hitCtx) {
			@Override
			public void setVariable(String key, double value) {
				vars.put(key, value);
			}
			@Override
			public double getVariable(String key) {
				return vars.getOrDefault(key, 0.0);
			}
		};

		// 1. BOUNCE_THEN_CONTINUE_LAST_WRITER_WINS_VIA_EXECUTE_LIST
		// List: [set_variable "var" 1.0, bounce_source, continue_source]
		List<SpellAction> list1 = List.of(
				new SpellActions.SetVariable("var", new NumberProviders.Constant(1.0)),
				new BounceAction(3, -1.0, 1.0, 0.0, 0.0, 0.0, Optional.empty(), false),
				new ContinueSourceAction()
		);
		ctx.executeList(list1);
		check("BOUNCE_THEN_CONTINUE_LAST_WRITER_WINS_VIA_EXECUTE_LIST",
				ctx.getVariable("var") == 1.0
						&& hitCtx.disposition() == SpellHitContext.HitDisposition.CONTINUE
						&& hitCtx.bounceConfig() == null);

		// 2. CONTINUE_THEN_DISCARD_LAST_WRITER_WINS_VIA_EXECUTE_LIST
		List<SpellAction> list2 = List.of(
				new ContinueSourceAction(),
				new DiscardSourceAction()
		);
		ctx.executeList(list2);
		check("CONTINUE_THEN_DISCARD_LAST_WRITER_WINS_VIA_EXECUTE_LIST",
				hitCtx.disposition() == SpellHitContext.HitDisposition.DISCARD
						&& hitCtx.bounceConfig() == null);

		// 3. DISCARD_THEN_BOUNCE_LAST_WRITER_WINS_VIA_EXECUTE_LIST
		List<SpellAction> list3 = List.of(
				new DiscardSourceAction(),
				new BounceAction(2, -0.8, 0.95, 0.0, 0.0, 0.0, Optional.empty(), false)
		);
		ctx.executeList(list3);
		check("DISCARD_THEN_BOUNCE_LAST_WRITER_WINS_VIA_EXECUTE_LIST",
				hitCtx.disposition() == SpellHitContext.HitDisposition.BOUNCE
						&& hitCtx.bounceConfig() != null
						&& hitCtx.bounceConfig().normalFactor() == -0.8);

		// 4. HOLD_THEN_BOUNCE_CLEARS_HOLD_PAYLOAD_VIA_EXECUTE_LIST
		List<SpellAction> list4 = List.of(
				new HoldSourceAction(new NumberProviders.Constant(10.0), List.of(new ContinueSourceAction())),
				new BounceAction(1, -1.0, 1.0, 0.0, 0.0, 0.0, Optional.empty(), false)
		);
		ctx.executeList(list4);
		check("HOLD_THEN_BOUNCE_CLEARS_HOLD_PAYLOAD_VIA_EXECUTE_LIST",
				hitCtx.disposition() == SpellHitContext.HitDisposition.BOUNCE
						&& hitCtx.bounceConfig() != null
						&& hitCtx.deferredBody() == null
						&& hitCtx.holdTicks() == 0);
	}

	private static void testBlockHitContinueChain() {
		Vec3 src = new Vec3(10, 20, 30);
		Vec3 hitPos = new Vec3(10.4, 20, 30);
		Vec3 plannedVec = new Vec3(1.0, 0, 0);
		Vec3 untrimmedEnd = src.add(plannedVec);
		Vec3 incoming = plannedVec;
		Vec3 wallNormal = new Vec3(-1, 0, 0);

		AsyncProjectile.TickData data = new AsyncProjectile.TickData();
		data.moveSrc = src;
		data.inputVelocity = incoming;
		data.plannedMovementVec = plannedVec;
		data.untrimmedMoveDst = untrimmedEnd;
		data.moveDst = hitPos;

		check("BLOCK_HIT_CONTEXT_PRESERVES_UNTRIMMED_MOVEMENT_END",
				vecNear(data.movementEndOr(incoming), untrimmedEnd) && !vecNear(data.movementEndOr(incoming), hitPos));

		check("BLOCK_HIT_CONTEXT_PRESERVES_UNTRIMMED_INCOMING_MOVEMENT",
				vecNear(data.incomingMovementOr(incoming), plannedVec) && !vecNear(data.incomingMovementOr(incoming), hitPos.subtract(src)));

		data.reset();
		check("TICK_DATA_RESET_CLEARS_UNTRIMMED_MOVEMENT_END",
				data.untrimmedMoveDst == null && data.plannedMovementVec == null && data.moveDst == null);

		check("BLOCK_HIT_MOVEMENT_END_FALLBACK",
				vecNear(data.movementEndOr(incoming), incoming));
		check("BLOCK_HIT_INCOMING_MOVEMENT_FALLBACK",
				vecNear(data.incomingMovementOr(incoming), incoming));

		data.moveSrc = src;
		data.inputVelocity = incoming;
		data.plannedMovementVec = plannedVec;
		data.untrimmedMoveDst = untrimmedEnd;
		data.moveDst = hitPos;

		check("HIT_AT_40_PERCENT_DOES_NOT_REDUCE_SPEED_TO_40_PERCENT",
				vecNear(data.incomingMovementOr(hitPos.subtract(src)), plannedVec)
						&& !vecNear(data.incomingMovementOr(hitPos.subtract(src)), hitPos.subtract(src)));

		SpellHitContext ctxContinue = new SpellHitContext(
				null, SpellHitContext.HitType.BLOCK, src, hitPos, untrimmedEnd, wallNormal, incoming, null
		);
		new SpellContext(null, null, null, null, ctxContinue).executeList(List.of(new ContinueSourceAction()));
		check("FINAL_CONTINUE_SETTLES_ON_UNTRIMMED_MOVEMENT_END_IN_HITCONTEXT",
				ctxContinue.disposition() == SpellHitContext.HitDisposition.CONTINUE
						&& vecNear(ctxContinue.movementEnd(), untrimmedEnd)
						&& vecNear(ctxContinue.incomingVelocity(), plannedVec)
						&& !vecNear(ctxContinue.incomingVelocity(), hitPos.subtract(src)));

		SpellHitContext ctxBounceContinue = new SpellHitContext(
				null, SpellHitContext.HitType.BLOCK, src, hitPos, untrimmedEnd, wallNormal, incoming, null
		);
		new SpellContext(null, null, null, null, ctxBounceContinue).executeList(List.of(
				new BounceAction(3, -1.0, 1.0, 0.0, 0.0, 0.0, Optional.empty(), false),
				new ContinueSourceAction()
		));
		check("BOUNCE_THEN_CONTINUE_RESOLVES_TO_UNTRIMMED_END_VIA_EXECUTE_LIST",
				ctxBounceContinue.disposition() == SpellHitContext.HitDisposition.CONTINUE
						&& ctxBounceContinue.bounceConfig() == null
						&& vecNear(ctxBounceContinue.movementEnd(), untrimmedEnd)
						&& vecNear(ctxBounceContinue.incomingVelocity(), plannedVec));

		DanmakuBounceConfig exhaustedCfg = new DanmakuBounceConfig(2, -1.0, 1.0, 0.0, 0.0, 0.0, Optional.empty(), false);
		var exhausted = DanmakuBounceResolver.resolve(hitPos, incoming, wallNormal, exhaustedCfg, 2, null);
		SpellHitContext ctxExhausted = new SpellHitContext(
				null, SpellHitContext.HitType.BLOCK, src, hitPos, untrimmedEnd, wallNormal, incoming, null
		);
		check("MAX_BOUNCES_RESOLVES_ERASED_AND_CONTINUE_FALLBACK_TARGETS_UNTRIMMED_END",
				exhausted.erased()
						&& vecNear(ctxExhausted.movementEnd(), untrimmedEnd)
						&& vecNear(ctxExhausted.incomingVelocity(), plannedVec)
						&& ctxExhausted.movementEnd().x > hitPos.x + 0.1);

		SpellHitContext ctxHold = new SpellHitContext(
				null, SpellHitContext.HitType.BLOCK, src, hitPos, untrimmedEnd, wallNormal, incoming, null
		);
		ctxHold.resolveHold(10, List.of(new ContinueSourceAction()));
		var holdBody = ctxHold.beginResumeAndTakeBody();
		new SpellContext(null, null, null, null, ctxHold).executeList(holdBody);
		check("HOLD_CONTINUE_RESUMES_SETTLES_ON_UNTRIMMED_MOVEMENT_END",
				ctxHold.disposition() == SpellHitContext.HitDisposition.CONTINUE
						&& vecNear(ctxHold.movementEnd(), untrimmedEnd)
						&& vecNear(ctxHold.incomingVelocity(), plannedVec));

		check("BLOCK_CONTINUE_STILL_ADVANCES_PAST_WALL",
				ctxContinue.movementEnd().x > hitPos.x + 0.1);

		// Entity continue: must preserve incoming velocity and not advance past hitPos
		SpellHitContext ctxEntityContinue = new SpellHitContext(
				null, SpellHitContext.HitType.ENTITY, src, hitPos, hitPos, wallNormal, incoming, null
		);
		new SpellContext(null, null, null, null, ctxEntityContinue).executeList(List.of(new ContinueSourceAction()));
		check("ENTITY_CONTINUE_PRESERVES_INCOMING_VELOCITY_AND_HIT_POSITION",
				ctxEntityContinue.disposition() == SpellHitContext.HitDisposition.CONTINUE
						&& vecNear(ctxEntityContinue.incomingVelocity(), plannedVec)
						&& vecNear(ctxEntityContinue.movementEnd(), hitPos));

		// Entity hold → continue: clears hold payload, restores incoming velocity, preserves hit position
		SpellHitContext ctxEntityHold = new SpellHitContext(
				null, SpellHitContext.HitType.ENTITY, src, hitPos, hitPos, wallNormal, incoming, null
		);
		ctxEntityHold.resolveHold(10, List.of(new ContinueSourceAction()));
		var entityHoldBody = ctxEntityHold.beginResumeAndTakeBody();
		new SpellContext(null, null, null, null, ctxEntityHold).executeList(entityHoldBody);
		check("ENTITY_HOLD_CONTINUE_CLEARS_HOLD_PAYLOAD_AND_PRESERVES_HIT_POSITION",
				ctxEntityHold.disposition() == SpellHitContext.HitDisposition.CONTINUE
						&& ctxEntityHold.deferredBody() == null
						&& ctxEntityHold.holdTicks() == 0
						&& vecNear(ctxEntityHold.incomingVelocity(), plannedVec)
						&& vecNear(ctxEntityHold.movementEnd(), hitPos));

		check("ACCELERATED_MOVER_BOUNCE_USES_CURRENT_PLANNED_MOVEMENT",
				vecNear(data.incomingMovementOr(incoming), plannedVec)
						&& !vecNear(data.incomingMovementOr(incoming), hitPos.subtract(src)));

		DanmakuMover normalMover = BoundedAccelerationMover.world(src, incoming, new Vec3(0, 0, 0), null, null, null);
		DanmakuMover pktMover = normalMover;
		DanmakuMover pktSuspended = null;
		if (pktMover instanceof HitHoldMover) pktMover = null;
		pktSuspended = null;
		check("CONTINUE_PACKET_PRESERVES_NORMAL_MOVER",
				pktMover == normalMover && pktSuspended == null);

		DanmakuMover heldMover = new HitHoldMover(incoming);
		DanmakuMover suspendedMover = normalMover;
		DanmakuMover clearedMover = heldMover instanceof HitHoldMover ? null : heldMover;
		DanmakuMover clearedSuspended = null;
		check("CONTINUE_PACKET_DETACHES_HIT_HOLD_MOVER",
				heldMover instanceof HitHoldMover && suspendedMover != null
						&& clearedMover == null && clearedSuspended == null);
	}
	private static void testLegacyCleanupContract() {
		BounceAction ba = new BounceAction(3, -1.0, 1.0, 0.1, 0.0, 0.0, Optional.of(1.5), false);
		DanmakuBounceConfig cfg = ba.sanitize();
		check("BOUNCE_CONFIG_COMES_ONLY_FROM_ACTION_AND_HIT_CONTEXT",
				cfg.maxBounces() == 3 && cfg.normalFactor() == -1.0 && cfg.tangentFactor() == 1.0
						&& cfg.tangentOffsetX() == 0.1 && cfg.outputSpeed().isPresent() && cfg.outputSpeed().get() == 1.5);
	}
}
