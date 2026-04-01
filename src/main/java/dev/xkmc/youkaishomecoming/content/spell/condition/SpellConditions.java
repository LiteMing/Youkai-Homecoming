package dev.xkmc.youkaishomecoming.content.spell.condition;

import com.mojang.serialization.Codec;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpellConditions {

	private static final Map<String, Codec<? extends SpellCondition>> REGISTRY = new HashMap<>();

	static {
		register("health_below", HealthBelow.CODEC);
		register("health_above", HealthAbove.CODEC);
		register("tick_elapsed", TickElapsed.CODEC);
		register("distance_above", DistanceAbove.CODEC);
		register("distance_below", DistanceBelow.CODEC);
		register("hit_count", HitCountCondition.CODEC);
		register("and", AndCondition.CODEC);
		register("or", OrCondition.CODEC);
		register("not", NotCondition.CODEC);
		register("variable_check", VariableCheck.CODEC);
		register("always", AlwaysCondition.CODEC);
	}

	public static void register(String id, Codec<? extends SpellCondition> codec) {
		REGISTRY.put(id, codec);
	}

	private static String getType(SpellCondition condition) {
		if (condition instanceof HealthBelow) return "health_below";
		if (condition instanceof HealthAbove) return "health_above";
		if (condition instanceof TickElapsed) return "tick_elapsed";
		if (condition instanceof DistanceAbove) return "distance_above";
		if (condition instanceof DistanceBelow) return "distance_below";
		if (condition instanceof HitCountCondition) return "hit_count";
		if (condition instanceof AndCondition) return "and";
		if (condition instanceof OrCondition) return "or";
		if (condition instanceof NotCondition) return "not";
		if (condition instanceof VariableCheck) return "variable_check";
		if (condition instanceof AlwaysCondition) return "always";
		throw new IllegalStateException("Unknown condition type: " + condition.getClass());
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
				case "=", "==" -> val == value;
				case "!=" -> val != value;
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
}
