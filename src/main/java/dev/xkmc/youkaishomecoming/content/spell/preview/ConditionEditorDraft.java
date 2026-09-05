package dev.xkmc.youkaishomecoming.content.spell.preview;

import dev.xkmc.youkaishomecoming.content.spell.condition.SpellCondition;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.UnaryOperator;

/** Keeps condition field callbacks based on the latest edited value between UI rebuilds. */
final class ConditionEditorDraft {

	private SpellCondition current;
	private final BiConsumer<SpellCondition, Boolean> onChanged;

	ConditionEditorDraft(SpellCondition initial, BiConsumer<SpellCondition, Boolean> onChanged) {
		this.current = Objects.requireNonNull(initial);
		this.onChanged = Objects.requireNonNull(onChanged);
	}

	void replace(SpellCondition condition, boolean rebuild) {
		current = Objects.requireNonNull(condition);
		onChanged.accept(current, rebuild);
	}

	void update(UnaryOperator<SpellCondition> modifier, boolean rebuild) {
		replace(modifier.apply(current), rebuild);
	}

	SpellCondition current() {
		return current;
	}
}
