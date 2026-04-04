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
		register("sin", Sin.CODEC, Sin.class);
		register("cos", Cos.CODEC, Cos.class);
		register("add", Add.CODEC, Add.class);
		register("mul", Mul.CODEC, Mul.class);
		register("distance", Distance.CODEC, Distance.class);
		register("div", Div.CODEC, Div.class);
		register("mod", Mod.CODEC, Mod.class);
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
	private static final Codec<NumberProvider> DISPATCH_CODEC = Codec.STRING.fieldOf("type")
			.codec()
			.dispatch(
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
	 * sin(input + phase) * amplitude.
	 * Input is in degrees.
	 * JSON: {"type": "sin", "input": {"type": "phase_tick"}, "amplitude": 1.0, "phase": 0}
	 */
	public record Sin(NumberProvider input, double amplitude, double phase) implements NumberProvider {
		public static final Codec<Sin> CODEC = RecordCodecBuilder.create(i -> i.group(
				NumberProvider.CODEC.fieldOf("input").forGetter(Sin::input),
				Codec.DOUBLE.optionalFieldOf("amplitude", 1.0).forGetter(Sin::amplitude),
				Codec.DOUBLE.optionalFieldOf("phase", 0.0).forGetter(Sin::phase)
		).apply(i, Sin::new));

		@Override
		public double get(SpellContext ctx) {
			return Math.sin(Math.toRadians(input.get(ctx) + phase)) * amplitude;
		}
	}

	/**
	 * cos(input + phase) * amplitude.
	 * Input is in degrees.
	 * JSON: {"type": "cos", "input": {"type": "phase_tick"}, "amplitude": 1.0, "phase": 0}
	 */
	public record Cos(NumberProvider input, double amplitude, double phase) implements NumberProvider {
		public static final Codec<Cos> CODEC = RecordCodecBuilder.create(i -> i.group(
				NumberProvider.CODEC.fieldOf("input").forGetter(Cos::input),
				Codec.DOUBLE.optionalFieldOf("amplitude", 1.0).forGetter(Cos::amplitude),
				Codec.DOUBLE.optionalFieldOf("phase", 0.0).forGetter(Cos::phase)
		).apply(i, Cos::new));

		@Override
		public double get(SpellContext ctx) {
			return Math.cos(Math.toRadians(input.get(ctx) + phase)) * amplitude;
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

}
