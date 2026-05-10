package dev.xkmc.youkaishomecoming.content.spell.definition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.DanmakuHelper;
import dev.xkmc.youkaishomecoming.content.spell.mover.*;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MoverConfigs {

	private static final Map<String, Codec<? extends MoverConfig>> REGISTRY = new HashMap<>();
	private static final Map<Class<?>, String> CLASS_TO_TYPE = new HashMap<>();

	static {
		register("acceleration", AccelerationConfig.CODEC, AccelerationConfig.class);
		register("deceleration", DecelerationConfig.CODEC, DecelerationConfig.class);
		register("rotate", RotateConfig.CODEC, RotateConfig.class);
		register("polar", PolarMoverConfig.CODEC, PolarMoverConfig.class);
		register("composite", CompositeMoverConfig.CODEC, CompositeMoverConfig.class);
		register("layered", LayeredMoverConfig.CODEC, LayeredMoverConfig.class);
		register("zero", ZeroMoverConfig.CODEC, ZeroMoverConfig.class);
		register("bezier", BezierMoverConfig.CODEC, BezierMoverConfig.class);
		register("multi_bezier", MultiBezierMoverConfig.CODEC, MultiBezierMoverConfig.class);
		register("spline", SplineMoverConfig.CODEC, SplineMoverConfig.class);
	}

	public static void register(String id, Codec<? extends MoverConfig> codec, Class<? extends MoverConfig> clazz) {
		REGISTRY.put(id, codec);
		CLASS_TO_TYPE.put(clazz, id);
	}

	private static String getType(MoverConfig config) {
		String type = CLASS_TO_TYPE.get(config.getClass());
		if (type != null) return type;
		throw new IllegalStateException("Unknown MoverConfig type: " + config.getClass());
	}

	@SuppressWarnings("unchecked")
	static final Codec<MoverConfig> DISPATCH_CODEC = Codec.STRING.fieldOf("type")
			.codec()
			.dispatch(
					MoverConfigs::getType,
					id -> {
						var codec = REGISTRY.get(id);
						if (codec == null) throw new IllegalStateException("Unknown MoverConfig: " + id);
						return (Codec<MoverConfig>) (Codec<?>) codec;
					}
			);

	/**
	 * Adds constant acceleration to the projectile (creates RectMover).
	 * JSON: {"type": "acceleration", "x": 0, "y": -0.05, "z": 0}
	 */
	public record AccelerationConfig(Vec3 acceleration) implements MoverConfig {
		public static final Codec<AccelerationConfig> CODEC = SpellCodecs.VEC3_CODEC
				.fieldOf("acceleration").codec()
				.xmap(AccelerationConfig::new, AccelerationConfig::acceleration);

		@Override
		public DanmakuMover create(Vec3 origin, Vec3 velocity) {
			return new RectMover(origin, velocity, acceleration);
		}
	}

	/**
	 * Velocity-proportional deceleration: acceleration = -velocity * factor.
	 * At factor=0.06, a bullet with speed=1.0 decelerates to ~0 in ~17 ticks.
	 * JSON: {"type": "deceleration", "factor": 0.06}
	 */
	public record DecelerationConfig(double factor) implements MoverConfig {
		public static final Codec<DecelerationConfig> CODEC = Codec.DOUBLE
				.fieldOf("factor").codec()
				.xmap(DecelerationConfig::new, DecelerationConfig::factor);

		@Override
		public DanmakuMover create(Vec3 origin, Vec3 velocity) {
			Vec3 acc = velocity.scale(-factor);
			return new RectMover(origin, velocity, acc);
		}
	}

	/**
	 * Makes the projectile rotate in place (creates RotateMover).
	 * JSON: {"type": "rotate", "degrees_per_tick": 5.0}
	 */
	public record RotateConfig(double degreesPerTick) implements MoverConfig {
		public static final Codec<RotateConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.DOUBLE.fieldOf("degrees_per_tick").forGetter(RotateConfig::degreesPerTick)
		).apply(i, RotateConfig::new));

		@Override
		public DanmakuMover create(Vec3 origin, Vec3 velocity) {
			return new RotateMover(velocity.normalize(), degreesPerTick);
		}
	}

	/**
	 * Polar coordinate motion: combines rectangular (p + v*t + a*t^2/2) and polar (radius + angle) components.
	 * All angular values are in degrees. Creates PolarMover.
	 * JSON: {"type": "polar", "radius": 5.0, "angular_speed": 10.0}
	 */
	public record PolarMoverConfig(
			double radius,
			double radialSpeed,
			double radialAccel,
			double initialAngle,
			double angularSpeed,
			double angularAccel
	) implements MoverConfig {
		public static final Codec<PolarMoverConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.DOUBLE.optionalFieldOf("radius", 0.0).forGetter(PolarMoverConfig::radius),
				Codec.DOUBLE.optionalFieldOf("radial_speed", 0.0).forGetter(PolarMoverConfig::radialSpeed),
				Codec.DOUBLE.optionalFieldOf("radial_accel", 0.0).forGetter(PolarMoverConfig::radialAccel),
				Codec.DOUBLE.optionalFieldOf("initial_angle", 0.0).forGetter(PolarMoverConfig::initialAngle),
				Codec.DOUBLE.optionalFieldOf("angular_speed", 0.0).forGetter(PolarMoverConfig::angularSpeed),
				Codec.DOUBLE.optionalFieldOf("angular_accel", 0.0).forGetter(PolarMoverConfig::angularAccel)
		).apply(i, PolarMoverConfig::new));

		@Override
		public DanmakuMover create(Vec3 origin, Vec3 velocity) {
			Vec3 dir = velocity.lengthSqr() > 1e-8 ? velocity.normalize() : new Vec3(0, 0, 1);
			var ori = DanmakuHelper.getOrientation(dir);
			var mover = new PolarMover(origin, Vec3.ZERO, Vec3.ZERO, ori.normal(), ori.forward());
			mover.radial(radius, radialSpeed, radialAccel);
			mover.angular(
					Math.toRadians(initialAngle),
					Math.toRadians(angularSpeed),
					Math.toRadians(angularAccel)
			);
			return mover;
		}
	}

	/**
	 * Composite (segmented) motion: chains multiple movers with specified durations.
	 * JSON: {"type": "composite", "segments": [{"duration": 20, "mover": {"type": "acceleration", ...}}, ...]}
	 */
	public record CompositeMoverConfig(List<Segment> segments) implements MoverConfig {
		public record Segment(int duration, MoverConfig mover) {
			public static final Codec<Segment> CODEC = RecordCodecBuilder.create(i -> i.group(
					Codec.INT.fieldOf("duration").forGetter(Segment::duration),
					MoverConfig.CODEC.fieldOf("mover").forGetter(Segment::mover)
			).apply(i, Segment::new));
		}

		public static final Codec<CompositeMoverConfig> CODEC = Segment.CODEC.listOf()
				.fieldOf("segments").codec()
				.xmap(CompositeMoverConfig::new, CompositeMoverConfig::segments);

		@Override
		public DanmakuMover create(Vec3 origin, Vec3 velocity) {
			// All segments share the same origin and velocity, matching legacy CompositeMover behavior.
			// Each sub-mover receives (global tick - segment start) as its local tick via CompositeMover.
			// Position continuity is achieved by the mover parameters themselves (not by chaining pos/vel).
			var composite = new CompositeMover();
			for (var seg : segments) {
				composite.add(seg.duration, seg.mover.create(origin, velocity));
			}
			return composite;
		}
	}

	/**
	 * Zero-velocity mover: projectile stays at its spawn position.
	 * JSON: {"type": "zero"}
	 */
	public record ZeroMoverConfig() implements MoverConfig {
		public static final Codec<ZeroMoverConfig> CODEC = Codec.unit(ZeroMoverConfig::new);

		@Override
		public DanmakuMover create(Vec3 origin, Vec3 velocity) {
			// Use actual ZeroMover (velocity-based, not position-based) to keep bullet
			// at its current position rather than teleporting back to origin.
			// rot0 = rot1 = current direction → no rotation change.
			Vec3 dir = velocity.lengthSqr() > 1e-8 ? velocity : new Vec3(0, 0, 1);
			return new ZeroMover(dir, dir, 1);
		}
	}

	/**
	 * Cubic Bezier curve mover. The projectile follows a cubic Bezier path.
	 * Control points are relative offsets from origin, scaled by velocity direction.
	 * JSON: {"type": "bezier", "cp1_forward": 5, "cp1_right": 3, "cp1_up": 0,
	 *        "cp2_forward": 10, "cp2_right": -3, "cp2_up": 0,
	 *        "end_forward": 15, "end_right": 0, "end_up": 0, "duration": 40}
	 */
	public record BezierMoverConfig(
			double cp1Forward, double cp1Right, double cp1Up,
			double cp2Forward, double cp2Right, double cp2Up,
			double endForward, double endRight, double endUp,
			int duration
	) implements MoverConfig {
		public static final Codec<BezierMoverConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.DOUBLE.optionalFieldOf("cp1_forward", 5.0).forGetter(BezierMoverConfig::cp1Forward),
				Codec.DOUBLE.optionalFieldOf("cp1_right", 3.0).forGetter(BezierMoverConfig::cp1Right),
				Codec.DOUBLE.optionalFieldOf("cp1_up", 0.0).forGetter(BezierMoverConfig::cp1Up),
				Codec.DOUBLE.optionalFieldOf("cp2_forward", 10.0).forGetter(BezierMoverConfig::cp2Forward),
				Codec.DOUBLE.optionalFieldOf("cp2_right", -3.0).forGetter(BezierMoverConfig::cp2Right),
				Codec.DOUBLE.optionalFieldOf("cp2_up", 0.0).forGetter(BezierMoverConfig::cp2Up),
				Codec.DOUBLE.optionalFieldOf("end_forward", 15.0).forGetter(BezierMoverConfig::endForward),
				Codec.DOUBLE.optionalFieldOf("end_right", 0.0).forGetter(BezierMoverConfig::endRight),
				Codec.DOUBLE.optionalFieldOf("end_up", 0.0).forGetter(BezierMoverConfig::endUp),
				Codec.INT.optionalFieldOf("duration", 40).forGetter(BezierMoverConfig::duration)
		).apply(i, BezierMoverConfig::new));

		@Override
		public DanmakuMover create(Vec3 origin, Vec3 velocity) {
			Vec3 dir = velocity.lengthSqr() > 1e-8 ? velocity.normalize() : new Vec3(0, 0, 1);
			var ori = DanmakuHelper.getOrientation(dir);

			// Build absolute control points from relative offsets
			// forward = along velocity dir, right = ori.side(), up = ori.normal()
			Vec3 cp1 = origin
					.add(dir.scale(cp1Forward))
					.add(ori.side().scale(cp1Right))
					.add(ori.normal().scale(cp1Up));
			Vec3 cp2 = origin
					.add(dir.scale(cp2Forward))
					.add(ori.side().scale(cp2Right))
					.add(ori.normal().scale(cp2Up));
			Vec3 end = origin
					.add(dir.scale(endForward))
					.add(ori.side().scale(endRight))
					.add(ori.normal().scale(endUp));

			return new dev.xkmc.youkaishomecoming.content.spell.mover.BezierMover(
					origin, cp1, cp2, end, duration);
		}
	}

	/**
	 * Layered (additive) motion: multiple movers run simultaneously and their displacements are summed.
	 * Unlike composite which chains movers in time, layered applies all movers every tick.
	 * JSON: {"type": "layered", "layers": [{"type": "acceleration", ...}, {"type": "polar", ...}]}
	 */
	public record LayeredMoverConfig(List<MoverConfig> layers) implements MoverConfig {
		public static final Codec<LayeredMoverConfig> CODEC = MoverConfig.CODEC.listOf()
				.fieldOf("layers").codec()
				.xmap(LayeredMoverConfig::new, LayeredMoverConfig::layers);

		@Override
		public DanmakuMover create(Vec3 origin, Vec3 velocity) {
			var movers = new java.util.ArrayList<DanmakuMover>();
			for (var layer : layers) {
				movers.add(layer.create(origin, velocity));
			}
			return new LayeredMover(origin, movers);
		}
	}

	/**
	 * Multi-segment Bezier mover: chains multiple cubic Bezier curves end-to-end.
	 * Each segment has its own control points (relative to origin/velocity direction) and duration.
	 * The end point of segment N automatically becomes the start of segment N+1.
	 * JSON: {"type": "multi_bezier", "segments": [{"cp1_forward": 5, ..., "duration": 40}, ...]}
	 */
	public record MultiBezierMoverConfig(List<BezierSegment> segments) implements MoverConfig {

		public record BezierSegment(
				double cp1Forward, double cp1Right, double cp1Up,
				double cp2Forward, double cp2Right, double cp2Up,
				double endForward, double endRight, double endUp,
				int duration
		) {
			public static final Codec<BezierSegment> CODEC = RecordCodecBuilder.create(i -> i.group(
					Codec.DOUBLE.optionalFieldOf("cp1_forward", 5.0).forGetter(BezierSegment::cp1Forward),
					Codec.DOUBLE.optionalFieldOf("cp1_right", 3.0).forGetter(BezierSegment::cp1Right),
					Codec.DOUBLE.optionalFieldOf("cp1_up", 0.0).forGetter(BezierSegment::cp1Up),
					Codec.DOUBLE.optionalFieldOf("cp2_forward", 10.0).forGetter(BezierSegment::cp2Forward),
					Codec.DOUBLE.optionalFieldOf("cp2_right", -3.0).forGetter(BezierSegment::cp2Right),
					Codec.DOUBLE.optionalFieldOf("cp2_up", 0.0).forGetter(BezierSegment::cp2Up),
					Codec.DOUBLE.optionalFieldOf("end_forward", 15.0).forGetter(BezierSegment::endForward),
					Codec.DOUBLE.optionalFieldOf("end_right", 0.0).forGetter(BezierSegment::endRight),
					Codec.DOUBLE.optionalFieldOf("end_up", 0.0).forGetter(BezierSegment::endUp),
					Codec.INT.optionalFieldOf("duration", 40).forGetter(BezierSegment::duration)
			).apply(i, BezierSegment::new));
		}

		public static final Codec<MultiBezierMoverConfig> CODEC = BezierSegment.CODEC.listOf()
				.fieldOf("segments").codec()
				.xmap(MultiBezierMoverConfig::new, MultiBezierMoverConfig::segments);

		@Override
		public DanmakuMover create(Vec3 origin, Vec3 velocity) {
			Vec3 dir = velocity.lengthSqr() > 1e-8 ? velocity.normalize() : new Vec3(0, 0, 1);
			var ori = DanmakuHelper.getOrientation(dir);

			var moverSegments = new java.util.ArrayList<MultiBezierMover.Segment>();
			Vec3 currentStart = origin;

			for (var seg : segments) {
				Vec3 cp1 = currentStart
						.add(dir.scale(seg.cp1Forward))
						.add(ori.side().scale(seg.cp1Right))
						.add(ori.normal().scale(seg.cp1Up));
				Vec3 cp2 = currentStart
						.add(dir.scale(seg.cp2Forward))
						.add(ori.side().scale(seg.cp2Right))
						.add(ori.normal().scale(seg.cp2Up));
				Vec3 end = currentStart
						.add(dir.scale(seg.endForward))
						.add(ori.side().scale(seg.endRight))
						.add(ori.normal().scale(seg.endUp));

				moverSegments.add(new MultiBezierMover.Segment(currentStart, cp1, cp2, end, seg.duration));
				currentStart = end; // Chain: next segment starts where this one ends
			}

			return new MultiBezierMover(moverSegments);
		}
	}

	/**
	 * Spline mover: the projectile smoothly passes through a list of waypoints using Catmull-Rom interpolation.
	 * Only requires specifying the points the path goes through — no manual control points needed.
	 * Each waypoint is [forward, right, up] relative to the initial velocity direction.
	 * If closed=true, the path loops back to the start (useful for circular/orbital paths).
	 * JSON: {"type": "spline", "waypoints": [[5,5,0],[5,-5,0],[-5,-5,0],[-5,5,0]], "duration": 60, "closed": true}
	 */
	public record SplineMoverConfig(List<double[]> waypoints, int duration, boolean closed) implements MoverConfig {
		public static final Codec<SplineMoverConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.DOUBLE.listOf().listOf().xmap(
						// List<List<Double>> -> List<double[]>
						lists -> lists.stream().map(l -> new double[]{
								l.size() > 0 ? l.get(0) : 0,
								l.size() > 1 ? l.get(1) : 0,
								l.size() > 2 ? l.get(2) : 0
						}).collect(java.util.stream.Collectors.toList()),
						// List<double[]> -> List<List<Double>>
						arrays -> arrays.stream().map(a -> List.of(a[0], a[1], a[2])).collect(java.util.stream.Collectors.toList())
				).fieldOf("waypoints").forGetter(SplineMoverConfig::waypoints),
				Codec.INT.optionalFieldOf("duration", 60).forGetter(SplineMoverConfig::duration),
				Codec.BOOL.optionalFieldOf("closed", false).forGetter(SplineMoverConfig::closed)
		).apply(i, SplineMoverConfig::new));

		@Override
		public DanmakuMover create(Vec3 origin, Vec3 velocity) {
			Vec3 dir = velocity.lengthSqr() > 1e-8 ? velocity.normalize() : new Vec3(0, 0, 1);
			var ori = DanmakuHelper.getOrientation(dir);

			// Convert relative waypoints to absolute world positions
			var absolutePoints = new java.util.ArrayList<Vec3>();
			for (var wp : waypoints) {
				Vec3 point = origin
						.add(dir.scale(wp[0]))
						.add(ori.side().scale(wp[1]))
						.add(ori.normal().scale(wp[2]));
				absolutePoints.add(point);
			}

			return new dev.xkmc.youkaishomecoming.content.spell.mover.SplineMover(absolutePoints, duration, closed);
		}
	}

}
