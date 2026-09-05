package dev.xkmc.youkaishomecoming.content.spell.definition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

public class NumberProviders {

	private static final Map<String, Codec<? extends NumberProvider>> REGISTRY = new HashMap<>();
	private static final Map<Class<?>, String> CLASS_TO_TYPE = new HashMap<>();

	static {
		register("constant", Constant.CODEC, Constant.class);
		register("random", RandomRange.CODEC, RandomRange.class);
		register("lerp_time", LerpOverTime.CODEC, LerpOverTime.class);
		register("by_health", ByHealthRatio.CODEC, ByHealthRatio.class);
		register("tick_mod", PhaseTickMod.CODEC, PhaseTickMod.class);
		register("variable", Variable.CODEC, Variable.class);
		register("phase_tick", PhaseTick.CODEC, PhaseTick.class);
		register("total_tick", TotalTick.CODEC, TotalTick.class);
		register("sin_deg", SinDeg.CODEC, SinDeg.class);
		register("cos_deg", CosDeg.CODEC, CosDeg.class);
		register("sin_rad", SinRad.CODEC, SinRad.class);
		register("cos_rad", CosRad.CODEC, CosRad.class);
		register("add", Add.CODEC, Add.class);
		register("mul", Mul.CODEC, Mul.class);
		register("distance", Distance.CODEC, Distance.class);
		register("div", Div.CODEC, Div.class);
		register("mod", Mod.CODEC, Mod.class);
		register("sqrt", Sqrt.CODEC, Sqrt.class);
		register("abs", Abs.CODEC, Abs.class);
		register("floor", Floor.CODEC, Floor.class);
		register("ceil", Ceil.CODEC, Ceil.class);
		register("round", Round.CODEC, Round.class);
		register("pow", Pow.CODEC, Pow.class);
		register("root", Root.CODEC, Root.class);
		register("log", Log.CODEC, Log.class);
		register("exp", Exp.CODEC, Exp.class);
		register("indexed", Indexed.CODEC, Indexed.class);
		register("random_choice", RandomChoice.CODEC, RandomChoice.class);
		register("conditional", Conditional.CODEC, Conditional.class);
		register("gaussian", GaussianRandom.CODEC, GaussianRandom.class);
		register("max", Max.CODEC, Max.class);
		register("min", Min.CODEC, Min.class);
		register("clamp", Clamp.CODEC, Clamp.class);
		register("caster_x", CasterX.CODEC, CasterX.class);
		register("caster_y", CasterY.CODEC, CasterY.class);
		register("caster_z", CasterZ.CODEC, CasterZ.class);
		register("caster_max_health", CasterMaxHealth.CODEC, CasterMaxHealth.class);
		register("caster_power", CasterPower.CODEC, CasterPower.class);
		register("target_x", TargetX.CODEC, TargetX.class);
		register("target_y", TargetY.CODEC, TargetY.class);
		register("target_z", TargetZ.CODEC, TargetZ.class);
		register("target_facing_x", TargetFacingX.CODEC, TargetFacingX.class);
		register("target_facing_y", TargetFacingY.CODEC, TargetFacingY.class);
		register("target_facing_z", TargetFacingZ.CODEC, TargetFacingZ.class);
		register("target_height", TargetHeight.CODEC, TargetHeight.class);
		register("game_difficulty", GameDifficulty.CODEC, GameDifficulty.class);
		register("target_fly_time", TargetFlyTime.CODEC, TargetFlyTime.class);
		register("target_speed", TargetSpeed.CODEC, TargetSpeed.class);
		register("callback_value", CallbackValue.CODEC, CallbackValue.class);
	}

	public static void register(String id, Codec<? extends NumberProvider> codec, Class<? extends NumberProvider> clazz) {
		REGISTRY.put(id, codec);
		CLASS_TO_TYPE.put(clazz, id);
	}

	private static String getType(NumberProvider provider) {
		String type = CLASS_TO_TYPE.get(provider.getClass());
		if (type != null) return type;
		throw new IllegalStateException("Unknown NumberProvider type: " + provider.getClass());
	}

