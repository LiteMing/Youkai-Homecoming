package dev.xkmc.youkaishomecoming.content.spell.preview;

import dev.xkmc.youkaishomecoming.content.spell.condition.SpellConditions;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberProvider;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberProviders;

import java.util.ArrayList;
import java.util.List;

public final class ConditionEditorDraftTest {

	private ConditionEditorDraftTest() {
	}

	public static int runAllTests() {
		List<Boolean> rebuilds = new ArrayList<>();
		ConditionEditorDraft draft = new ConditionEditorDraft(
				new SpellConditions.CompareNumbers(NumberProvider.constant(1), "<", NumberProvider.constant(100)),
				(condition, rebuild) -> rebuilds.add(rebuild));

		draft.update(current -> {
			var latest = (SpellConditions.CompareNumbers) current;
			return new SpellConditions.CompareNumbers(NumberProvider.constant(42), latest.op(), latest.right());
		}, false);
		draft.update(current -> {
			var latest = (SpellConditions.CompareNumbers) current;
			return new SpellConditions.CompareNumbers(latest.left(), latest.op(), NumberProvider.constant(7));
		}, false);
		draft.update(current -> {
			var latest = (SpellConditions.CompareNumbers) current;
			return new SpellConditions.CompareNumbers(latest.left(), ">=", latest.right());
		}, true);

		var result = (SpellConditions.CompareNumbers) draft.current();
		check("CONDITION_REBUILD_PRESERVES_COMPARE_LEFT", constant(result.left()) == 42);
		check("CONDITION_REBUILD_PRESERVES_COMPARE_RIGHT", constant(result.right()) == 7);
		check("CONDITION_REBUILD_PRESERVES_COMPARE_OPERATOR", result.op().equals(">="));
		check("CONDITION_OPERATOR_CHANGE_REQUESTS_REBUILD",
				rebuilds.equals(List.of(false, false, true)));
		return 4;
	}

	private static double constant(NumberProvider provider) {
		return ((NumberProviders.Constant) provider).value();
	}

	private static void check(String name, boolean condition) {
		if (!condition) throw new AssertionError(name);
		System.out.println("PASS  " + name);
	}
}
