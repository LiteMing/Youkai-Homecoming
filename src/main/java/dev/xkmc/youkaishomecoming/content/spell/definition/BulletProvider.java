package dev.xkmc.youkaishomecoming.content.spell.definition;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface BulletProvider {

	YHDanmaku.Bullet get(SpellContext ctx);

	Map<String, Codec<? extends BulletProvider>> REGISTRY = new HashMap<>();
	Map<Class<?>, String> CLASS_TO_TYPE = new HashMap<>();

	static void register(String id, Codec<? extends BulletProvider> codec, Class<? extends BulletProvider> clazz) {
		REGISTRY.put(id, codec);
		CLASS_TO_TYPE.put(clazz, id);
	}

	@SuppressWarnings("unchecked")
	Codec<BulletProvider> CODEC = new Codec<>() {
		@Override
		public <T> DataResult<Pair<BulletProvider, T>> decode(DynamicOps<T> ops, T input) {
			var bulletResult = SpellCodecs.BULLET_CODEC.decode(ops, input);
			if (bulletResult.result().isPresent()) {
				var pair = bulletResult.result().get();
				return DataResult.success(Pair.of(new Constant(pair.getFirst()), pair.getSecond()));
			}
			return ops.getMap(input)
					.flatMap(map -> ops.getStringValue(map.get("type"))
							.flatMap(type -> {
						var codec = REGISTRY.get(type);
						if (codec == null) return DataResult.error(() -> "Unknown BulletProvider type: " + type);
						return ((Codec<BulletProvider>) codec).decode(ops, input);
					}));
		}

		@Override
		public <T> DataResult<T> encode(BulletProvider value, DynamicOps<T> ops, T prefix) {
			if (value instanceof Constant c) {
				return SpellCodecs.BULLET_CODEC.encode(c.bullet, ops, prefix);
			}
			String type = CLASS_TO_TYPE.get(value.getClass());
			if (type == null) return DataResult.error(() -> "Unknown BulletProvider class: " + value.getClass());
			var codec = (Codec<BulletProvider>) REGISTRY.get(type);
			return codec.encode(value, ops, prefix)
					.flatMap(encoded -> ops.mergeToMap(encoded, ops.createString("type"), ops.createString(type)));
		}
	};

	boolean _INIT = _doInit();

	private static boolean _doInit() {
		register("constant", Constant.CODEC, Constant.class);
		register("indexed", Indexed.CODEC, Indexed.class);
		register("random_choice", RandomChoice.CODEC, RandomChoice.class);
		return true;
	}

	static BulletProvider constant(YHDanmaku.Bullet bullet) {
		return new Constant(bullet);
	}

	record Constant(YHDanmaku.Bullet bullet) implements BulletProvider {
		public static final Codec<Constant> CODEC = RecordCodecBuilder.create(i -> i.group(
				SpellCodecs.BULLET_CODEC.fieldOf("bullet").forGetter(Constant::bullet)
		).apply(i, Constant::new));

		@Override
		public YHDanmaku.Bullet get(SpellContext ctx) {
			return bullet;
		}
	}

	record Indexed(NumberProvider index, List<YHDanmaku.Bullet> palette) implements BulletProvider {
		public static final Codec<Indexed> CODEC = RecordCodecBuilder.create(i -> i.group(
				NumberProvider.CODEC.fieldOf("index").forGetter(Indexed::index),
				SpellCodecs.BULLET_CODEC.listOf().fieldOf("palette").forGetter(Indexed::palette)
		).apply(i, Indexed::new));

		@Override
		public YHDanmaku.Bullet get(SpellContext ctx) {
			if (palette.isEmpty()) return YHDanmaku.Bullet.CIRCLE;
			int id = ((int) Math.floor(index.get(ctx))) % palette.size();
			if (id < 0) id += palette.size();
			return palette.get(id);
		}
	}

	record RandomChoice(List<YHDanmaku.Bullet> palette) implements BulletProvider {
		public static final Codec<RandomChoice> CODEC = RecordCodecBuilder.create(i -> i.group(
				SpellCodecs.BULLET_CODEC.listOf().fieldOf("palette").forGetter(RandomChoice::palette)
		).apply(i, RandomChoice::new));

		@Override
		public YHDanmaku.Bullet get(SpellContext ctx) {
			if (palette.isEmpty()) return YHDanmaku.Bullet.CIRCLE;
			return palette.get(ctx.holder().random().nextInt(palette.size()));
		}
	}

}