	@SuppressWarnings("unchecked")
	private static final Codec<NumberProvider> DISPATCH_CODEC = Codec.STRING.dispatch(
			"type",
			NumberProviders::getType,
			id -> {
				var codec = REGISTRY.get(id);
				if (codec == null) throw new IllegalStateException("Unknown NumberProvider: " + id);
				return (Codec<NumberProvider>) (Codec<?>) codec;
			}
	);

	/**
	 * Main codec: accepts either a bare number (→ Constant) or a typed object.
	 */
	static final Codec<NumberProvider> CODEC = new Codec<>() {
		@Override
		public <T> DataResult<Pair<NumberProvider, T>> decode(DynamicOps<T> ops, T input) {
			// Try bare number first
			var numResult = ops.getNumberValue(input);
			if (numResult.result().isPresent()) {
				double val = numResult.result().get().doubleValue();
				return DataResult.success(Pair.of(new Constant(val), ops.empty()));
			}
			var strResult = ops.getStringValue(input);
			if (strResult.result().isPresent()) {
				NumberProvider parsed = NumberExprParser.parse(strResult.result().get());
				if (parsed != null) {
					return DataResult.success(Pair.of(parsed, ops.empty()));
				}
			}
			// Fall back to dispatch codec
			return DISPATCH_CODEC.decode(ops, input);
		}

		@Override
		public <T> DataResult<T> encode(NumberProvider input, DynamicOps<T> ops, T prefix) {
			// Encode Constant as bare number for brevity
			if (input instanceof Constant c) {
				return DataResult.success(ops.createDouble(c.value()));
			}
			return DISPATCH_CODEC.encode(input, ops, prefix);
		}
	};

	// --- Implementations ---

