package dev.xkmc.youkaishomecoming.content.spell.definition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.DanmakuHelper;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import dev.xkmc.youkaishomecoming.content.spell.mover.*;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
		register("formula", FormulaMoverConfig.CODEC, FormulaMoverConfig.class);
		register("attached", AttachedMoverConfig.CODEC, AttachedMoverConfig.class);
		register("attached_free_rot", AttachedFreeRotMoverConfig.CODEC, AttachedFreeRotMoverConfig.class);
		register("fixed_dir", FixedDirMoverConfig.CODEC, FixedDirMoverConfig.class);
		register("orbital", OrbitalMoverConfig.CODEC, OrbitalMoverConfig.class);
		register("translate", TranslateMoverConfig.CODEC, TranslateMoverConfig.class);
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

	/**
	 * Returns the registered type ID string for the given MoverConfig instance,
	 * or null if the type is not registered.
	 */
	public static String getTypeId(MoverConfig config) {
		return CLASS_TO_TYPE.get(config.getClass());
	}

	private static final Pattern FORMULA_VARIABLE = Pattern.compile("\\$([A-Za-z_][A-Za-z0-9_]*)");

	private static String bindFormula(String formula, SpellContext ctx) {
		if (formula == null || formula.isBlank()) return "0";
		if (ctx == null) return formula;
		Matcher matcher = FORMULA_VARIABLE.matcher(formula);
		StringBuffer sb = new StringBuffer();
		while (matcher.find()) {
			matcher.appendReplacement(sb, Matcher.quoteReplacement(formatFormulaNumber(ctx.getVariable(matcher.group(1)))));
		}
		matcher.appendTail(sb);
		return sb.toString()
				.replaceAll("\\bphase_tick\\b", formatFormulaNumber(ctx.phaseTick()))
				.replaceAll("\\btotal_tick\\b", formatFormulaNumber(ctx.totalTick()));
	}

	private static String formatFormulaNumber(double value) {
		if (!Double.isFinite(value)) return "0";
		if (value == (long) value) return Long.toString((long) value);
		return Double.toString(value);
	}

	private static double getNumber(NumberProvider provider, SpellContext ctx) {
		if (ctx != null) return provider.get(ctx);
		if (provider instanceof NumberProviders.Constant c) return c.value();
		return 0;
	}

	private static int getTicks(NumberProvider provider, SpellContext ctx) {
		return Math.max(0, (int) Math.round(getNumber(provider, ctx)));
	}

	@SuppressWarnings("unchecked")
	static final Codec<MoverConfig> DISPATCH_CODEC = Codec.STRING.dispatch(
			"type",
			MoverConfigs::getType,
			id -> {
				var codec = REGISTRY.get(id);
				if (codec == null) throw new IllegalStateException("Unknown MoverConfig: " + id);
				return (Codec<MoverConfig>) (Codec<?>) codec;
			}
	);

	/**
	 * Adds acceleration to the projectile (creates RectMover).
	 * Each component is a NumberProvider evaluated when the projectile is spawned.
	 * JSON: {"type": "acceleration", "x": "$i * 0.02", "y": 0, "z": "sin_rad(tick) * 0.01"}
	 */
	public record AccelerationConfig(NumberProvider x, NumberProvider y, NumberProvider z) implements MoverConfig {
		public static final Codec<AccelerationConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
				NumberProvider.CODEC.optionalFieldOf("x", NumberProvider.constant(0)).forGetter(AccelerationConfig::x),
				NumberProvider.CODEC.optionalFieldOf("y", NumberProvider.constant(0)).forGetter(AccelerationConfig::y),
				NumberProvider.CODEC.optionalFieldOf("z", NumberProvider.constant(0)).forGetter(AccelerationConfig::z)
		).apply(i, AccelerationConfig::new));

		public AccelerationConfig(Vec3 acceleration) {
			this(NumberProvider.constant(acceleration.x), NumberProvider.constant(acceleration.y),
					NumberProvider.constant(acceleration.z));
		}

		@Override
		public DanmakuMover create(Vec3 origin, Vec3 velocity) {
			return new RectMover(origin, velocity, new Vec3(getNumber(x, null), getNumber(y, null), getNumber(z, null)));
		}

		@Override
		public DanmakuMover create(SpellContext ctx, Vec3 origin, Vec3 velocity, Vec3 baseDirection, Vec3 targetPos, Vec3 casterPos) {
			return new RectMover(origin, velocity, new Vec3(getNumber(x, ctx), getNumber(y, ctx), getNumber(z, ctx)));
		}
	}

	/**
	 * Velocity-proportional deceleration: acceleration = -velocity * factor.
	 * At factor=0.06, a bullet with speed=1.0 decelerates to ~0 in ~17 ticks.
	 * JSON: {"type": "deceleration", "factor": 0.06}
	 */
	public record DecelerationConfig(NumberProvider factor) implements MoverConfig {
		public static final Codec<DecelerationConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
				NumberProvider.CODEC.fieldOf("factor").forGetter(DecelerationConfig::factor)
		).apply(i, DecelerationConfig::new));

		public DecelerationConfig(double factor) {
			this(NumberProvider.constant(factor));
		}

		@Override
		public DanmakuMover create(Vec3 origin, Vec3 velocity) {
			Vec3 acc = velocity.scale(-getNumber(factor, null));
			return new RectMover(origin, velocity, acc);
		}

		@Override
		public DanmakuMover create(SpellContext ctx, Vec3 origin, Vec3 velocity, Vec3 baseDirection, Vec3 targetPos, Vec3 casterPos) {
			Vec3 acc = velocity.scale(-getNumber(factor, ctx));
			return new RectMover(origin, velocity, acc);
		}
	}

	/**
	 * Makes the projectile rotate in place (creates RotateMover).
	 * JSON: {"type": "rotate", "degrees_per_tick": 5.0}
	 */
	public record RotateConfig(NumberProvider degreesPerTick) implements MoverConfig {
		public static final Codec<RotateConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
				NumberProvider.CODEC.fieldOf("degrees_per_tick").forGetter(RotateConfig::degreesPerTick)
		).apply(i, RotateConfig::new));

		public RotateConfig(double degreesPerTick) {
			this(NumberProvider.constant(degreesPerTick));
		}

		@Override
		public DanmakuMover create(Vec3 origin, Vec3 velocity) {
			return new RotateMover(velocity.normalize(), getNumber(degreesPerTick, null));
		}

		@Override
		public DanmakuMover create(SpellContext ctx, Vec3 origin, Vec3 velocity, Vec3 baseDirection, Vec3 targetPos, Vec3 casterPos) {
			return new RotateMover(velocity.normalize(), getNumber(degreesPerTick, ctx));
		}
	}

	/**
	 * Polar coordinate motion: combines rectangular (p + v*t + a*t^2/2) and polar (radius + angle) components.
	 * All angular values are in degrees. Creates PolarMover.
	 * JSON: {"type": "polar", "radius": 5.0, "angular_speed": 10.0}
	 */
	public record PolarMoverConfig(
			NumberProvider radius,
			NumberProvider radialSpeed,
			NumberProvider radialAccel,
			NumberProvider initialAngle,
			NumberProvider angularSpeed,
			NumberProvider angularAccel
	) implements MoverConfig {
		public static final Codec<PolarMoverConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
				NumberProvider.CODEC.optionalFieldOf("radius", NumberProvider.constant(0)).forGetter(PolarMoverConfig::radius),
				NumberProvider.CODEC.optionalFieldOf("radial_speed", NumberProvider.constant(0)).forGetter(PolarMoverConfig::radialSpeed),
				NumberProvider.CODEC.optionalFieldOf("radial_accel", NumberProvider.constant(0)).forGetter(PolarMoverConfig::radialAccel),
				NumberProvider.CODEC.optionalFieldOf("initial_angle", NumberProvider.constant(0)).forGetter(PolarMoverConfig::initialAngle),
				NumberProvider.CODEC.optionalFieldOf("angular_speed", NumberProvider.constant(0)).forGetter(PolarMoverConfig::angularSpeed),
				NumberProvider.CODEC.optionalFieldOf("angular_accel", NumberProvider.constant(0)).forGetter(PolarMoverConfig::angularAccel)
		).apply(i, PolarMoverConfig::new));

		public PolarMoverConfig(double radius, double radialSpeed, double radialAccel,
								double initialAngle, double angularSpeed, double angularAccel) {
			this(NumberProvider.constant(radius), NumberProvider.constant(radialSpeed), NumberProvider.constant(radialAccel),
					NumberProvider.constant(initialAngle), NumberProvider.constant(angularSpeed), NumberProvider.constant(angularAccel));
		}

		@Override
		public DanmakuMover create(Vec3 origin, Vec3 velocity) {
			return create(origin, velocity, velocity);
		}

		@Override
		public DanmakuMover create(Vec3 origin, Vec3 velocity, Vec3 baseDirection) {
			return create(null, origin, velocity, baseDirection, Vec3.ZERO, Vec3.ZERO);
		}

		@Override
		public DanmakuMover create(SpellContext ctx, Vec3 origin, Vec3 velocity, Vec3 baseDirection, Vec3 targetPos, Vec3 casterPos) {
			// Rotation plane normal comes from baseDirection (shared by all projectiles).
			// Forward direction (initial polar angle reference) comes from per-projectile velocity,
			// projected onto the rotation plane to ensure orthogonality with normal.
			Vec3 baseDir = baseDirection.lengthSqr() > 1e-8 ? baseDirection.normalize() : new Vec3(0, 0, 1);
			Vec3 projDir = velocity.lengthSqr() > 1e-8 ? velocity.normalize() : baseDir;
			var baseOri = DanmakuHelper.getOrientation(baseDir);
			Vec3 normal = baseOri.normal();
			// Project projDir onto the plane perpendicular to normal
			Vec3 projOnPlane = projDir.subtract(normal.scale(projDir.dot(normal)));
			if (projOnPlane.lengthSqr() < 1e-8) {
				// projDir is parallel to normal — fall back to baseDir's forward
				projOnPlane = baseOri.forward();
			} else {
				projOnPlane = projOnPlane.normalize();
			}
			var mover = new PolarMover(origin, Vec3.ZERO, Vec3.ZERO, normal, projOnPlane);
			mover.radial(getNumber(radius, ctx), getNumber(radialSpeed, ctx), getNumber(radialAccel, ctx));
			mover.angular(
					Math.toRadians(getNumber(initialAngle, ctx)),
					Math.toRadians(getNumber(angularSpeed, ctx)),
					Math.toRadians(getNumber(angularAccel, ctx))
			);
			return mover;
		}
	}

	/**
	 * Composite (segmented) motion: chains multiple movers with specified durations.
	 * JSON: {"type": "composite", "segments": [{"duration": 20, "mover": {"type": "acceleration", ...}}, ...]}
	 */
	public record CompositeMoverConfig(List<Segment> segments) implements MoverConfig {
		public record Segment(NumberProvider duration, MoverConfig mover) {
			public static final Codec<Segment> CODEC = RecordCodecBuilder.create(i -> i.group(
					NumberProvider.CODEC.fieldOf("duration").forGetter(Segment::duration),
					MoverConfig.CODEC.fieldOf("mover").forGetter(Segment::mover)
			).apply(i, Segment::new));

			public Segment(int duration, MoverConfig mover) {
				this(NumberProvider.constant(duration), mover);
			}
		}

		public static final Codec<CompositeMoverConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
				Segment.CODEC.listOf().fieldOf("segments").forGetter(CompositeMoverConfig::segments)
		).apply(i, CompositeMoverConfig::new));

		@Override
		public DanmakuMover create(Vec3 origin, Vec3 velocity) {
			return create(origin, velocity, velocity, Vec3.ZERO, Vec3.ZERO);
		}

		@Override
		public DanmakuMover create(Vec3 origin, Vec3 velocity, Vec3 baseDirection) {
			return create(origin, velocity, baseDirection, Vec3.ZERO, Vec3.ZERO);
		}

		@Override
		public DanmakuMover create(Vec3 origin, Vec3 velocity, Vec3 baseDirection, Vec3 targetPos, Vec3 casterPos) {
			// All segments share the same origin, velocity, and baseDirection.
			// Each sub-mover receives (global tick - segment start) as its local tick via CompositeMover.
			// Position continuity is achieved by the mover parameters themselves (not by chaining pos/vel).
			var composite = new CompositeMover();
			for (var seg : segments) {
				composite.add(getTicks(seg.duration, null), seg.mover.create(origin, velocity, baseDirection, targetPos, casterPos));
			}
			return composite;
		}

		@Override
		public DanmakuMover create(SpellContext ctx, Vec3 origin, Vec3 velocity, Vec3 baseDirection, Vec3 targetPos, Vec3 casterPos) {
			var composite = new CompositeMover();
			for (var seg : segments) {
				composite.add(getTicks(seg.duration, ctx), seg.mover.create(ctx, origin, velocity, baseDirection, targetPos, casterPos));
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
			NumberProvider cp1Forward, NumberProvider cp1Right, NumberProvider cp1Up,
			NumberProvider cp2Forward, NumberProvider cp2Right, NumberProvider cp2Up,
			NumberProvider endForward, NumberProvider endRight, NumberProvider endUp,
			NumberProvider duration
	) implements MoverConfig {
		public static final Codec<BezierMoverConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
				NumberProvider.CODEC.optionalFieldOf("cp1_forward", NumberProvider.constant(5)).forGetter(BezierMoverConfig::cp1Forward),
				NumberProvider.CODEC.optionalFieldOf("cp1_right", NumberProvider.constant(3)).forGetter(BezierMoverConfig::cp1Right),
				NumberProvider.CODEC.optionalFieldOf("cp1_up", NumberProvider.constant(0)).forGetter(BezierMoverConfig::cp1Up),
				NumberProvider.CODEC.optionalFieldOf("cp2_forward", NumberProvider.constant(10)).forGetter(BezierMoverConfig::cp2Forward),
				NumberProvider.CODEC.optionalFieldOf("cp2_right", NumberProvider.constant(-3)).forGetter(BezierMoverConfig::cp2Right),
				NumberProvider.CODEC.optionalFieldOf("cp2_up", NumberProvider.constant(0)).forGetter(BezierMoverConfig::cp2Up),
				NumberProvider.CODEC.optionalFieldOf("end_forward", NumberProvider.constant(15)).forGetter(BezierMoverConfig::endForward),
				NumberProvider.CODEC.optionalFieldOf("end_right", NumberProvider.constant(0)).forGetter(BezierMoverConfig::endRight),
				NumberProvider.CODEC.optionalFieldOf("end_up", NumberProvider.constant(0)).forGetter(BezierMoverConfig::endUp),
				NumberProvider.CODEC.optionalFieldOf("duration", NumberProvider.constant(40)).forGetter(BezierMoverConfig::duration)
		).apply(i, BezierMoverConfig::new));

		public BezierMoverConfig(double cp1Forward, double cp1Right, double cp1Up,
								 double cp2Forward, double cp2Right, double cp2Up,
								 double endForward, double endRight, double endUp,
								 int duration) {
			this(NumberProvider.constant(cp1Forward), NumberProvider.constant(cp1Right), NumberProvider.constant(cp1Up),
					NumberProvider.constant(cp2Forward), NumberProvider.constant(cp2Right), NumberProvider.constant(cp2Up),
					NumberProvider.constant(endForward), NumberProvider.constant(endRight), NumberProvider.constant(endUp),
					NumberProvider.constant(duration));
		}

		@Override
		public DanmakuMover create(Vec3 origin, Vec3 velocity) {
			return create(null, origin, velocity, velocity, Vec3.ZERO, Vec3.ZERO);
		}

		@Override
		public DanmakuMover create(SpellContext ctx, Vec3 origin, Vec3 velocity, Vec3 baseDirection, Vec3 targetPos, Vec3 casterPos) {
			Vec3 dir = velocity.lengthSqr() > 1e-8 ? velocity.normalize() : new Vec3(0, 0, 1);
			var ori = DanmakuHelper.getOrientation(dir);

			// Build absolute control points from relative offsets
			// forward = along velocity dir, right = ori.side(), up = ori.normal()
			Vec3 cp1 = origin
					.add(dir.scale(getNumber(cp1Forward, ctx)))
					.add(ori.side().scale(getNumber(cp1Right, ctx)))
					.add(ori.normal().scale(getNumber(cp1Up, ctx)));
			Vec3 cp2 = origin
					.add(dir.scale(getNumber(cp2Forward, ctx)))
					.add(ori.side().scale(getNumber(cp2Right, ctx)))
					.add(ori.normal().scale(getNumber(cp2Up, ctx)));
			Vec3 end = origin
					.add(dir.scale(getNumber(endForward, ctx)))
					.add(ori.side().scale(getNumber(endRight, ctx)))
					.add(ori.normal().scale(getNumber(endUp, ctx)));

			return new dev.xkmc.youkaishomecoming.content.spell.mover.BezierMover(
					origin, cp1, cp2, end, getTicks(duration, ctx));
		}
	}

	/**
	 * Layered (additive) motion: multiple movers run simultaneously and their displacements are summed.
	 * Unlike composite which chains movers in time, layered applies all movers every tick.
	 * JSON: {"type": "layered", "layers": [{"type": "acceleration", ...}, {"type": "polar", ...}]}
	 */
	public record LayeredMoverConfig(List<MoverConfig> layers) implements MoverConfig {
		public static final Codec<LayeredMoverConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
				MoverConfig.CODEC.listOf().fieldOf("layers").forGetter(LayeredMoverConfig::layers)
		).apply(i, LayeredMoverConfig::new));

		@Override
		public DanmakuMover create(Vec3 origin, Vec3 velocity) {
			return create(origin, velocity, velocity, Vec3.ZERO, Vec3.ZERO);
		}

		@Override
		public DanmakuMover create(Vec3 origin, Vec3 velocity, Vec3 baseDirection) {
			return create(origin, velocity, baseDirection, Vec3.ZERO, Vec3.ZERO);
		}

		@Override
		public DanmakuMover create(Vec3 origin, Vec3 velocity, Vec3 baseDirection, Vec3 targetPos, Vec3 casterPos) {
			var movers = new java.util.ArrayList<DanmakuMover>();
			for (var layer : layers) {
				movers.add(layer.create(origin, velocity, baseDirection, targetPos, casterPos));
			}
			return new LayeredMover(origin, movers);
		}

		@Override
		public DanmakuMover create(SpellContext ctx, Vec3 origin, Vec3 velocity, Vec3 baseDirection, Vec3 targetPos, Vec3 casterPos) {
			var movers = new java.util.ArrayList<DanmakuMover>();
			for (var layer : layers) {
				movers.add(layer.create(ctx, origin, velocity, baseDirection, targetPos, casterPos));
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
				NumberProvider cp1Forward, NumberProvider cp1Right, NumberProvider cp1Up,
				NumberProvider cp2Forward, NumberProvider cp2Right, NumberProvider cp2Up,
				NumberProvider endForward, NumberProvider endRight, NumberProvider endUp,
				NumberProvider duration
		) {
			public static final Codec<BezierSegment> CODEC = RecordCodecBuilder.create(i -> i.group(
					NumberProvider.CODEC.optionalFieldOf("cp1_forward", NumberProvider.constant(5)).forGetter(BezierSegment::cp1Forward),
					NumberProvider.CODEC.optionalFieldOf("cp1_right", NumberProvider.constant(3)).forGetter(BezierSegment::cp1Right),
					NumberProvider.CODEC.optionalFieldOf("cp1_up", NumberProvider.constant(0)).forGetter(BezierSegment::cp1Up),
					NumberProvider.CODEC.optionalFieldOf("cp2_forward", NumberProvider.constant(10)).forGetter(BezierSegment::cp2Forward),
					NumberProvider.CODEC.optionalFieldOf("cp2_right", NumberProvider.constant(-3)).forGetter(BezierSegment::cp2Right),
					NumberProvider.CODEC.optionalFieldOf("cp2_up", NumberProvider.constant(0)).forGetter(BezierSegment::cp2Up),
					NumberProvider.CODEC.optionalFieldOf("end_forward", NumberProvider.constant(15)).forGetter(BezierSegment::endForward),
					NumberProvider.CODEC.optionalFieldOf("end_right", NumberProvider.constant(0)).forGetter(BezierSegment::endRight),
					NumberProvider.CODEC.optionalFieldOf("end_up", NumberProvider.constant(0)).forGetter(BezierSegment::endUp),
					NumberProvider.CODEC.optionalFieldOf("duration", NumberProvider.constant(40)).forGetter(BezierSegment::duration)
			).apply(i, BezierSegment::new));

			public BezierSegment(double cp1Forward, double cp1Right, double cp1Up,
								 double cp2Forward, double cp2Right, double cp2Up,
								 double endForward, double endRight, double endUp,
								 int duration) {
				this(NumberProvider.constant(cp1Forward), NumberProvider.constant(cp1Right), NumberProvider.constant(cp1Up),
						NumberProvider.constant(cp2Forward), NumberProvider.constant(cp2Right), NumberProvider.constant(cp2Up),
						NumberProvider.constant(endForward), NumberProvider.constant(endRight), NumberProvider.constant(endUp),
						NumberProvider.constant(duration));
			}
		}

		public static final Codec<MultiBezierMoverConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
				BezierSegment.CODEC.listOf().fieldOf("segments").forGetter(MultiBezierMoverConfig::segments)
		).apply(i, MultiBezierMoverConfig::new));

		@Override
		public DanmakuMover create(Vec3 origin, Vec3 velocity) {
			return create(null, origin, velocity, velocity, Vec3.ZERO, Vec3.ZERO);
		}

		@Override
		public DanmakuMover create(SpellContext ctx, Vec3 origin, Vec3 velocity, Vec3 baseDirection, Vec3 targetPos, Vec3 casterPos) {
			Vec3 dir = velocity.lengthSqr() > 1e-8 ? velocity.normalize() : new Vec3(0, 0, 1);
			var ori = DanmakuHelper.getOrientation(dir);

			var moverSegments = new java.util.ArrayList<MultiBezierMover.Segment>();
			Vec3 currentStart = origin;

			for (var seg : segments) {
				Vec3 cp1 = currentStart
						.add(dir.scale(getNumber(seg.cp1Forward, ctx)))
						.add(ori.side().scale(getNumber(seg.cp1Right, ctx)))
						.add(ori.normal().scale(getNumber(seg.cp1Up, ctx)));
				Vec3 cp2 = currentStart
						.add(dir.scale(getNumber(seg.cp2Forward, ctx)))
						.add(ori.side().scale(getNumber(seg.cp2Right, ctx)))
						.add(ori.normal().scale(getNumber(seg.cp2Up, ctx)));
				Vec3 end = currentStart
						.add(dir.scale(getNumber(seg.endForward, ctx)))
						.add(ori.side().scale(getNumber(seg.endRight, ctx)))
						.add(ori.normal().scale(getNumber(seg.endUp, ctx)));

				moverSegments.add(new MultiBezierMover.Segment(currentStart, cp1, cp2, end, getTicks(seg.duration, ctx)));
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
	public record SplineMoverConfig(List<double[]> waypoints, NumberProvider duration, boolean closed) implements MoverConfig {
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
				NumberProvider.CODEC.optionalFieldOf("duration", NumberProvider.constant(60)).forGetter(SplineMoverConfig::duration),
				Codec.BOOL.optionalFieldOf("closed", false).forGetter(SplineMoverConfig::closed)
		).apply(i, SplineMoverConfig::new));

		public SplineMoverConfig(List<double[]> waypoints, int duration, boolean closed) {
			this(waypoints, NumberProvider.constant(duration), closed);
		}

		@Override
		public DanmakuMover create(Vec3 origin, Vec3 velocity) {
			return create(null, origin, velocity, velocity, Vec3.ZERO, Vec3.ZERO);
		}

		@Override
		public DanmakuMover create(SpellContext ctx, Vec3 origin, Vec3 velocity, Vec3 baseDirection, Vec3 targetPos, Vec3 casterPos) {
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

			return new dev.xkmc.youkaishomecoming.content.spell.mover.SplineMover(absolutePoints, getTicks(duration, ctx), closed);
		}
	}

	/**
	 * Formula mover: position defined by mathematical expressions for each axis.
	 * Expressions can use 'tick' (or 't'), arithmetic operators, and math functions.
	 * Axes are relative to the initial velocity direction: x=forward, y=right, z=up.
	 * If speed > 0, the projectile also moves forward at that speed (blocks/tick) in addition to the formula offset.
	 * JSON: {"type": "formula", "x": "tick * 0.3", "y": "3 * sin_rad(tick * 0.15)", "z": "3 * cos_rad(tick * 0.15)", "speed": 0.5}
	 */
	public record FormulaMoverConfig(String x, String y, String z, NumberProvider speed) implements MoverConfig {
		public static final Codec<FormulaMoverConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.optionalFieldOf("x", "0").forGetter(FormulaMoverConfig::x),
				Codec.STRING.optionalFieldOf("y", "0").forGetter(FormulaMoverConfig::y),
				Codec.STRING.optionalFieldOf("z", "0").forGetter(FormulaMoverConfig::z),
				NumberProvider.CODEC.optionalFieldOf("speed", NumberProvider.constant(0)).forGetter(FormulaMoverConfig::speed)
		).apply(i, FormulaMoverConfig::new));

		public FormulaMoverConfig(String x, String y, String z, double speed) {
			this(x, y, z, NumberProvider.constant(speed));
		}

		/** Backwards-compatible constructor without speed. */
		public FormulaMoverConfig(String x, String y, String z) {
			this(x, y, z, NumberProvider.constant(0));
		}

		@Override
		public DanmakuMover create(Vec3 origin, Vec3 velocity) {
			return create(origin, velocity, velocity);
		}

		@Override
		public DanmakuMover create(Vec3 origin, Vec3 velocity, Vec3 baseDirection) {
			Vec3 dir = velocity.lengthSqr() > 1e-8 ? velocity.normalize() : new Vec3(0, 0, 1);
			var ori = DanmakuHelper.getOrientation(dir);
			// Drift direction: use baseDirection (shared by all projectiles in the pattern)
			Vec3 drift = baseDirection.lengthSqr() > 1e-8 ? baseDirection.normalize().scale(getNumber(speed, null)) : Vec3.ZERO;
			return new dev.xkmc.youkaishomecoming.content.spell.mover.FormulaMover(
					origin, dir, ori.side(), ori.normal(), x, y, z, drift);
		}

		@Override
		public DanmakuMover create(SpellContext ctx, Vec3 origin, Vec3 velocity, Vec3 baseDirection, Vec3 targetPos, Vec3 casterPos) {
			Vec3 dir = velocity.lengthSqr() > 1e-8 ? velocity.normalize() : new Vec3(0, 0, 1);
			var ori = DanmakuHelper.getOrientation(dir);
			Vec3 drift = baseDirection.lengthSqr() > 1e-8 ? baseDirection.normalize().scale(getNumber(speed, ctx)) : Vec3.ZERO;
			return new dev.xkmc.youkaishomecoming.content.spell.mover.FormulaMover(
					origin, dir, ori.side(), ori.normal(),
					bindFormula(x, ctx), bindFormula(y, ctx), bindFormula(z, ctx), drift);
		}
	}

	/**
	 * Attached: projectile locks to its owner entity's position each tick.
	 * No parameters. Useful for shooter/laser bullets that should follow the caster.
	 * Visual rotation comes from the projectile's own delta movement.
	 * JSON: {"type": "attached"}
	 */
	public record AttachedMoverConfig() implements MoverConfig {
		public static final Codec<AttachedMoverConfig> CODEC = Codec.unit(AttachedMoverConfig::new);

		@Override
		public DanmakuMover create(Vec3 origin, Vec3 velocity) {
			return new AttachedMover();
		}
	}

	/**
	 * Attached + free rotation: like {@link AttachedMoverConfig} but the projectile's
	 * visual rotation also tracks the owner's facing direction (uses ownerForward from MoverInfo).
	 * JSON: {"type": "attached_free_rot"}
	 */
	public record AttachedFreeRotMoverConfig() implements MoverConfig {
		public static final Codec<AttachedFreeRotMoverConfig> CODEC = Codec.unit(AttachedFreeRotMoverConfig::new);

		@Override
		public DanmakuMover create(Vec3 origin, Vec3 velocity) {
			return new AttachedFreeRotMover();
		}
	}

	/**
	 * Fixed direction wrapper: runs an inner mover but locks the projectile's visual
	 * rotation to the pattern's baseDirection (the group's shared aim direction).
	 * The inner mover controls displacement; this wrapper only overrides rotation.
	 * JSON: {"type": "fixed_dir", "inner": { ... mover ... }}
	 */
	public record FixedDirMoverConfig(MoverConfig inner) implements MoverConfig {
		public static final Codec<FixedDirMoverConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
				MoverConfig.CODEC.fieldOf("inner").forGetter(FixedDirMoverConfig::inner)
		).apply(i, FixedDirMoverConfig::new));

		@Override
		public DanmakuMover create(Vec3 origin, Vec3 velocity) {
			return create(origin, velocity, velocity);
		}

		@Override
		public DanmakuMover create(Vec3 origin, Vec3 velocity, Vec3 baseDirection) {
			Vec3 fixedDir = baseDirection.lengthSqr() > 1e-8 ? baseDirection.normalize() : new Vec3(0, 0, 1);
			return new FixedDirMover(inner.create(origin, velocity, baseDirection), fixedDir);
		}

		@Override
		public DanmakuMover create(SpellContext ctx, Vec3 origin, Vec3 velocity, Vec3 baseDirection, Vec3 targetPos, Vec3 casterPos) {
			Vec3 fixedDir = baseDirection.lengthSqr() > 1e-8 ? baseDirection.normalize() : new Vec3(0, 0, 1);
			return new FixedDirMover(inner.create(ctx, origin, velocity, baseDirection, targetPos, casterPos), fixedDir);
		}
	}

	/**
	 * Orbital mover: projectile orbits around the baseDirection axis with formula-driven radius.
	 * The orbit plane is perpendicular to baseDirection. Each projectile's initial angle
	 * is derived from its velocity direction projected onto the orbit plane, so CONE/RING
	 * patterns naturally distribute projectiles around the orbit.
	 * <p>
	 * Use cases:
	 * <ul>
	 *   <li>Dragon pattern (龍紋弾): SPHERE + on_expiry CONE with orbital mover</li>
	 *   <li>Any "ring of bullets orbiting an axis" effect</li>
	 *   <li>Sin-pulsing radius creates breathing/contracting rings</li>
	 * </ul>
	 * JSON: {"type": "orbital", "angular_speed": 5.0, "radius": "3 * sin_rad(tick * 0.05)", "drift": "2 * sin_rad(tick * 0.03)"}
	 */
	public record OrbitalMoverConfig(NumberProvider angularSpeed, String radius, String drift) implements MoverConfig {
		public static final Codec<OrbitalMoverConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
				NumberProvider.CODEC.optionalFieldOf("angular_speed", NumberProvider.constant(0)).forGetter(OrbitalMoverConfig::angularSpeed),
				Codec.STRING.optionalFieldOf("radius", "1").forGetter(OrbitalMoverConfig::radius),
				Codec.STRING.optionalFieldOf("drift", "0").forGetter(OrbitalMoverConfig::drift)
		).apply(i, OrbitalMoverConfig::new));

		public OrbitalMoverConfig(double angularSpeed, String radius, String drift) {
			this(NumberProvider.constant(angularSpeed), radius, drift);
		}

		@Override
		public DanmakuMover create(Vec3 origin, Vec3 velocity) {
			return create(origin, velocity, velocity);
		}

		@Override
		public DanmakuMover create(Vec3 origin, Vec3 velocity, Vec3 baseDirection) {
			// Orbit axis = baseDirection (shared by all projectiles in the pattern)
			Vec3 axis = baseDirection.lengthSqr() > 1e-8 ? baseDirection.normalize() : new Vec3(0, 1, 0);
			return new OrbitalMover(origin, axis, velocity, getNumber(angularSpeed, null), radius, drift);
		}

		@Override
		public DanmakuMover create(SpellContext ctx, Vec3 origin, Vec3 velocity, Vec3 baseDirection, Vec3 targetPos, Vec3 casterPos) {
			Vec3 axis = baseDirection.lengthSqr() > 1e-8 ? baseDirection.normalize() : new Vec3(0, 1, 0);
			return new OrbitalMover(origin, axis, velocity, getNumber(angularSpeed, ctx), bindFormula(radius, ctx), bindFormula(drift, ctx));
		}
	}

	/**
	 * Translate mover: world-coordinate offset defined by formula expressions for each axis.
	 * Unlike formula mover (which uses per-projectile local coordinates), translate operates
	 * in absolute world space: X = east/west, Y = up/down, Z = south/north.
	 * <p>
	 * Supports extended variables: targetX/Y/Z, casterX/Y/Z, originX/Y/Z, tick.
	 * Target and caster positions are snapshotted at bullet creation time.
	 * <p>
	 * For simple aiming at constant speed, use the {@code speed} + {@code aim} shorthand
	 * instead of writing long normalization expressions:
	 * <pre>
	 *   {"type": "translate", "speed": 0.3, "aim": "target"}
	 * </pre>
	 * This pre-computes the normalized direction at creation time, avoiding long formula strings
	 * that would bloat network packets.
	 * <p>
	 * {@code aim} modes: "target" (origin to target), "caster_to_target" (caster to target),
	 * "forward" (pattern base direction), "velocity" (initial projectile velocity),
	 * "fixed" (x/y/z as a fixed direction), "none" (use x/y/z formulas)
	 * <p>
	 * JSON: {"type": "translate", "x": "tick * 0.1", "y": "0", "z": "0"}
	 * JSON: {"type": "translate", "speed": 0.3, "aim": "target"}
	 */
	public record TranslateMoverConfig(String x, String y, String z, NumberProvider speed, String aim) implements MoverConfig {
		public static final Codec<TranslateMoverConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.optionalFieldOf("x", "0").forGetter(TranslateMoverConfig::x),
				Codec.STRING.optionalFieldOf("y", "0").forGetter(TranslateMoverConfig::y),
				Codec.STRING.optionalFieldOf("z", "0").forGetter(TranslateMoverConfig::z),
				NumberProvider.CODEC.optionalFieldOf("speed", NumberProvider.constant(0)).forGetter(TranslateMoverConfig::speed),
				Codec.STRING.optionalFieldOf("aim", "none").forGetter(TranslateMoverConfig::aim)
		).apply(i, TranslateMoverConfig::new));

		public TranslateMoverConfig(String x, String y, String z, double speed, String aim) {
			this(x, y, z, NumberProvider.constant(speed), aim);
		}

		@Override
		public DanmakuMover create(Vec3 origin, Vec3 velocity) {
			return new TranslateMover(origin, x, y, z, Vec3.ZERO, Vec3.ZERO);
		}

		@Override
		public DanmakuMover create(Vec3 origin, Vec3 velocity, Vec3 baseDirection) {
			return new TranslateMover(origin, x, y, z, Vec3.ZERO, Vec3.ZERO);
		}

		@Override
		public DanmakuMover create(Vec3 origin, Vec3 velocity, Vec3 baseDirection, Vec3 targetPos, Vec3 casterPos) {
			double resolvedSpeed = getNumber(speed, null);
			if (resolvedSpeed != 0 && !"none".equals(aim)) {
				return new TranslateMover(origin, resolveAimDirection(null, origin, velocity, baseDirection, targetPos, casterPos), resolvedSpeed);
			}
			return new TranslateMover(origin, x, y, z, targetPos, casterPos);
		}

		@Override
		public DanmakuMover create(SpellContext ctx, Vec3 origin, Vec3 velocity, Vec3 baseDirection, Vec3 targetPos, Vec3 casterPos) {
			double resolvedSpeed = getNumber(speed, ctx);
			if (resolvedSpeed != 0 && !"none".equals(aim)) {
				return new TranslateMover(origin, resolveAimDirection(ctx, origin, velocity, baseDirection, targetPos, casterPos), resolvedSpeed);
			}
			return new TranslateMover(origin, bindFormula(x, ctx), bindFormula(y, ctx), bindFormula(z, ctx), targetPos, casterPos);
		}

		private Vec3 resolveAimDirection(SpellContext ctx, Vec3 origin, Vec3 velocity, Vec3 baseDirection,
										 Vec3 targetPos, Vec3 casterPos) {
			Vec3 dir = switch (aim) {
				case "forward", "base" -> baseDirection;
				case "velocity" -> velocity;
				case "fixed" -> fixedDirection(ctx);
				case "caster_to_target" -> targetPos.subtract(casterPos);
				case "target" -> targetPos.subtract(origin);
				default -> targetPos.subtract(origin);
			};
			double len = dir.length();
			return len > 1e-4 ? dir.scale(1.0 / len) : Vec3.ZERO;
		}

		private Vec3 fixedDirection(SpellContext ctx) {
			return new Vec3(evalAtCreation(x, ctx), evalAtCreation(y, ctx), evalAtCreation(z, ctx));
		}

		private static double evalAtCreation(String formula, SpellContext ctx) {
			var expr = FormulaExpr.parse(bindFormula(formula, ctx));
			return expr == null ? 0 : expr.eval(0);
		}
	}

}
