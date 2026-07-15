package dev.xkmc.youkaishomecoming.content.spell.definition;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import net.minecraft.world.item.DyeColor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface ColorProvider {

	DanmakuColor get(SpellContext ctx);

	Map<String, Codec<? extends ColorProvider>> REGISTRY = new HashMap<>();
	Map<Class<?>, String> CLASS_TO_TYPE = new HashMap<>();

	static void register(String id, Codec<? extends ColorProvider> codec, Class<? extends ColorProvider> clazz) {
		REGISTRY.put(id, codec);
		CLASS_TO_TYPE.put(clazz, id);
	}

	@SuppressWarnings("unchecked")
	Codec<ColorProvider> CODEC = new Codec<>() {
		@Override
		public <T> DataResult<Pair<ColorProvider, T>> decode(DynamicOps<T> ops, T input) {
			var strResult = Codec.STRING.decode(ops, input);
			if (strResult.result().isPresent()) {
				String text = strResult.result().get().getFirst();
				var parsed = DanmakuColor.parse(text);
				if (parsed.isPresent()) {
					return DataResult.success(Pair.of(new Constant(parsed.get()), strResult.result().get().getSecond()));
				}
			}
			return ops.getMap(input)
					.flatMap(map -> ops.getStringValue(map.get("type"))
							.flatMap(type -> {
								var codec = REGISTRY.get(type);
								if (codec == null) return DataResult.error(() -> "Unknown ColorProvider type: " + type);
								return ((Codec<ColorProvider>) codec).decode(ops, input);
							}));
		}

		@Override
		public <T> DataResult<T> encode(ColorProvider value, DynamicOps<T> ops, T prefix) {
			if (value instanceof Constant c) {
				return Codec.STRING.encode(c.color.format(), ops, prefix);
			}
			String type = CLASS_TO_TYPE.get(value.getClass());
			if (type == null) return DataResult.error(() -> "Unknown ColorProvider class: " + value.getClass());
			var codec = (Codec<ColorProvider>) REGISTRY.get(type);
			return codec.encode(value, ops, prefix)
					.flatMap(encoded -> ops.mergeToMap(encoded, ops.createString("type"), ops.createString(type)));
		}
	};

	boolean _INIT = _doInit();

	private static boolean _doInit() {
		register("constant", Constant.CODEC, Constant.class);
		register("by_variable", ByVariable.CODEC, ByVariable.class);
		register("indexed", Indexed.CODEC, Indexed.class);
		register("cycle", Cycle.CODEC, Cycle.class);
		register("random_choice", RandomChoice.CODEC, RandomChoice.class);
		return true;
	}

	static ColorProvider constant(DyeColor color) {
		return new Constant(DanmakuColor.of(color));
	}

	static ColorProvider constant(DanmakuColor color) {
		return new Constant(color);
	}

	record Constant(DanmakuColor color) implements ColorProvider {
		public static final Codec<Constant> CODEC = RecordCodecBuilder.create(i -> i.group(
				DanmakuColor.CODEC.fieldOf("color").forGetter(Constant::color)
		).apply(i, Constant::new));

		@Override
		public DanmakuColor get(SpellContext ctx) {
			return color;
		}
	}

	record ByVariable(String key, List<DanmakuColor> palette) implements ColorProvider {
		public static final Codec<ByVariable> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("key").forGetter(ByVariable::key),
				DanmakuColor.CODEC.listOf().fieldOf("palette").forGetter(ByVariable::palette)
		).apply(i, ByVariable::new));

		@Override
		public DanmakuColor get(SpellContext ctx) {
			if (palette.isEmpty()) return DanmakuColor.WHITE;
			int index = ((int) Math.floor(ctx.getVariable(key))) % palette.size();
			if (index < 0) index += palette.size();
			return palette.get(index);
		}
	}

	record Indexed(NumberProvider index, List<DanmakuColor> palette) implements ColorProvider {
		public static final Codec<Indexed> CODEC = RecordCodecBuilder.create(i -> i.group(
				NumberProvider.CODEC.fieldOf("index").forGetter(Indexed::index),
				DanmakuColor.CODEC.listOf().fieldOf("palette").forGetter(Indexed::palette)
		).apply(i, Indexed::new));

		@Override
		public DanmakuColor get(SpellContext ctx) {
			if (palette.isEmpty()) return DanmakuColor.WHITE;
			int id = ((int) Math.floor(index.get(ctx))) % palette.size();
			if (id < 0) id += palette.size();
			return palette.get(id);
		}
	}

	record Cycle(List<DanmakuColor> palette, int interval) implements ColorProvider {
		public static final Codec<Cycle> CODEC = RecordCodecBuilder.create(i -> i.group(
				DanmakuColor.CODEC.listOf().fieldOf("palette").forGetter(Cycle::palette),
				Codec.INT.optionalFieldOf("interval", 1).forGetter(Cycle::interval)
		).apply(i, Cycle::new));

		@Override
		public DanmakuColor get(SpellContext ctx) {
			if (palette.isEmpty()) return DanmakuColor.WHITE;
			int idx = (ctx.phaseTick() / Math.max(1, interval)) % palette.size();
			return palette.get(idx);
		}
	}

	record RandomChoice(List<DanmakuColor> palette) implements ColorProvider {
		public static final Codec<RandomChoice> CODEC = RecordCodecBuilder.create(i -> i.group(
				DanmakuColor.CODEC.listOf().fieldOf("palette").forGetter(RandomChoice::palette)
		).apply(i, RandomChoice::new));

		@Override
		public DanmakuColor get(SpellContext ctx) {
			if (palette.isEmpty()) return DanmakuColor.WHITE;
			return palette.get(ctx.holder().random().nextInt(palette.size()));
		}
	}

}
