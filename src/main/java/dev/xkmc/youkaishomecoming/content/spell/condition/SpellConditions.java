package dev.xkmc.youkaishomecoming.content.spell.condition;

import com.mojang.serialization.Codec;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpellConditions {

	private static final Map<String, Codec<? extends SpellCondition>> REGISTRY = new HashMap<>();
	private static final Map<Class<?>, String> CLASS_TO_TYPE = new HashMap<>();

	static {
		register("health_below", HealthBelow.CODEC, HealthBelow.class);
		register("health_above", HealthAbove.CODEC, HealthAbove.class);
		register("tick_elapsed", TickElapsed.CODEC, TickElapsed.class);
		register("distance_above", DistanceAbove.CODEC, DistanceAbove.class);
		register("distance_below", DistanceBelow.CODEC, DistanceBelow.class);
		register("hit_count", HitCountCondition.CODEC, HitCountCondition.class);
		register("and", AndCondition.CODEC, AndCondition.class);
		register("or", OrCondition.CODEC, OrCondition.class);
		register("not", NotCondition.CODEC, NotCondition.class);
		register("variable_check", VariableCheck.CODEC, VariableCheck.class);
		register("always", AlwaysCondition.CODEC, AlwaysCondition.class);
		register("tick_interval", TickInterval.CODEC, TickInterval.class);
	}

	public static void register(String id, Codec<? extends SpellCondition> codec) {
		REGISTRY.put(id, codec);
	}

	public static void register(String id, Codec<? extends SpellCondition> codec, Class<? extends SpellCondition> clazz) {
		REGISTRY.put(id, codec);
		CLASS_TO_TYPE.put(clazz, id);
	}

	private static String getType(SpellCondition condition) {
		String type = CLASS_TO_TYPE.get(condition.getClass());
		if (type != null) return type;
		throw new IllegalStateException("Unknown condition type: " + condition.getClass());
	}

	/**
	 * Returns the registered type ID for the given condition, or null if unknown.
	 */
	public static String getTypeId(SpellCondition condition) {
		return CLASS_TO_TYPE.get(condition.getClass());
	}

	@SuppressWarnings("unchecked")
	static final Codec<SpellCondition> DISPATCH_CODEC = Codec.STRING.fieldOf("type")
			.codec()
			.dispatch(
					SpellConditions::getType,
					id -> {
						var codec = REGISTRY.get(id);
						if (codec == null) throw new IllegalStateException("Unknown condition: " + id);
						return (Codec<SpellCondition>) (Codec<?>) codec;
					}
			);

	// --- Condition implementations ---

	public record HealthBelow(float threshold) implements SpellCondition {
		public static final Codec<HealthBelow> CODEC = Codec.FLOAT
				.fieldOf("threshold").codec().xmap(HealthBelow::new, HealthBelow::threshold);

		@Override
		public boolean test(SpellContext ctx) {
			return ctx.healthRatio() < threshold;
		}
	}

	public record HealthAbove(float threshold) implements SpellCondition {
		public static final Codec<HealthAbove> CODEC = Codec.FLOAT
				.fieldOf("threshold").codec().xmap(HealthAbove::new, HealthAbove::threshold);

		@Override
		public boolean test(SpellContext ctx) {
			return ctx.healthRatio() > threshold;
		}
	}

	public record TickElapsed(int ticks) implements SpellCondition {
		public static final Codec<TickElapsed> CODEC = Codec.INT
				.fieldOf("ticks").codec().xmap(TickElapsed::new, TickElapsed::ticks);

		@Override
		public boolean test(SpellContext ctx) {
			return ctx.phaseTick() >= ticks;
		}
	}

	public record DistanceAbove(double distance) implements SpellCondition {
		public static final Codec<DistanceAbove> CODEC = Codec.DOUBLE
				.fieldOf("distance").codec().xmap(DistanceAbove::new, DistanceAbove::distance);

		@Override
		public boolean test(SpellContext ctx) {
			return ctx.distanceToTarget() > distance;
		}
	}

	public record DistanceBelow(double distance) implements SpellCondition {
		public static final Codec<DistanceBelow> CODEC = Codec.DOUBLE
				.fieldOf("distance").codec().xmap(DistanceBelow::new, DistanceBelow::distance);

		@Override
		public boolean test(SpellContext ctx) {
			return ctx.distanceToTarget() < distance;
		}
	}

	public record HitCountCondition(int count) implements SpellCondition {
		public static final Codec<HitCountCondition> CODEC = Codec.INT
				.fieldOf("count").codec().xmap(HitCountCondition::new, HitCountCondition::count);

		@Override
		public boolean test(SpellContext ctx) {
			return ctx.hitCount() >= count;
		}
	}

	public record AndCondition(List<SpellCondition> conditions) implements SpellCondition {
		public static final Codec<AndCondition> CODEC = SpellCondition.CODEC.listOf()
				.fieldOf("conditions").codec().xmap(AndCondition::new, AndCondition::conditions);

		@Override
		public boolean test(SpellContext ctx) {
			return conditions.stream().allMatch(c -> c.test(ctx));
		}
	}

	public record OrCondition(List<SpellCondition> conditions) implements SpellCondition {
		public static final Codec<OrCondition> CODEC = SpellCondition.CODEC.listOf()
				.fieldOf("conditions").codec().xmap(OrCondition::new, OrCondition::conditions);

		@Override
		public boolean test(SpellContext ctx) {
			return conditions.stream().anyMatch(c -> c.test(ctx));
		}
	}

	public record NotCondition(SpellCondition condition) implements SpellCondition {
		public static final Codec<NotCondition> CODEC = SpellCondition.CODEC
				.fieldOf("condition").codec().xmap(NotCondition::new, NotCondition::condition);

		@Override
		public boolean test(SpellContext ctx) {
			return !condition.test(ctx);
		}
	}

	public record VariableCheck(String key, String op, double value) implements SpellCondition {
		public static final Codec<VariableCheck> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.<VariableCheck>create(i -> i.group(
				Codec.STRING.fieldOf("key").forGetter(VariableCheck::key),
				Codec.STRING.fieldOf("op").forGetter(VariableCheck::op),
				Codec.DOUBLE.fieldOf("value").forGetter(VariableCheck::value)
		).apply(i, VariableCheck::new));

		@Override
		public boolean test(SpellContext ctx) {
			double val = ctx.getVariable(key);
			return switch (op) {
				case "=", "==" -> Math.abs(val - value) < 1e-9;
				case "!=" -> Math.abs(val - value) >= 1e-9;
				case "<" -> val < value;
				case "<=" -> val <= value;
				case ">" -> val > value;
				case ">=" -> val >= value;
				default -> false;
			};
		}
	}

	public record AlwaysCondition(boolean value) implements SpellCondition {
		public static final Codec<AlwaysCondition> CODEC = Codec.BOOL
				.optionalFieldOf("value", true).codec().xmap(AlwaysCondition::new, AlwaysCondition::value);

		@Override
		public boolean test(SpellContext ctx) {
			return value;
		}
	}

	/**
	 * True when phaseTick % interval == offset. Default offset is 0.
	 * Used for periodic firing (e.g., fire every 10 ticks).
	 */
	public record TickInterval(int interval, int offset) implements SpellCondition {
		public static final Codec<TickInterval> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.<TickInterval>create(i -> i.group(
				Codec.INT.fieldOf("interval").forGetter(TickInterval::interval),
				Codec.INT.optionalFieldOf("offset", 0).forGetter(TickInterval::offset)
		).apply(i, TickInterval::new));

		@Override
		public boolean test(SpellContext ctx) {
			return interval > 0 && ctx.phaseTick() % interval == offset;
		}
	}
}
