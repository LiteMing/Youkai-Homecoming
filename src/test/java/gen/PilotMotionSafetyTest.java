package gen;

import dev.xkmc.youkaishomecoming.content.spell.pilot.DodgePilot;
import dev.xkmc.youkaishomecoming.content.spell.pilot.PilotFlightState;
import dev.xkmc.youkaishomecoming.content.spell.pilot.PilotMotion;
import dev.xkmc.youkaishomecoming.content.spell.pilot.PilotProfile;
import dev.xkmc.youkaishomecoming.content.spell.pilot.PilotState;
import dev.xkmc.youkaishomecoming.content.spell.pilot.predict.Threat;
import dev.xkmc.youkaishomecoming.content.spell.pilot.predict.ThreatFrame;
import dev.xkmc.youkaishomecoming.content.spell.pilot.predict.ThreatSemantic;
import dev.xkmc.youkaishomecoming.content.spell.pilot.search.ActionModel;
import dev.xkmc.youkaishomecoming.content.spell.pilot.search.CorridorEvaluator;
import dev.xkmc.youkaishomecoming.content.spell.pilot.search.GroundedModel;
import dev.xkmc.youkaishomecoming.content.spell.pilot.search.PilotSearchNode;
import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.CollisionOracle;
import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.NodeScorer;
import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.SelfBoxModel;
import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.SweptCollision;
import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.ThreatSnapshot;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Focused movement regressions without a world, renderer or FML bootstrap. */
public final class PilotMotionSafetyTest {

	private static int checks;
	private static final Vec3 EAST = new Vec3(1, 0, 0);
	private static final PilotProfile PROFILE = new PilotProfile("MOTION_SAFETY", 0.3, 0.14,
			0.04, 0.68, 2.8, 0.42, 0.55, 8, 2, 100, 12, 8, 4, 1.5f, 0.6, 1.8, 0);

	public static void main(String[] args) {
		arenaCannotLaunchPlayer();
		playerVerticalOwnership();
		flightAndWalkingTransitions();
		fullBodyAvoidsAllSixDirections();
		groundPathsKeepFooting();
		flightCanReachLandingSurface();
		speedLimit();
		System.out.println("PilotMotionSafetyTest: all " + checks + " checks passed");
	}

	private static void arenaCannotLaunchPlayer() {
		for (Vec3 feet : List.of(new Vec3(0, -2000, 0), new Vec3(0, 2000, 0),
				new Vec3(2000, 0, 0), new Vec3(800, -1200, -600))) {
			PilotState state = state(feet);
			state.anchor = Vec3.ZERO;
			state.arena = new AABB(-10, -4, -10, 10, 6, 10);
			state.deadlineNanos = 1; // Exercise the APF fallback after the search budget expires.
			Vec3 result = new DodgePilot(PROFILE).tick(overhead(feet), state);
			check("old arena cannot turn distance into speed at " + feet, result.length() <= 0.3 + 1e-8);
			near("staying outside an old arena stays still", state.clampToArena(feet, Vec3.ZERO), Vec3.ZERO);
		}
		PilotState ground = state(new Vec3(0, -2000, 0));
		ground.grounded = true;
		ground.anchor = Vec3.ZERO;
		ground.arena = new AABB(-10, -4, -10, 10, 6, 10);
		ground.deadlineNanos = 1;
		Vec3 result = new DodgePilot(PROFILE, new GroundedModel()).tick(overhead(ground.feet), ground);
		check("ground boundary cannot manufacture an upward jump", result.y == 0);
		check("ground recovery stays speed limited", result.length() <= 0.3 + 1e-8);
		PilotState edge = state(new Vec3(0.95, 0, 0));
		edge.arena = new AABB(-1, -1, -1, 1, 1, 1);
		near("boundary only shortens outward motion", edge.clampToArena(edge.feet, EAST.scale(0.3)), EAST.scale(0.05));
		edge.feet = new Vec3(1000, 0, 0);
		near("outside inward motion retains its chosen speed", edge.clampToArena(edge.feet, EAST.scale(-0.3)), EAST.scale(-0.3));
		check("search can recover inward from outside", edge.terrainAllows(edge.feet, EAST.scale(-0.3)));
		check("search cannot drift farther outside", !edge.terrainAllows(edge.feet, EAST.scale(0.3)));
	}

