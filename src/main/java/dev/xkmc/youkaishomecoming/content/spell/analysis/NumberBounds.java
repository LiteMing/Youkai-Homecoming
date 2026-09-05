package dev.xkmc.youkaishomecoming.content.spell.analysis;

import dev.xkmc.youkaishomecoming.content.capability.GrazeHelper;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberProvider;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberProviders;

import java.util.List;

/**
 * Conservative numeric bounds for a NumberProvider.
 * {@code UNBOUNDED} marks providers whose value cannot be statically bounded
 * (variables, phase/total tick, distances, positions, gaussian random, ...).
 * <p>
 * Resolution rules (design doc §10 "不可静态求界的表达式"):
 * <ul>
 *   <li>constants / random ranges / lerp / health ratio / tick_mod / choices are bounded;</li>
 *   <li>sin/cos are bounded by their amplitude regardless of input;</li>
 *   <li>arithmetic is bounded iff all operands are bounded (division/modulo reject
 *   denominators that may cross zero);</li>
 *   <li>clamp is bounded iff its min/max are bounded (value may be unbounded);</li>
 *   <li>indexed selects from its value list regardless of index;</li>
 *   <li>game_difficulty is bounded to [0, 3], caster_power to the configured player cap;</li>
 *   <li>variable / phase_tick / total_tick / distance / positions / target stats and
 *   gaussian are unbounded.</li>
 * </ul>
 */
public record NumberBounds(double min, double max) {

	public static final NumberBounds UNBOUNDED =
			new NumberBounds(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);

	public boolean bounded() {
		return min != Double.NEGATIVE_INFINITY && max != Double.POSITIVE_INFINITY
				&& !Double.isNaN(min) && !Double.isNaN(max);
	}

	public static NumberBounds of(double value) {
		return new NumberBounds(value, value);
	}

	public static NumberBounds of(double min, double max) {
		return new NumberBounds(min, Math.max(min, max));
	}

