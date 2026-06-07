package dev.xkmc.youkaishomecoming.content.spell.definition;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public class EntityNumberProviderEvaluator {

	public static double get(NumberProvider provider, int tick, double fallback, RandomSource random) {
		if (provider instanceof NumberProviders.Constant p) return p.value();
		if (provider instanceof NumberProviders.LerpOverTime p) {
			double t = p.duration() > 0 ? Mth.clamp((double) tick / p.duration(), 0, 1) : 1;
			return Mth.lerp(t, p.start(), p.end());
		}
		if (provider instanceof NumberProviders.PhaseTick) return tick;
		if (provider instanceof NumberProviders.TotalTick) return tick;
		if (provider instanceof NumberProviders.PhaseTickMod p) return p.period() > 0 ? tick % p.period() : 0;
		if (provider instanceof NumberProviders.Sin p) return Math.sin(Math.toRadians(get(p.input(), tick, fallback, random) + p.phase())) * p.amplitude();
		if (provider instanceof NumberProviders.Cos p) return Math.cos(Math.toRadians(get(p.input(), tick, fallback, random) + p.phase())) * p.amplitude();
		if (provider instanceof NumberProviders.Add p) return get(p.a(), tick, fallback, random) + get(p.b(), tick, fallback, random);
		if (provider instanceof NumberProviders.Mul p) return get(p.a(), tick, fallback, random) * get(p.b(), tick, fallback, random);
		if (provider instanceof NumberProviders.Div p) return get(p.a(), tick, fallback, random) / get(p.b(), tick, fallback, random);
		if (provider instanceof NumberProviders.Mod p) return get(p.a(), tick, fallback, random) % get(p.b(), tick, fallback, random);
		if (provider instanceof NumberProviders.Sqrt p) {
			double v = get(p.input(), tick, fallback, random);
			return v >= 0 ? Math.sqrt(v) : 0;
		}
		if (provider instanceof NumberProviders.Max p) return Math.max(get(p.a(), tick, fallback, random), get(p.b(), tick, fallback, random));
		if (provider instanceof NumberProviders.Min p) return Math.min(get(p.a(), tick, fallback, random), get(p.b(), tick, fallback, random));
		if (provider instanceof NumberProviders.Clamp p) return Mth.clamp(get(p.value(), tick, fallback, random), get(p.min(), tick, fallback, random), get(p.max(), tick, fallback, random));
		if (provider instanceof NumberProviders.RandomRange p) return p.min() + random.nextDouble() * (p.max() - p.min());
		if (provider instanceof NumberProviders.RandomChoice p) return p.values().isEmpty() ? 0 : p.values().get(random.nextInt(p.values().size()));
		if (provider instanceof NumberProviders.GaussianRandom p) return p.mean() + random.nextGaussian() * p.stdDev();
		return fallback;
	}

}