	private static void playerVerticalOwnership() {
		Vec3 horizontal = EAST.scale(0.2);
		for (double y : new double[]{2000, -2000, 0.42}) {
			near("flight explicitly brakes old Y=" + y,
					PilotMotion.playerVelocity(horizontal, new Vec3(0, y, 0), true, false), horizontal);
		}
		near("ground does not inherit a stale launch",
				PilotMotion.playerVelocity(Vec3.ZERO, new Vec3(0, 2000, 0), false, true), Vec3.ZERO);
		near("a grounded jump remains a single normal pulse",
				PilotMotion.playerVelocity(new Vec3(0, 0.42, 0), Vec3.ZERO, false, true), new Vec3(0, 0.42, 0));
		near("non-flight airborne motion cannot receive a new jump",
				PilotMotion.playerVelocity(new Vec3(0, 0.42, 0), new Vec3(0, -0.08, 0), false, false),
				new Vec3(0, -0.08, 0));
		near("existing normal jump keeps its vertical physics",
				PilotMotion.playerVelocity(Vec3.ZERO, new Vec3(0, 0.3, 0), false, false), new Vec3(0, 0.3, 0));
		Vec3 gravity = new Vec3(0.2, -0.0784, 0);
		near("ground gravity reaches vanilla move so it can detect onGround",
				PilotMotion.afterCollision(gravity, horizontal, false), gravity);
		near("flight may brake Y at a floor without retaining gravity",
				PilotMotion.afterCollision(gravity, horizontal, true), horizontal);
		near("ground wall clipping keeps physical Y but stops X",
				PilotMotion.afterCollision(gravity, Vec3.ZERO, false), new Vec3(0, gravity.y, 0));
	}

	private static void flightAndWalkingTransitions() {
		PilotFlightState flight = new PilotFlightState();
		check("a supported player starts with walking", !flight.update(0, true, false, true));
		flight.takeOff();
		check("takeoff survives vanilla's grounded flying flag", flight.update(1, true, false, true));
		check("one contact cannot land", !flight.landIfReady(1, 4, false, true));
		check("duplicate join/tick observations cannot accelerate landing", flight.update(1, true, false, true)
				&& !flight.landIfReady(1, 4, false, true));
		check("leaving support keeps flight", flight.update(2, true, false, false));
		for (int tick = 3; tick < 6; tick++) {
			check("brief ground contact keeps the flight plan " + tick, flight.update(tick, true, false, true));
			check("landing waits for the committed route " + tick, !flight.landIfReady(tick, 4, false, true));
		}
		flight.update(6, true, false, true);
		check("unsafe ground route cannot force landing", !flight.landIfReady(6, 4, false, false));
		check("a needed upward escape cannot force landing", !flight.landIfReady(6, 4, true, true));
		check("settled safe ground switches back to walking", flight.landIfReady(6, 4, false, true));
		check("walking remains stable after landing", !flight.update(7, true, false, true));
		flight.takeOff();
		check("walking can take off again", flight.update(8, true, false, false));
		check("flight permission loss clears the remembered mode", !flight.update(9, false, false, false));
		check("flight-capable airborne player can resume automatically", flight.update(10, true, false, false));
		flight.reset();
		check("manual/idle handoff forgets stale flight intent", !flight.update(11, true, false, true));
		check("a player without flight permission stays in ground physics", !flight.update(12, false, false, false));
	}

	private static void fullBodyAvoidsAllSixDirections() {
		Vec3[] axes = {EAST, EAST.reverse(), new Vec3(0, 1, 0), new Vec3(0, -1, 0),
				new Vec3(0, 0, 1), new Vec3(0, 0, -1)};
		for (double height : new double[]{1.8, 0.6}) {
			for (Vec3 axis : axes) {
				PilotState state = state(Vec3.ZERO);
				// A tiny danmaku hitbox must not shrink the standing/gliding terrain body.
				state.selfBox = new SelfBoxModel(-0.03, height / 2, -0.03, 0.03, height / 2 + 0.06, 0.03,
						-0.3, 0, -0.3, 0.3, height, 0.3, 0.3f);
				AABB body = state.selfBox.bodyAt(state.feet);
				state.oracle = solid(wallBeside(body, axis));
				Vec3 crossing = axis.scale(3);
				check("far endpoint is empty " + axis + " height=" + height, state.oracle.isFree(body.move(crossing)));
				check("sweep still rejects the intervening solid " + axis + " height=" + height,
						!state.terrainAllows(state.feet, crossing));
				check("corridor rejects the same solid " + axis + " height=" + height,
						!CorridorEvaluator.evaluate(ThreatSnapshot.empty(4), state, NodeScorer.defaults(), crossing, 1).collisionFree());
				state.anchor = axis.scale(10);
				state.deadlineNanos = 1;
				near("APF cannot bypass the solid " + axis + " height=" + height,
						new DodgePilot(PROFILE).tick(ThreatSnapshot.empty(4), state), Vec3.ZERO);
			}
		}
	}

