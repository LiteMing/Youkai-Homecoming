package gen;

import dev.xkmc.youkaishomecoming.content.spell.action.PatternEmitter;
import dev.xkmc.youkaishomecoming.content.spell.definition.AimMode;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberProvider;
import dev.xkmc.youkaishomecoming.content.spell.definition.PatternType;
import dev.xkmc.youkaishomecoming.content.spell.difficulty.DifficultyModifiers;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/** Exercises the shared bullet/shooter emitter without entities, a world, or FML. */
public final class PatternEmitterCountTest {

	private static int checks;

	public static void main(String[] args) {
		for (PatternType pattern : PatternType.values()) {
			for (double count : new double[]{0, -2, 0.99}) {
				expect("empty " + pattern + " count=" + count,
						emitted(pattern, count, Optional.of(3.0), DifficultyModifiers.DEFAULT), 0);
			}
			expect("difficulty cannot revive an empty " + pattern,
					emitted(pattern, 0, Optional.of(3.0), new DifficultyModifiers(1, 1, 4)), 0);
		}
		for (PatternType pattern : new PatternType[]{PatternType.NESTED_RING, PatternType.GRID}) {
			for (double outer : new double[]{0, -2, 0.99}) {
				expect("empty outer dimension " + pattern + " outer=" + outer,
						emitted(pattern, 3, Optional.of(outer), DifficultyModifiers.DEFAULT), 0);
			}
		}
		for (PatternType pattern : new PatternType[]{PatternType.RING, PatternType.LINE,
				PatternType.AIMED, PatternType.SPHERE, PatternType.SPIRAL, PatternType.CONE}) {
			expect("positive count unchanged " + pattern,
					emitted(pattern, 3, Optional.empty(), DifficultyModifiers.DEFAULT), 3);
		}
		expect("grid defaults to count squared",
				emitted(PatternType.GRID, 3, Optional.empty(), DifficultyModifiers.DEFAULT), 9);
		expect("explicit grid dimensions",
				emitted(PatternType.GRID, 3, Optional.of(2.0), DifficultyModifiers.DEFAULT), 6);
		expect("explicit nested ring dimensions",
				emitted(PatternType.NESTED_RING, 3, Optional.of(2.0), DifficultyModifiers.DEFAULT), 6);
		expect("nested ring without outer count",
				emitted(PatternType.NESTED_RING, 3, Optional.empty(), DifficultyModifiers.DEFAULT), 3);
		expect("fractional positive counts still truncate",
				emitted(PatternType.RING, 1.99, Optional.empty(), DifficultyModifiers.DEFAULT), 1);
		expect("positive count retains its difficulty minimum",
				emitted(PatternType.RING, 1, Optional.empty(), new DifficultyModifiers(1, 1, 0.1f)), 1);
		expect("positive difficulty scaling still rounds",
				emitted(PatternType.RING, 3, Optional.empty(), new DifficultyModifiers(1, 1, 1.5f)), 5);
		expect("zero and one actions use one spawn in total",
				emitted(PatternType.RING, 0, Optional.empty(), DifficultyModifiers.DEFAULT)
						+ emitted(PatternType.RING, 1, Optional.empty(), DifficultyModifiers.DEFAULT), 1);
		expect("negative difficulty cannot revive a negative count",
				emitted(PatternType.RING, -2, Optional.empty(), new DifficultyModifiers(1, 1, -2)), 0);
		checkEmptyEmissionSkipsOtherProviders();
		System.out.println("PatternEmitterCountTest: all " + checks + " checks passed");
	}

	private static int emitted(PatternType pattern, double count, Optional<Double> outer,
			DifficultyModifiers difficulty) {
		var settings = settings(pattern, count, outer, NumberProvider.constant(2));
		int[] emitted = {0};
		PatternEmitter.emit(new SpellContext(null, null, null, difficulty), Vec3.ZERO, settings,
				(velocity, direction, index, spread) -> {
					if (index != emitted[0]++) throw new AssertionError("Non-contiguous spawn index");
					if (!Double.isFinite(velocity.lengthSqr())) throw new AssertionError("Invalid velocity");
				});
		return emitted[0];
	}

	private static void checkEmptyEmissionSkipsOtherProviders() {
		NumberProvider unexpected = ctx -> { throw new AssertionError("Evaluated an empty emission's speed"); };
		var settings = settings(PatternType.RING, 0, Optional.empty(), unexpected);
		int[] emitted = {0};
		PatternEmitter.emit(new SpellContext(null, null, null, DifficultyModifiers.DEFAULT), Vec3.ZERO,
				settings, (velocity, direction, index, spread) -> emitted[0]++);
		expect("empty emissions skip direction and speed work", emitted[0], 0);
	}

	private static PatternEmitter.Settings settings(PatternType pattern, double count,
			Optional<Double> outer, NumberProvider speed) {
		return new PatternEmitter.Settings(NumberProvider.constant(count), speed, NumberProvider.constant(0),
				NumberProvider.constant(36), NumberProvider.constant(0), pattern,
				new AimMode.AimModes.FixedDirection(new Vec3(0, 0, 1)), NumberProvider.constant(0),
				outer.map(NumberProvider::constant), Optional.empty(), Optional.empty(), false);
	}

	private static void expect(String label, int actual, int expected) {
		checks++;
		if (actual != expected) throw new AssertionError(label + ": expected " + expected + ", got " + actual);
	}
}
