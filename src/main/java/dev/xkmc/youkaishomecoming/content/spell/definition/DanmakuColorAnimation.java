package dev.xkmc.youkaishomecoming.content.spell.definition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import net.minecraft.util.Mth;

public interface DanmakuColorAnimation {

	Codec<DanmakuColorAnimation> CODEC = Codec.STRING.dispatch(
			"type",
			DanmakuColorAnimation::type,
			type -> switch (type) {
				case "hue_cycle" -> HueCycle.CODEC;
				default -> throw new IllegalStateException("Unknown danmaku color animation: " + type);
			}
	);

	String type();

	Resolved resolve(SpellContext ctx, int spawnIndex);

	static HueCycle hueCycle() {
		return new HueCycle(
				NumberProvider.constant(120),
				NumberProvider.constant(0),
				NumberProvider.constant(0),
				NumberProvider.constant(1),
				NumberProvider.constant(1),
				NumberProvider.constant(1)
		);
	}

	record HueCycle(
			NumberProvider period,
			NumberProvider hueOffset,
			NumberProvider indexStep,
			NumberProvider saturation,
			NumberProvider brightness,
			NumberProvider alpha
	) implements DanmakuColorAnimation {

		public static final Codec<HueCycle> CODEC = RecordCodecBuilder.create(i -> i.group(
				NumberProvider.CODEC.optionalFieldOf("period", NumberProvider.constant(120)).forGetter(HueCycle::period),
				NumberProvider.CODEC.optionalFieldOf("hue_offset", NumberProvider.constant(0)).forGetter(HueCycle::hueOffset),
				NumberProvider.CODEC.optionalFieldOf("index_step", NumberProvider.constant(0)).forGetter(HueCycle::indexStep),
				NumberProvider.CODEC.optionalFieldOf("saturation", NumberProvider.constant(1)).forGetter(HueCycle::saturation),
				NumberProvider.CODEC.optionalFieldOf("brightness", NumberProvider.constant(1)).forGetter(HueCycle::brightness),
				NumberProvider.CODEC.optionalFieldOf("alpha", NumberProvider.constant(1)).forGetter(HueCycle::alpha)
		).apply(i, HueCycle::new));

		@Override
		public String type() {
			return "hue_cycle";
		}

		@Override
		public Resolved resolve(SpellContext ctx, int spawnIndex) {
			float p = Math.max(1.0e-3f, (float) period.get(ctx));
			float offset = (float) hueOffset.get(ctx) + spawnIndex * (float) indexStep.get(ctx);
			float sat = Mth.clamp((float) saturation.get(ctx), 0, 1);
			float bright = Mth.clamp((float) brightness.get(ctx), 0, 1);
			float a = Mth.clamp((float) alpha.get(ctx), 0, 1);
			return new Resolved(Resolved.HUE_CYCLE, p, offset, sat, bright, a);
		}
	}

	record Resolved(int mode, float period, float hueOffset, float saturation, float brightness, float alpha) {
		public static final int NONE = 0;
		public static final int HUE_CYCLE = 1;
	}

}
