package gen;

import dev.xkmc.youkaishomecoming.content.spell.analysis.NumberBounds;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberExprParser;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberProvider;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberProviders;

/** Focused numeric checks; does not bootstrap FML or the full spell codec. */
public final class NonSpellPowerBoundsTest {
	private static int checks;

	public static void main(String[] args) {
		NumberProvider adaptive = expression("caster_power + 1");
		for (double power : new double[]{0, 0.25, 0.99, 1, 2.75, 4, 8.5, 14}) {
			bounds("adaptive count at Power " + power, adaptive, NumberBounds.of(power), power + 1, power + 1);
		}
		bounds("recursive arithmetic", expression("2 * (floor(caster_power) + 1)"), NumberBounds.of(2.75), 6, 6);
		bounds("division keeps current Power", expression("(caster_power + 1) / 2"), NumberBounds.of(3.5), 2.25, 2.25);
		bounds("ceil remains explicit", expression("ceil(caster_power) + 1"), NumberBounds.of(2.75), 4, 4);
		bounds("round remains half-to-even", expression("round(caster_power) + 1"), NumberBounds.of(2.5), 3, 3);
		bounds("clamp does not widen a known count", expression("clamp(floor(caster_power) + 1, 1, 10)"),
				NumberBounds.of(2.75), 3, 3);
		bounds("clamp still caps large Power", expression("clamp(caster_power + 1, 1, 5)"),
				NumberBounds.of(8.5), 5, 5);
		bounds("clamp bounds an unknown value", expression("clamp($count, 0, caster_power + 1)"),
				NumberBounds.of(2.75), 0, 3.75);
		bounds("min and max", expression("max(1, min(caster_power, 4))"), NumberBounds.of(2.75), 2.75, 2.75);
		bounds("explicit full Power interval remains conservative", adaptive, NumberBounds.of(0, 14), 1, 15);
		bounds("random counts retain their range", new NumberProviders.Add(new NumberProviders.CasterPower(),
				new NumberProviders.RandomRange(1, 3)), NumberBounds.of(2.75), 3.75, 5.75);
		bounds("clamp cannot narrow a log interval across zero", new NumberProviders.Clamp(
				new NumberProviders.Log(new NumberProviders.RandomRange(0, 1)),
				NumberProvider.constant(-10), NumberProvider.constant(0)), NumberBounds.of(2.75), -10, 0);
		check("unknown variables remain unbounded",
				!NumberBounds.resolve(expression("$count + caster_power"), NumberBounds.of(2.75)).bounded());
		check("possible division by zero remains unbounded",
				!NumberBounds.resolve(expression("1 / caster_power"), NumberBounds.of(0, 4)).bounded());
		check("unknown power functions remain unbounded",
				!NumberBounds.resolve(new NumberProviders.Pow(expression("$count"), NumberProvider.constant(2)),
						NumberBounds.of(2.75)).bounded());
		check("constant-only default resolution needs no game config",
				NumberBounds.resolve(expression("2 + 3")).equals(NumberBounds.of(5)));
		System.out.println("NonSpellPowerBoundsTest: all " + checks + " checks passed");
	}

	private static NumberProvider expression(String expression) {
		NumberProvider provider = NumberExprParser.parse(expression);
		if (provider == null) throw new AssertionError("Invalid test expression: " + expression);
		return provider;
	}

	private static void bounds(String label, NumberProvider provider, NumberBounds power, double min, double max) {
		NumberBounds actual = NumberBounds.resolve(provider, power);
		check(label + ": " + actual, actual.bounded() && actual.min() == min && actual.max() == max);
	}

	private static void check(String label, boolean valid) {
		checks++;
		if (!valid) throw new AssertionError(label);
	}
}
