package dev.xkmc.youkaishomecoming.content.spell.definition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import net.minecraft.world.item.DyeColor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides a DyeColor that can be dynamically resolved at runtime.
 * Similar to NumberProvider but for colors.
 * <p>
 * Supports a shorthand string form for backward compatibility:
 * "white" → Constant(WHITE).
 */
public interface ColorProvider {

	DyeColor get(SpellContext ctx);

	// --- Registry ---

	Map<String, Codec<? extends ColorProvider>> REGISTRY = new HashMap<>();
	Map<Class<?>, String> CLASS_TO_TYPE = new HashMap<>();

	static void register(String id, Codec<? extends ColorProvider> codec, Class<? extends ColorProvider> clazz) {
		REGISTRY.put(id, codec);
		CLASS_TO_TYPE.put(clazz, id);
	}

	// --- Codec ---

	/**
	 * Dispatch codec: either a string (shorthand for constant) or {"type": "...", ...}.
	 */
	@SuppressWarnings("unchecked")
	Codec<ColorProvider> CODEC = new Codec<>() {
		@Override
		public <T> DataResult<Pair<ColorProvider, T>> decode(DynamicOps<T> ops, T input) {
			// Try string shorthand first
			var strResult = Codec.STRING.decode(ops, input);
			if (strResult.result().isPresent()) {
				String s = strResult.result().get().getFirst();
				try {
					DyeColor c = DyeColor.valueOf(s.toUpperCase());
					return DataResult.success(Pair.of(new Constant(c), strResult.result().get().getSecond()));
				} catch (IllegalArgumentException ignored) {}
			}
			// Fall through to typed dispatch
			return Codec.STRING.fieldOf("type").codec()
					.decode(ops, input)
					.flatMap(typePair -> {
						String type = typePair.getFirst();
						var codec = REGISTRY.get(type);
						if (codec == null) return DataResult.error(() -> "Unknown ColorProvider type: " + type);
						return ((Codec<ColorProvider>) codec).decode(ops, input);
					});
		}

		@Override
		public <T> DataResult<T> encode(ColorProvider value, DynamicOps<T> ops, T prefix) {
			// Shorthand: constant → just the color string
			if (value instanceof Constant c) {
				return Codec.STRING.encode(c.color.name().toLowerCase(), ops, prefix);
			}
			String type = CLASS_TO_TYPE.get(value.getClass());
			if (type == null) return DataResult.error(() -> "Unknown ColorProvider class: " + value.getClass());
			var codec = (Codec<ColorProvider>) REGISTRY.get(type);
			return codec.encode(value, ops, prefix);
		}
	};

	// --- Implementations ---

	// Registration is triggered by class loading of this field
	boolean _INIT = _doInit();

	private static boolean _doInit() {
		register("constant", Constant.CODEC, Constant.class);
		register("by_variable", ByVariable.CODEC, ByVariable.class);
		register("cycle", Cycle.CODEC, Cycle.class);
		register("random_choice", RandomChoice.CODEC, RandomChoice.class);
		return true;
	}

	static ColorProvider constant(DyeColor color) {
		return new Constant(color);
	}

	/** Fixed color (the most common case). */
	record Constant(DyeColor color) implements ColorProvider {
		public static final Codec<Constant> CODEC = SpellCodecs.DYE_COLOR_CODEC
				.fieldOf("color").codec()
				.xmap(Constant::new, Constant::color);

		@Override
		public DyeColor get(SpellContext ctx) {
			return color;
		}
	}

	/**
	 * Select color from a palette by a runtime variable (floor(value) mod palette.size).
	 * Useful with repeat's indexVariable.
	 */
	record ByVariable(String key, List<DyeColor> palette) implements ColorProvider {
		public static final Codec<ByVariable> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("key").forGetter(ByVariable::key),
				SpellCodecs.DYE_COLOR_CODEC.listOf().fieldOf("palette").forGetter(ByVariable::palette)
		).apply(i, ByVariable::new));

		@Override
		public DyeColor get(SpellContext ctx) {
			if (palette.isEmpty()) return DyeColor.WHITE;
			int index = ((int) Math.floor(ctx.getVariable(key))) % palette.size();
			if (index < 0) index += palette.size();
			return palette.get(index);
		}
	}

	/**
	 * Cycle through a palette based on phase tick.
	 * Color changes every 'interval' ticks.
	 */
	record Cycle(List<DyeColor> palette, int interval) implements ColorProvider {
		public static final Codec<Cycle> CODEC = RecordCodecBuilder.create(i -> i.group(
				SpellCodecs.DYE_COLOR_CODEC.listOf().fieldOf("palette").forGetter(Cycle::palette),
				Codec.INT.optionalFieldOf("interval", 1).forGetter(Cycle::interval)
		).apply(i, Cycle::new));

		@Override
		public DyeColor get(SpellContext ctx) {
			if (palette.isEmpty()) return DyeColor.WHITE;
			int idx = (ctx.phaseTick() / Math.max(1, interval)) % palette.size();
			return palette.get(idx);
		}
	}

	/**
	 * Randomly picks one color from the palette each time it's evaluated.
	 * Useful for per-entity random coloring (e.g. each shooter gets a random color).
	 */
	record RandomChoice(List<DyeColor> palette) implements ColorProvider {
		public static final Codec<RandomChoice> CODEC = SpellCodecs.DYE_COLOR_CODEC.listOf()
				.fieldOf("palette").codec()
				.xmap(RandomChoice::new, RandomChoice::palette);

		@Override
		public DyeColor get(SpellContext ctx) {
			if (palette.isEmpty()) return DyeColor.WHITE;
			return palette.get(ctx.holder().random().nextInt(palette.size()));
		}
	}
}
