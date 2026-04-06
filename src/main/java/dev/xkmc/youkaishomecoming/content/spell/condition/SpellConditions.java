package dev.xkmc.youkaishomecoming.content.spell.condition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.content.entity.boss.BossYoukaiEntity;
import dev.xkmc.youkaishomecoming.content.entity.fairy.ClownEntity;
import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberProvider;
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
		register("target_on_ground", TargetOnGround.CODEC, TargetOnGround.class);
		register("target_speed", TargetSpeed.CODEC, TargetSpeed.class);
		register("random_chance", RandomChance.CODEC, RandomChance.class);
		register("entity_trait", EntityTrait.CODEC, EntityTrait.class);
		register("dynamic_tick_interval", DynamicTickInterval.CODEC, DynamicTickInterval.class);
		register("compare", CompareNumbers.CODEC, CompareNumbers.class);
		register("target_health_below", TargetHealthBelow.CODEC, TargetHealthBelow.class);
		register("target_health_above", TargetHealthAbove.CODEC, TargetHealthAbove.class);
		register("target_is_flying", TargetIsFlying.CODEC, TargetIsFlying.class);
		register("target_is_fallflying", TargetIsFallFlying.CODEC, TargetIsFallFlying.class);
		register("difficulty_equals", DifficultyEquals.CODEC, DifficultyEquals.class);
		register("difficulty_above", DifficultyAbove.CODEC, DifficultyAbove.class);
		register("entity_flag", EntityFlagCondition.CODEC, EntityFlagCondition.class);
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

	/**
	 * True when the target entity is on the ground.
	 * Useful for ground-slam or gravity-related spell patterns.
	 */
	public record TargetOnGround() implements SpellCondition {
		public static final Codec<TargetOnGround> CODEC = Codec.unit(TargetOnGround::new);

		@Override
		public boolean test(SpellContext ctx) {
			return ctx.targetOnGround();
		}
	}

	/**
	 * True when the target's horizontal speed is above the given threshold.
	 * Use with NOT to check if the target is slow/stationary.
	 */
	public record TargetSpeed(double threshold, String op) implements SpellCondition {
		public static final Codec<TargetSpeed> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.<TargetSpeed>create(i -> i.group(
				Codec.DOUBLE.fieldOf("threshold").forGetter(TargetSpeed::threshold),
				Codec.STRING.optionalFieldOf("op", ">").forGetter(TargetSpeed::op)
		).apply(i, TargetSpeed::new));

		@Override
		public boolean test(SpellContext ctx) {
			double speed = ctx.targetSpeed();
			return switch (op) {
				case ">" -> speed > threshold;
				case ">=" -> speed >= threshold;
				case "<" -> speed < threshold;
				case "<=" -> speed <= threshold;
				default -> speed > threshold;
			};
		}
	}

	/**
	 * True with the given probability each time it is evaluated.
	 * probability should be between 0.0 (never) and 1.0 (always).
	 */
	public record RandomChance(float probability) implements SpellCondition {
		public static final Codec<RandomChance> CODEC = Codec.FLOAT
				.fieldOf("probability").codec().xmap(RandomChance::new, RandomChance::probability);

		@Override
		public boolean test(SpellContext ctx) {
			return ctx.holder().random().nextFloat() < probability;
		}
	}

	/**
	 * Checks a named boolean trait on the caster entity.
	 * <p>
	 * Supported traits:
	 * <ul>
	 *   <li>{@code "is_lunatic"} — ClownEntity.isLunatic()</li>
	 *   <li>{@code "is_chaotic"} — BossYoukaiEntity.isChaotic()</li>
	 * </ul>
	 * Returns false if the caster is not the expected entity type.
	 */
	public record EntityTrait(String trait) implements SpellCondition {
		public static final Codec<EntityTrait> CODEC = Codec.STRING
				.fieldOf("trait").codec().xmap(EntityTrait::new, EntityTrait::trait);

		@Override
		public boolean test(SpellContext ctx) {
			var self = ctx.self();
			return switch (trait) {
				case "is_lunatic" -> self instanceof ClownEntity c && c.isLunatic();
				case "is_chaotic" -> self instanceof BossYoukaiEntity b && b.isChaotic();
				case "is_abyssal" -> self instanceof YoukaiEntity y && y.getFlag(4);
				default -> false;
			};
		}
	}

	/**
	 * Like TickInterval but with NumberProvider period and offset.
	 * True when {@code floor(phaseTick) % floor(period) == floor(offset)}.
	 * Allows difficulty-dependent timing (e.g. period=$dur).
	 */
	public record DynamicTickInterval(NumberProvider period, NumberProvider offset) implements SpellCondition {
		public static final Codec<DynamicTickInterval> CODEC = RecordCodecBuilder.<DynamicTickInterval>create(i -> i.group(
				NumberProvider.CODEC.fieldOf("period").forGetter(DynamicTickInterval::period),
				NumberProvider.CODEC.optionalFieldOf("offset", NumberProvider.constant(0)).forGetter(DynamicTickInterval::offset)
		).apply(i, DynamicTickInterval::new));

		@Override
		public boolean test(SpellContext ctx) {
			int p = (int) period.get(ctx);
			int o = (int) offset.get(ctx);
			return p > 0 && ctx.phaseTick() % p == o;
		}
	}

	/**
	 * Compares two NumberProvider values with a given operator.
	 * JSON: {"type": "compare", "left": ..., "op": "<", "right": 40}
	 */
	public record CompareNumbers(NumberProvider left, String op, NumberProvider right) implements SpellCondition {
		public static final Codec<CompareNumbers> CODEC = RecordCodecBuilder.<CompareNumbers>create(i -> i.group(
				NumberProvider.CODEC.fieldOf("left").forGetter(CompareNumbers::left),
				Codec.STRING.fieldOf("op").forGetter(CompareNumbers::op),
				NumberProvider.CODEC.fieldOf("right").forGetter(CompareNumbers::right)
		).apply(i, CompareNumbers::new));

		@Override
		public boolean test(SpellContext ctx) {
			double l = left.get(ctx);
			double r = right.get(ctx);
			return switch (op) {
				case "<" -> l < r;
				case "<=" -> l <= r;
				case ">" -> l > r;
				case ">=" -> l >= r;
				case "=", "==" -> Math.abs(l - r) < 1e-9;
				case "!=" -> Math.abs(l - r) >= 1e-9;
				default -> false;
			};
		}
	}

	/**
	 * True when the target entity's health ratio is below the threshold.
	 * JSON: {"type": "target_health_below", "threshold": 0.5}
	 */
	public record TargetHealthBelow(float threshold) implements SpellCondition {
		public static final Codec<TargetHealthBelow> CODEC = Codec.FLOAT
				.fieldOf("threshold").codec().xmap(TargetHealthBelow::new, TargetHealthBelow::threshold);

		@Override
		public boolean test(SpellContext ctx) {
			return ctx.targetHealthRatio() < threshold;
		}
	}

	/**
	 * True when the target entity's health ratio is above the threshold.
	 * JSON: {"type": "target_health_above", "threshold": 0.5}
	 */
	public record TargetHealthAbove(float threshold) implements SpellCondition {
		public static final Codec<TargetHealthAbove> CODEC = Codec.FLOAT
				.fieldOf("threshold").codec().xmap(TargetHealthAbove::new, TargetHealthAbove::threshold);

		@Override
		public boolean test(SpellContext ctx) {
			return ctx.targetHealthRatio() > threshold;
		}
	}

	/**
	 * True when the target entity is flying (creative flight or similar).
	 * In preview mode this is controlled by the target properties panel.
	 * JSON: {"type": "target_is_flying"}
	 */
	public record TargetIsFlying() implements SpellCondition {
		public static final Codec<TargetIsFlying> CODEC = Codec.unit(TargetIsFlying::new);

		@Override
		public boolean test(SpellContext ctx) {
			return ctx.targetIsFlying();
		}
	}

	/**
	 * True when the target entity is elytra gliding (fall flying).
	 * In preview mode this is controlled by the target properties panel.
	 * JSON: {"type": "target_is_fallflying"}
	 */
	public record TargetIsFallFlying() implements SpellCondition {
		public static final Codec<TargetIsFallFlying> CODEC = Codec.unit(TargetIsFallFlying::new);

		@Override
		public boolean test(SpellContext ctx) {
			return ctx.targetIsFallFlying();
		}
	}

	/**
	 * True when the game difficulty matches exactly.
	 * PEACEFUL=0, EASY=1, NORMAL=2, HARD=3.
	 * JSON: {"type": "difficulty_equals", "difficulty": 3}
	 */
	public record DifficultyEquals(int difficultyId) implements SpellCondition {
		public static final Codec<DifficultyEquals> CODEC = Codec.INT
				.fieldOf("difficulty").codec().xmap(DifficultyEquals::new, DifficultyEquals::difficultyId);

		@Override
		public boolean test(SpellContext ctx) {
			return ctx.holder().self().level().getDifficulty().getId() == difficultyId;
		}
	}

	/**
	 * True when the game difficulty is at or above the given level.
	 * PEACEFUL=0, EASY=1, NORMAL=2, HARD=3.
	 * JSON: {"type": "difficulty_above", "min_difficulty": 2}
	 */
	public record DifficultyAbove(int minDifficultyId) implements SpellCondition {
		public static final Codec<DifficultyAbove> CODEC = Codec.INT
				.fieldOf("min_difficulty").codec().xmap(DifficultyAbove::new, DifficultyAbove::minDifficultyId);

		@Override
		public boolean test(SpellContext ctx) {
			return ctx.holder().self().level().getDifficulty().getId() >= minDifficultyId;
		}
	}

	/**
	 * Checks a specific entity flag on the caster ({@link YoukaiEntity}).
	 * <p>
	 * Entity flags are bitfield values controlling visual states and behavior:
	 * <ul>
	 *   <li>Flag 4: Abyssal mode (Reimu)</li>
	 *   <li>Flag 16: Feed cooldown</li>
	 *   <li>Flag 32: Boss rage</li>
	 * </ul>
	 * Returns false if the caster is not a YoukaiEntity.
	 * <p>
	 * JSON: {@code {"type": "entity_flag", "flag": 4}}
	 */
	public record EntityFlagCondition(int flag) implements SpellCondition {
		public static final Codec<EntityFlagCondition> CODEC = Codec.INT
				.fieldOf("flag").codec().xmap(EntityFlagCondition::new, EntityFlagCondition::flag);

		@Override
		public boolean test(SpellContext ctx) {
			if (ctx.self() instanceof YoukaiEntity youkai) {
				return youkai.getFlag(flag);
			}
			return false;
		}
	}
}