	private static void groundPathsKeepFooting() {
		PilotState state = state(Vec3.ZERO);
		state.grounded = true;
		state.anchor = EAST.scale(10);
		state.oracle = new CollisionOracle() {
			@Override public boolean isFree(AABB box) { return true; }
			@Override public boolean isSupported(AABB box) { return box.minX < -0.25; }
		};
		check("ground corridor rejects a ledge",
				!CorridorEvaluator.evaluate(ThreatSnapshot.empty(4), state, NodeScorer.defaults(), EAST.scale(0.3), 1).collisionFree());
		state.deadlineNanos = 1;
		near("APF fallback cannot walk off a ledge",
				new DodgePilot(PROFILE, new GroundedModel()).tick(ThreatSnapshot.empty(4), state), Vec3.ZERO);
		state.deadlineNanos = 0;
		state.inputPreference = EAST;
		ActionModel refineOnly = new ActionModel() {
			@Override public List<Action> actions(PilotSearchNode node, double high, double low) { return List.of(); }
			@Override public List<Vec3> directionSeeds() { return List.of(EAST); }
			@Override public boolean supportsVerticalMovement() { return false; }
		};
		near("emergency refinement cannot bypass footing",
				new DodgePilot(PROFILE, refineOnly).tick(ThreatSnapshot.empty(4), state), Vec3.ZERO);

		CollisionOracle gap = new CollisionOracle() {
			@Override public boolean isFree(AABB box) { return true; }
			@Override public boolean isSupported(AABB box) { return box.minX < 0.1 || box.maxX > 1.9; }
		};
		AABB body = state.selfBox.bodyAt(Vec3.ZERO);
		check("both banks have support", gap.isSupported(body) && gap.isSupported(body.move(3, 0, 0)));
		check("ground step cannot skip a hole between supported endpoints", !gap.isMovementSafe(body, EAST.scale(3), true));
		check("ground jump cannot skip that hole either", !gap.isMovementSafe(body, new Vec3(3, 0.42, 0), true));
		check("flight is allowed above a gap", gap.isMovementSafe(body, EAST.scale(3), false));
		state.oracle = new CollisionOracle() {
			@Override public boolean isFree(AABB box) { return true; }
			@Override public boolean isSupported(AABB box) { return false; }
		};
		check("ground mode cannot jump again in midair", !state.terrainAllows(Vec3.ZERO, new Vec3(0, 0.42, 0)));
	}

	private static void speedLimit() {
		check("large velocity is capped", PilotMotion.limitSpeed(new Vec3(0, 10000, 0), 0.3).length() <= 0.3 + 1e-8);
		near("non-finite velocity is discarded", PilotMotion.limitSpeed(new Vec3(0, Double.NaN, 0), 0.3), Vec3.ZERO);
		check("ground command cap retains the model's jump", new GroundedModel().maxSpeed(0.3, 0.14) == 0.42);
		ActionModel excessive = (node, high, low) -> List.of(new ActionModel.Action(new Vec3(0, 10000, 0), true, 0));
		PilotState state = state(Vec3.ZERO);
		state.inputPreference = new Vec3(0, 1, 0);
		check("controller caps a search result before application",
				new DodgePilot(PROFILE, excessive).tick(ThreatSnapshot.empty(4), state).length() <= 0.3 + 1e-8);
	}

	private static void flightCanReachLandingSurface() {
		PilotState state = state(new Vec3(0, 0.1, 0));
		state.anchor = new Vec3(0, -5, 0);
		state.deadlineNanos = 1;
		state.oracle = new CollisionOracle() {
			@Override public boolean isFree(AABB box) { return box.minY >= 0; }
			@Override public Vec3 resolveMovement(AABB from, Vec3 delta) {
				return new Vec3(delta.x, Math.max(-from.minY, delta.y), delta.z);
			}
		};
		Vec3 landing = new DodgePilot(PROFILE).tick(ThreatSnapshot.empty(4), state);
		near("flight clips a downward step onto the floor instead of hovering above it",
				state.feet.add(landing), Vec3.ZERO);
		check("landing displacement remains speed limited", landing.length() <= PROFILE.highSpeed());
	}

	private static PilotState state(Vec3 feet) {
		return new PilotState(feet, Vec3.ZERO, SelfBoxModel.vanillaPlayer());
	}

	private static ThreatSnapshot overhead(Vec3 feet) {
		ThreatFrame frame = new ThreatFrame(feet.add(0.5, 1.8, 0), null, 0.2f, true);
		return ThreatSnapshot.of(List.of(new Threat(1, new ThreatFrame[]{frame, frame, frame, frame},
				ThreatSemantic.DANMAKU, null, 1)), 4);
	}

	private static CollisionOracle solid(AABB solid) {
		return new CollisionOracle() {
			@Override public boolean isFree(AABB box) { return !box.intersects(solid); }
			@Override public boolean isPathFree(AABB from, Vec3 delta) {
				return SweptCollision.sweptHit(from, delta, solid, Vec3.ZERO) < 0;
			}
		};
	}

	private static AABB wallBeside(AABB body, Vec3 axis) {
		if (axis.x > 0) return new AABB(body.maxX + 0.05, -10, -10, body.maxX + 0.1, 10, 10);
		if (axis.x < 0) return new AABB(body.minX - 0.1, -10, -10, body.minX - 0.05, 10, 10);
		if (axis.y > 0) return new AABB(-10, body.maxY + 0.05, -10, 10, body.maxY + 0.1, 10);
		if (axis.y < 0) return new AABB(-10, body.minY - 0.1, -10, 10, body.minY - 0.05, 10);
		if (axis.z > 0) return new AABB(-10, -10, body.maxZ + 0.05, 10, 10, body.maxZ + 0.1);
		return new AABB(-10, -10, body.minZ - 0.1, 10, 10, body.minZ - 0.05);
	}

	private static void near(String label, Vec3 actual, Vec3 expected) {
		check(label + ": " + actual, actual.distanceToSqr(expected) < 1e-12);
	}

	private static void check(String label, boolean valid) {
		checks++;
		if (!valid) throw new AssertionError(label);
	}
}