	/**
	 * Fixed constant value.
	 * JSON: 12 or {"type": "constant", "value": 12}
	 */
	public record Constant(double value) implements NumberProvider {
		public static final Codec<Constant> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.DOUBLE.fieldOf("value").forGetter(Constant::value)
		).apply(i, Constant::new));

		@Override
		public double get(SpellContext ctx) {
			return value;
		}
	}

	/**
	 * Random value in [min, max] range, sampled each call.
	 * JSON: {"type": "random", "min": 0.6, "max": 1.0}
	 */
	public record RandomRange(double min, double max) implements NumberProvider {
		public static final Codec<RandomRange> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.DOUBLE.fieldOf("min").forGetter(RandomRange::min),
				Codec.DOUBLE.fieldOf("max").forGetter(RandomRange::max)
		).apply(i, RandomRange::new));

		@Override
		public double get(SpellContext ctx) {
			return min + ctx.holder().random().nextDouble() * (max - min);
		}
	}

	/**
	 * Linear interpolation over phaseTick from start to end over duration ticks.
	 * Clamps at end value after duration.
	 * JSON: {"type": "lerp_time", "start": 0.5, "end": 1.5, "duration": 200}
	 */
	public record LerpOverTime(double start, double end, int duration) implements NumberProvider {
		public static final Codec<LerpOverTime> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.DOUBLE.fieldOf("start").forGetter(LerpOverTime::start),
				Codec.DOUBLE.fieldOf("end").forGetter(LerpOverTime::end),
				Codec.INT.fieldOf("duration").forGetter(LerpOverTime::duration)
		).apply(i, LerpOverTime::new));

		@Override
		public double get(SpellContext ctx) {
			double t = duration > 0 ? Mth.clamp((double) ctx.phaseTick() / duration, 0, 1) : 1;
			return Mth.lerp(t, start, end);
		}
	}

	/**
	 * Interpolation by health ratio: atFull when health=100%, atEmpty when health=0%.
	 * JSON: {"type": "by_health", "at_full": 0.8, "at_empty": 1.6}
	 */
	public record ByHealthRatio(double atFull, double atEmpty) implements NumberProvider {
		public static final Codec<ByHealthRatio> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.DOUBLE.fieldOf("at_full").forGetter(ByHealthRatio::atFull),
				Codec.DOUBLE.fieldOf("at_empty").forGetter(ByHealthRatio::atEmpty)
		).apply(i, ByHealthRatio::new));

		@Override
		public double get(SpellContext ctx) {
			float hp = Mth.clamp(ctx.healthRatio(), 0, 1);
			return Mth.lerp(hp, atEmpty, atFull);
		}
	}

	/**
	 * Returns phaseTick % period. Useful for periodic firing (e.g., fire every N ticks when result == 0).
	 * JSON: {"type": "tick_mod", "period": 20}
	 */
	public record PhaseTickMod(int period) implements NumberProvider {
		public static final Codec<PhaseTickMod> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("period").forGetter(PhaseTickMod::period)
		).apply(i, PhaseTickMod::new));

		@Override
		public double get(SpellContext ctx) {
			return period > 0 ? ctx.phaseTick() % period : 0;
		}
	}

	/**
	 * Reads a runtime variable value. Returns 0 if not set.
	 * JSON: {"type": "variable", "key": "angle"}
	 */
	public record Variable(String key) implements NumberProvider {
		public static final Codec<Variable> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("key").forGetter(Variable::key)
		).apply(i, Variable::new));

		@Override
		public double get(SpellContext ctx) {
			return ctx.getVariable(key);
		}
	}

	/**
	 * Returns the current phase tick value.
	 * JSON: {"type": "phase_tick"}
	 */
	public record PhaseTick() implements NumberProvider {
		public static final Codec<PhaseTick> CODEC = Codec.unit(PhaseTick::new);

		@Override
		public double get(SpellContext ctx) {
			return ctx.phaseTick();
		}
	}

	/**
	 * Returns the total tick value across all phases.
	 * JSON: {"type": "total_tick"}
	 */
	public record TotalTick() implements NumberProvider {
		public static final Codec<TotalTick> CODEC = Codec.unit(TotalTick::new);

		@Override
		public double get(SpellContext ctx) {
			return ctx.totalTick();
		}
	}

	/**
	 * sin_deg(input + phase) * amplitude.
	 * Input is in degrees.
	 * JSON: {"type": "sin_deg", "input": {"type": "phase_tick"}, "amplitude": 1.0, "phase": 0}
	 */
	public record SinDeg(NumberProvider input, double amplitude, double phase) implements NumberProvider {
		public static final Codec<SinDeg> CODEC = RecordCodecBuilder.create(i -> i.group(
				NumberProvider.CODEC.fieldOf("input").forGetter(SinDeg::input),
				Codec.DOUBLE.optionalFieldOf("amplitude", 1.0).forGetter(SinDeg::amplitude),
				Codec.DOUBLE.optionalFieldOf("phase", 0.0).forGetter(SinDeg::phase)
		).apply(i, SinDeg::new));

		@Override
		public double get(SpellContext ctx) {
			return Math.sin(Math.toRadians(input.get(ctx) + phase)) * amplitude;
		}
	}

	/**
	 * cos_deg(input + phase) * amplitude.
	 * Input is in degrees.
	 * JSON: {"type": "cos_deg", "input": {"type": "phase_tick"}, "amplitude": 1.0, "phase": 0}
	 */
	public record CosDeg(NumberProvider input, double amplitude, double phase) implements NumberProvider {
		public static final Codec<CosDeg> CODEC = RecordCodecBuilder.create(i -> i.group(
				NumberProvider.CODEC.fieldOf("input").forGetter(CosDeg::input),
				Codec.DOUBLE.optionalFieldOf("amplitude", 1.0).forGetter(CosDeg::amplitude),
				Codec.DOUBLE.optionalFieldOf("phase", 0.0).forGetter(CosDeg::phase)
		).apply(i, CosDeg::new));

		@Override
		public double get(SpellContext ctx) {
			return Math.cos(Math.toRadians(input.get(ctx) + phase)) * amplitude;
		}
	}

	/**
	 * sin_rad(input + phase) * amplitude.
	 * Input is in radians.
	 * JSON: {"type": "sin_rad", "input": {"type": "phase_tick"}, "amplitude": 1.0, "phase": 0}
	 */
	public record SinRad(NumberProvider input, double amplitude, double phase) implements NumberProvider {
		public static final Codec<SinRad> CODEC = RecordCodecBuilder.create(i -> i.group(
				NumberProvider.CODEC.fieldOf("input").forGetter(SinRad::input),
				Codec.DOUBLE.optionalFieldOf("amplitude", 1.0).forGetter(SinRad::amplitude),
				Codec.DOUBLE.optionalFieldOf("phase", 0.0).forGetter(SinRad::phase)
		).apply(i, SinRad::new));

		@Override
		public double get(SpellContext ctx) {
			return Math.sin(input.get(ctx) + phase) * amplitude;
		}
	}

	/**
	 * cos_rad(input + phase) * amplitude.
	 * Input is in radians.
	 * JSON: {"type": "cos_rad", "input": {"type": "phase_tick"}, "amplitude": 1.0, "phase": 0}
	 */
	public record CosRad(NumberProvider input, double amplitude, double phase) implements NumberProvider {
		public static final Codec<CosRad> CODEC = RecordCodecBuilder.create(i -> i.group(
				NumberProvider.CODEC.fieldOf("input").forGetter(CosRad::input),
				Codec.DOUBLE.optionalFieldOf("amplitude", 1.0).forGetter(CosRad::amplitude),
				Codec.DOUBLE.optionalFieldOf("phase", 0.0).forGetter(CosRad::phase)
		).apply(i, CosRad::new));

		@Override
		public double get(SpellContext ctx) {
			return Math.cos(input.get(ctx) + phase) * amplitude;
		}
	}

	/**
	 * Sum of two NumberProviders: a + b.
	 * JSON: {"type": "add", "a": 10, "b": {"type": "phase_tick"}}
	 */
	public record Add(NumberProvider a, NumberProvider b) implements NumberProvider {
		public static final Codec<Add> CODEC = RecordCodecBuilder.create(i -> i.group(
				NumberProvider.CODEC.fieldOf("a").forGetter(Add::a),
				NumberProvider.CODEC.fieldOf("b").forGetter(Add::b)
		).apply(i, Add::new));

		@Override
		public double get(SpellContext ctx) {
			return a.get(ctx) + b.get(ctx);
		}
	}

	/**
	 * Product of two NumberProviders: a * b.
	 * JSON: {"type": "mul", "a": 0.5, "b": {"type": "phase_tick"}}
	 */
	public record Mul(NumberProvider a, NumberProvider b) implements NumberProvider {
		public static final Codec<Mul> CODEC = RecordCodecBuilder.create(i -> i.group(
				NumberProvider.CODEC.fieldOf("a").forGetter(Mul::a),
				NumberProvider.CODEC.fieldOf("b").forGetter(Mul::b)
		).apply(i, Mul::new));

		@Override
		public double get(SpellContext ctx) {
			return a.get(ctx) * b.get(ctx);
		}
	}

	/**
	 * Returns the distance from holder.center() to holder.target().
	 * In onExpiry context (TrailCardHolder), this is the distance from the danmaku's expiry position to the target.
	 * JSON: {"type": "distance"}
	 */
	public record Distance() implements NumberProvider {
		public static final Codec<Distance> CODEC = Codec.unit(Distance::new);

		@Override
		public double get(SpellContext ctx) {
			return ctx.distanceToTarget();
		}
	}

	/**
	 * a / b.
	 * JSON: {"type": "div", "a": 10, "b": 2}
	 */
	public record Div(NumberProvider a, NumberProvider b) implements NumberProvider {
		public static final Codec<Div> CODEC = RecordCodecBuilder.create(i -> i.group(
				NumberProvider.CODEC.fieldOf("a").forGetter(Div::a),
				NumberProvider.CODEC.fieldOf("b").forGetter(Div::b)
		).apply(i, Div::new));

		@Override
		public double get(SpellContext ctx) {
			return a.get(ctx) / b.get(ctx);
		}
	}

	/**
	 * a % b (modulo).
	 * JSON: {"type": "mod", "a": 10, "b": 3}
	 */
	public record Mod(NumberProvider a, NumberProvider b) implements NumberProvider {
		public static final Codec<Mod> CODEC = RecordCodecBuilder.create(i -> i.group(
				NumberProvider.CODEC.fieldOf("a").forGetter(Mod::a),
				NumberProvider.CODEC.fieldOf("b").forGetter(Mod::b)
		).apply(i, Mod::new));

		@Override
		public double get(SpellContext ctx) {
			return a.get(ctx) % b.get(ctx);
		}
	}

	/**
	 * Square root of input. Returns 0 for negative inputs.
	 * JSON: {"type": "sqrt", "input": ...}
	 */
	public record Sqrt(NumberProvider input) implements NumberProvider {
		public static final Codec<Sqrt> CODEC = RecordCodecBuilder.create(i -> i.group(
				NumberProvider.CODEC.fieldOf("input").forGetter(Sqrt::input)
		).apply(i, Sqrt::new));

		@Override
		public double get(SpellContext ctx) {
			double v = input.get(ctx);
			return v >= 0 ? Math.sqrt(v) : 0;
		}
	}

	/** abs(input). */
	public record Abs(NumberProvider input) implements NumberProvider {
		public static final Codec<Abs> CODEC = RecordCodecBuilder.create(i -> i.group(
				NumberProvider.CODEC.fieldOf("input").forGetter(Abs::input)
		).apply(i, Abs::new));
		@Override public double get(SpellContext ctx) { return Math.abs(input.get(ctx)); }
	}

	/** floor(input). */
	public record Floor(NumberProvider input) implements NumberProvider {
		public static final Codec<Floor> CODEC = RecordCodecBuilder.create(i -> i.group(
				NumberProvider.CODEC.fieldOf("input").forGetter(Floor::input)
		).apply(i, Floor::new));
		@Override public double get(SpellContext ctx) { return Math.floor(input.get(ctx)); }
	}

	/** ceil(input). */
	public record Ceil(NumberProvider input) implements NumberProvider {
		public static final Codec<Ceil> CODEC = RecordCodecBuilder.create(i -> i.group(
				NumberProvider.CODEC.fieldOf("input").forGetter(Ceil::input)
		).apply(i, Ceil::new));
		@Override public double get(SpellContext ctx) { return Math.ceil(input.get(ctx)); }
	}

	/** round(input), half-to-even. */
	public record Round(NumberProvider input) implements NumberProvider {
		public static final Codec<Round> CODEC = RecordCodecBuilder.create(i -> i.group(
				NumberProvider.CODEC.fieldOf("input").forGetter(Round::input)
		).apply(i, Round::new));
		@Override public double get(SpellContext ctx) { return Math.rint(input.get(ctx)); }
	}

	/** pow(base, exponent). */
	public record Pow(NumberProvider base, NumberProvider exponent) implements NumberProvider {
		public static final Codec<Pow> CODEC = RecordCodecBuilder.create(i -> i.group(
				NumberProvider.CODEC.fieldOf("base").forGetter(Pow::base),
				NumberProvider.CODEC.fieldOf("exponent").forGetter(Pow::exponent)
		).apply(i, Pow::new));
		@Override public double get(SpellContext ctx) { return Math.pow(base.get(ctx), exponent.get(ctx)); }
	}

	/** root(value, degree), returns 0 when degree is zero. */
	public record Root(NumberProvider value, NumberProvider degree) implements NumberProvider {
		public static final Codec<Root> CODEC = RecordCodecBuilder.create(i -> i.group(
				NumberProvider.CODEC.fieldOf("value").forGetter(Root::value),
				NumberProvider.CODEC.fieldOf("degree").forGetter(Root::degree)
		).apply(i, Root::new));
		@Override public double get(SpellContext ctx) {
			double d = degree.get(ctx);
			return d == 0 ? 0 : Math.pow(value.get(ctx), 1.0 / d);
		}
	}

	/** Natural logarithm. */
	public record Log(NumberProvider input) implements NumberProvider {
		public static final Codec<Log> CODEC = RecordCodecBuilder.create(i -> i.group(
				NumberProvider.CODEC.fieldOf("input").forGetter(Log::input)
		).apply(i, Log::new));
		@Override public double get(SpellContext ctx) {
			double v = input.get(ctx);
			return v > 0 ? Math.log(v) : 0;
		}
	}

	/** exp(input). */
	public record Exp(NumberProvider input) implements NumberProvider {
		public static final Codec<Exp> CODEC = RecordCodecBuilder.create(i -> i.group(
				NumberProvider.CODEC.fieldOf("input").forGetter(Exp::input)
		).apply(i, Exp::new));
		@Override public double get(SpellContext ctx) { return Math.exp(input.get(ctx)); }
	}

	/**
	 * Pick a random value from a discrete list.
	 * JSON: {"type": "random_choice", "values": [1, -1]}
	 */
	public record RandomChoice(java.util.List<Double> values) implements NumberProvider {
		public static final Codec<RandomChoice> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.DOUBLE.listOf().fieldOf("values").forGetter(RandomChoice::values)
		).apply(i, RandomChoice::new));

		@Override
		public double get(SpellContext ctx) {
			if (values.isEmpty()) return 0;
			return values.get(ctx.holder().random().nextInt(values.size()));
		}
	}

	/**
	 * Select a value from a list by an index expression.
	 * JSON: {"type": "indexed", "index": {"type": "variable", "key": "i"}, "values": [30, 45, 60]}
	 */
	public record Indexed(NumberProvider index, java.util.List<Double> values) implements NumberProvider {
		public static final Codec<Indexed> CODEC = RecordCodecBuilder.create(i -> i.group(
				NumberProvider.CODEC.fieldOf("index").forGetter(Indexed::index),
				Codec.DOUBLE.listOf().fieldOf("values").forGetter(Indexed::values)
		).apply(i, Indexed::new));

		@Override
		public double get(SpellContext ctx) {
			if (values.isEmpty()) return 0;
			int id = ((int) Math.floor(index.get(ctx))) % values.size();
			if (id < 0) id += values.size();
			return values.get(id);
		}
	}

	/**
	 * Returns ifTrue or ifFalse based on a SpellCondition.
	 * JSON: {"type": "conditional", "condition": {...}, "if_true": 1, "if_false": -1}
	 */
	public record Conditional(
			dev.xkmc.youkaishomecoming.content.spell.condition.SpellCondition condition,
			NumberProvider ifTrue,
			NumberProvider ifFalse
	) implements NumberProvider {
		public static final Codec<Conditional> CODEC = RecordCodecBuilder.create(i -> i.group(
				dev.xkmc.youkaishomecoming.content.spell.condition.SpellCondition.CODEC
						.fieldOf("condition").forGetter(Conditional::condition),
				NumberProvider.CODEC.fieldOf("if_true").forGetter(Conditional::ifTrue),
				NumberProvider.CODEC.optionalFieldOf("if_false", NumberProvider.constant(0))
						.forGetter(Conditional::ifFalse)
		).apply(i, Conditional::new));

		@Override
		public double get(SpellContext ctx) {
			return condition.test(ctx) ? ifTrue.get(ctx) : ifFalse.get(ctx);
		}
	}

	/**
	 * Gaussian (normal) random: mean + random.nextGaussian() * stdDev.
	 * JSON: {"type": "gaussian", "mean": 0, "std_dev": 5}
	 */
	public record GaussianRandom(double mean, double stdDev) implements NumberProvider {
		public static final Codec<GaussianRandom> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.DOUBLE.optionalFieldOf("mean", 0.0).forGetter(GaussianRandom::mean),
				Codec.DOUBLE.fieldOf("std_dev").forGetter(GaussianRandom::stdDev)
		).apply(i, GaussianRandom::new));

		@Override
		public double get(SpellContext ctx) {
			return mean + ctx.holder().random().nextGaussian() * stdDev;
		}
	}

	/** max(a, b). JSON: {"type": "max", "a": ..., "b": ...} */
	public record Max(NumberProvider a, NumberProvider b) implements NumberProvider {
		public static final Codec<Max> CODEC = RecordCodecBuilder.create(i -> i.group(
				NumberProvider.CODEC.fieldOf("a").forGetter(Max::a),
				NumberProvider.CODEC.fieldOf("b").forGetter(Max::b)
		).apply(i, Max::new));

		@Override
		public double get(SpellContext ctx) { return Math.max(a.get(ctx), b.get(ctx)); }
	}

	/** min(a, b). JSON: {"type": "min", "a": ..., "b": ...} */
	public record Min(NumberProvider a, NumberProvider b) implements NumberProvider {
		public static final Codec<Min> CODEC = RecordCodecBuilder.create(i -> i.group(
				NumberProvider.CODEC.fieldOf("a").forGetter(Min::a),
				NumberProvider.CODEC.fieldOf("b").forGetter(Min::b)
		).apply(i, Min::new));

		@Override
		public double get(SpellContext ctx) { return Math.min(a.get(ctx), b.get(ctx)); }
	}

	/** clamp(value, min, max). JSON: {"type": "clamp", "value": ..., "min": 0, "max": 20} */
	public record Clamp(NumberProvider value, NumberProvider min, NumberProvider max) implements NumberProvider {
		public static final Codec<Clamp> CODEC = RecordCodecBuilder.create(i -> i.group(
				NumberProvider.CODEC.fieldOf("value").forGetter(Clamp::value),
				NumberProvider.CODEC.fieldOf("min").forGetter(Clamp::min),
				NumberProvider.CODEC.fieldOf("max").forGetter(Clamp::max)
		).apply(i, Clamp::new));

		@Override
		public double get(SpellContext ctx) {
			return Mth.clamp(value.get(ctx), min.get(ctx), max.get(ctx));
		}
	}

	/** Caster's X position. JSON: {"type": "caster_x"} */
	public record CasterX() implements NumberProvider {
		public static final Codec<CasterX> CODEC = Codec.unit(CasterX::new);
		@Override public double get(SpellContext ctx) { return ctx.holder().center().x; }
	}

	/** Caster's Y position. JSON: {"type": "caster_y"} */
	public record CasterY() implements NumberProvider {
		public static final Codec<CasterY> CODEC = Codec.unit(CasterY::new);
		@Override public double get(SpellContext ctx) { return ctx.holder().center().y; }
	}

	/** Caster's Z position. JSON: {"type": "caster_z"} */
	public record CasterZ() implements NumberProvider {
		public static final Codec<CasterZ> CODEC = Codec.unit(CasterZ::new);
		@Override public double get(SpellContext ctx) { return ctx.holder().center().z; }
	}

	/**
	 * Returns the maximum health of the entity that owns the spell runtime.
	 * JSON: {"type": "caster_max_health"}
	 * Expression keyword: caster_max_health
	 */
	public record CasterMaxHealth() implements NumberProvider {
		public static final Codec<CasterMaxHealth> CODEC = Codec.unit(CasterMaxHealth::new);
		@Override public double get(SpellContext ctx) { return ctx.self().getMaxHealth(); }
	}

	/**
	 * Returns the root player's current STG power level (for example 3.25).
	 * Non-player casters return 0. JSON: {"type": "caster_power"}
	 * Expression keyword: caster_power
	 */
	public record CasterPower() implements NumberProvider {
		public static final Codec<CasterPower> CODEC = Codec.unit(CasterPower::new);
		@Override public double get(SpellContext ctx) { return ctx.holder().casterPower(); }
	}

	/** Target's X position (or caster X if no target). JSON: {"type": "target_x"} */
	public record TargetX() implements NumberProvider {
		public static final Codec<TargetX> CODEC = Codec.unit(TargetX::new);
		@Override public double get(SpellContext ctx) {
			var t = ctx.holder().target();
			return t != null ? t.x : ctx.holder().center().x;
		}
	}

	/** Target's Y position (or caster Y if no target). JSON: {"type": "target_y"} */
	public record TargetY() implements NumberProvider {
		public static final Codec<TargetY> CODEC = Codec.unit(TargetY::new);
		@Override public double get(SpellContext ctx) {
			var t = ctx.holder().target();
			return t != null ? t.y : ctx.holder().center().y;
		}
	}

	/** Target's Z position (or caster Z if no target). JSON: {"type": "target_z"} */
	public record TargetZ() implements NumberProvider {
		public static final Codec<TargetZ> CODEC = Codec.unit(TargetZ::new);
		@Override public double get(SpellContext ctx) {
			var t = ctx.holder().target();
			return t != null ? t.z : ctx.holder().center().z;
		}
	}

	/**
	 * Target's Y position (height). Useful for setting horizontal-plane bullet heights.
	 * Equivalent to target_y but named for clarity in expressions.
	 * JSON: {"type": "target_height"}
	 * Expression keyword: target_height
	 */
	public record TargetHeight() implements NumberProvider {
		public static final Codec<TargetHeight> CODEC = Codec.unit(TargetHeight::new);
		@Override public double get(SpellContext ctx) {
			var t = ctx.holder().target();
			return t != null ? t.y : ctx.holder().center().y;
		}
	}

	/**
	 * Returns the game difficulty as an integer: PEACEFUL=0, EASY=1, NORMAL=2, HARD=3.
	 * Useful for scaling bullet count, speed, etc. by difficulty.
	 * JSON: {"type": "game_difficulty"}
	 * Expression keyword: game_difficulty
	 *
	 * Examples:
	 *   count: 8 + game_difficulty * 4     (EASY=12, NORMAL=16, HARD=20)
	 *   speed: 0.5 + game_difficulty * 0.1 (EASY=0.6, NORMAL=0.7, HARD=0.8)
	 */
	public record GameDifficulty() implements NumberProvider {
		public static final Codec<GameDifficulty> CODEC = Codec.unit(GameDifficulty::new);
		@Override public double get(SpellContext ctx) {
			return ctx.holder().self().level().getDifficulty().getId();
		}
	}

	/**
	 * Returns how many ticks the target has been continuously not on the ground.
	 * Returns 0 if the target is on the ground or absent.
	 * JSON: {"type": "target_fly_time"}
	 */
	public record TargetFlyTime() implements NumberProvider {
		public static final Codec<TargetFlyTime> CODEC = Codec.unit(TargetFlyTime::new);
		@Override public double get(SpellContext ctx) {
			return ctx.targetFlyTime();
		}
	}

	/**
	 * Returns the horizontal speed of the target entity (blocks/tick).
	 * Returns 0 if no target or no velocity data.
	 * JSON: {"type": "target_speed"}
	 */
	public record TargetSpeed() implements NumberProvider {
		public static final Codec<TargetSpeed> CODEC = Codec.unit(TargetSpeed::new);
		@Override public double get(SpellContext ctx) {
			return ctx.targetSpeed();
		}
	}

	public record TargetFacingX() implements NumberProvider {
		public static final Codec<TargetFacingX> CODEC = Codec.unit(TargetFacingX::new);
		@Override public double get(SpellContext ctx) { return ctx.targetFacing().x; }
	}

	public record TargetFacingY() implements NumberProvider {
		public static final Codec<TargetFacingY> CODEC = Codec.unit(TargetFacingY::new);
		@Override public double get(SpellContext ctx) { return ctx.targetFacing().y; }
	}

	public record TargetFacingZ() implements NumberProvider {
		public static final Codec<TargetFacingZ> CODEC = Codec.unit(TargetFacingZ::new);
		@Override public double get(SpellContext ctx) { return ctx.targetFacing().z; }
	}

	/**
	 * Reads a scalar from the immutable projectile callback snapshot. This is
	 * intentionally a single provider with a named key rather than one class per
	 * coordinate, so new callback fields remain codec-compatible and editor
	 * tooling can expose one searchable group.
	 *
	 * JSON: {"type":"callback_value", "key":"source_speed"}
	 */
	public record CallbackValue(String key) implements NumberProvider {
		public static final Codec<CallbackValue> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("key").forGetter(CallbackValue::key)
		).apply(i, CallbackValue::new));

		@Override
		public double get(SpellContext ctx) {
			return ctx.callbackValue(key);
		}
	}

}