	/**
	 * Resolve conservative bounds for a provider, or UNBOUNDED.
	 */
	public static NumberBounds resolve(NumberProvider provider) {
		if (provider instanceof NumberProviders.Constant c) return of(c.value());
		if (provider instanceof NumberProviders.RandomRange r) return of(Math.min(r.min(), r.max()), Math.max(r.min(), r.max()));
		if (provider instanceof NumberProviders.LerpOverTime l) return of(Math.min(l.start(), l.end()), Math.max(l.start(), l.end()));
		if (provider instanceof NumberProviders.ByHealthRatio h) return of(Math.min(h.atFull(), h.atEmpty()), Math.max(h.atFull(), h.atEmpty()));
		if (provider instanceof NumberProviders.PhaseTickMod t) return t.period() > 0 ? of(0, t.period() - 1) : of(0);
		if (provider instanceof NumberProviders.RandomChoice choice) return boundsOfList(choice.values());
		if (provider instanceof NumberProviders.Indexed idx) return boundsOfList(idx.values());
		if (provider instanceof NumberProviders.GameDifficulty) return of(0, 3);
		if (provider instanceof NumberProviders.CasterPower) return of(0, GrazeHelper.getMaximumPowerLevel());
		if (provider instanceof NumberProviders.SinDeg s) return of(-Math.abs(s.amplitude()), Math.abs(s.amplitude()));
		if (provider instanceof NumberProviders.CosDeg c) return of(-Math.abs(c.amplitude()), Math.abs(c.amplitude()));
		if (provider instanceof NumberProviders.SinRad s) return of(-Math.abs(s.amplitude()), Math.abs(s.amplitude()));
		if (provider instanceof NumberProviders.CosRad c) return of(-Math.abs(c.amplitude()), Math.abs(c.amplitude()));
		if (provider instanceof NumberProviders.Add a) return combine(resolve(a.a()), resolve(a.b()), (x, y) -> x + y);
		if (provider instanceof NumberProviders.Mul m) return combine(resolve(m.a()), resolve(m.b()), (x, y) -> x * y);
		if (provider instanceof NumberProviders.Div d) {
			NumberBounds b = resolve(d.b());
			if (!b.bounded() || b.min() <= 0 && b.max() >= 0) return UNBOUNDED;
			NumberBounds a = resolve(d.a());
			if (!a.bounded()) return UNBOUNDED;
			return combine(a, b, (x, y) -> x / y);
		}
		if (provider instanceof NumberProviders.Mod m) {
			NumberBounds a = resolve(m.a());
			NumberBounds b = resolve(m.b());
			if (!a.bounded() || !b.bounded() || b.min() <= 0 && b.max() >= 0) return UNBOUNDED;
			double abs = Math.max(Math.abs(b.min()), Math.abs(b.max()));
			return of(-abs, abs);
		}
		if (provider instanceof NumberProviders.Sqrt s) {
			NumberBounds a = resolve(s.input());
			if (!a.bounded()) return UNBOUNDED;
			double hi = Math.sqrt(Math.max(0, a.max()));
			return of(0, hi);
		}
		if (provider instanceof NumberProviders.Abs a) {
			NumberBounds i = resolve(a.input());
			if (!i.bounded()) return UNBOUNDED;
			double abs = Math.max(Math.abs(i.min()), Math.abs(i.max()));
			return of(0, abs);
		}
		if (provider instanceof NumberProviders.Floor f) return monotone(f.input(), Math::floor);
		if (provider instanceof NumberProviders.Ceil c) return monotone(c.input(), Math::ceil);
		if (provider instanceof NumberProviders.Round r) return monotone(r.input(), Math::rint);
		if (provider instanceof NumberProviders.Log l) {
			NumberBounds i = resolve(l.input());
			if (!i.bounded()) return UNBOUNDED;
			if (i.max() <= 0) return of(0);
			double hi = Math.log(i.max());
			double lo = i.min() > 0 ? Math.min(0, Math.log(i.min())) : 0;
			return of(Math.min(0, lo), Math.max(0, hi));
		}
		if (provider instanceof NumberProviders.Exp e) {
			NumberBounds i = resolve(e.input());
			if (!i.bounded()) return UNBOUNDED;
			double lo = Math.exp(Math.min(0, i.min()));
			double hi = Math.exp(Math.max(0, i.max()));
			return of(lo, hi);
		}
		// pow/root corner sampling is not a valid interval operation (e.g. base [-2,2],
		// exponent 2 only samples 4, missing 0); fail open to UNBOUNDED rather than
		// returning a wrong lower bound (acceptance review issue 9)
		if (provider instanceof NumberProviders.Pow) return UNBOUNDED;
		if (provider instanceof NumberProviders.Root) return UNBOUNDED;
		if (provider instanceof NumberProviders.Max m) {
			NumberBounds a = resolve(m.a());
			NumberBounds b = resolve(m.b());
			if (!a.bounded() || !b.bounded()) return UNBOUNDED;
			return of(Math.max(a.min(), b.min()), Math.max(a.max(), b.max()));
		}
		if (provider instanceof NumberProviders.Min m) {
			NumberBounds a = resolve(m.a());
			NumberBounds b = resolve(m.b());
			if (!a.bounded() || !b.bounded()) return UNBOUNDED;
			return of(Math.min(a.min(), b.min()), Math.min(a.max(), b.max()));
		}
		if (provider instanceof NumberProviders.Clamp c) {
			NumberBounds lo = resolve(c.min());
			NumberBounds hi = resolve(c.max());
			if (!lo.bounded() || !hi.bounded() || lo.max() > hi.min()) return UNBOUNDED;
			return of(lo.min(), hi.max());
		}
		if (provider instanceof NumberProviders.Conditional cond) {
			NumberBounds a = resolve(cond.ifTrue());
			NumberBounds b = resolve(cond.ifFalse());
			if (!a.bounded() || !b.bounded()) return UNBOUNDED;
			return of(Math.min(a.min(), b.min()), Math.max(a.max(), b.max()));
		}
		return UNBOUNDED;
	}

	private interface BinOp {
		double apply(double a, double b);
	}

	private static NumberBounds combine(NumberBounds a, NumberBounds b, BinOp op) {
		if (!a.bounded() || !b.bounded()) return UNBOUNDED;
		double[] corners = {
				op.apply(a.min(), b.min()),
				op.apply(a.min(), b.max()),
				op.apply(a.max(), b.min()),
				op.apply(a.max(), b.max())
		};
		double lo = corners[0], hi = corners[0];
		for (int i = 1; i < 4; i++) {
			if (Double.isNaN(corners[i])) return UNBOUNDED;
			lo = Math.min(lo, corners[i]);
			hi = Math.max(hi, corners[i]);
		}
		return of(lo, hi);
	}

	private static NumberBounds monotone(NumberProvider input, java.util.function.DoubleUnaryOperator fn) {
		NumberBounds i = resolve(input);
		if (!i.bounded()) return UNBOUNDED;
		double lo = fn.applyAsDouble(i.min());
		double hi = fn.applyAsDouble(i.max());
		if (Double.isNaN(lo) || Double.isNaN(hi)) return UNBOUNDED;
		return of(Math.min(lo, hi), Math.max(lo, hi));
	}

	private static NumberBounds boundsOfList(List<Double> values) {
		if (values == null || values.isEmpty()) return of(0);
		double lo = values.get(0), hi = values.get(0);
		for (double v : values) {
			lo = Math.min(lo, v);
			hi = Math.max(hi, v);
		}
		return of(lo, hi);
	}
}
