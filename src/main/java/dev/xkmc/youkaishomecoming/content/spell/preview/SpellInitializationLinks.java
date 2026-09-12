package dev.xkmc.youkaishomecoming.content.spell.preview;

import dev.xkmc.youkaishomecoming.content.spell.action.SetInvulnerableAction;
import dev.xkmc.youkaishomecoming.content.spell.action.FreezeOnTickAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SetSpellHealthAction;
import dev.xkmc.youkaishomecoming.content.spell.action.ShowSpellCardAction;
import dev.xkmc.youkaishomecoming.content.spell.action.ShowSpellTitleAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellActions;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntFunction;
import java.util.function.Supplier;

/** Editor links to the adjacent declaration actions; the runtime still executes ordinary siblings. */
final class SpellInitializationLinks {

	private SpellInitializationLinks() {}

	enum Kind {
		INVULNERABILITY("set_invulnerable", "invulnerable"),
		FREEZE("freeze_on_tick", "freeze_on_tick"),
		TITLE("show_spell_title", "title"),
		CARD("show_spell_card", "card");

		final String actionType;
		final String labelKey;

		Kind(String actionType, String labelKey) {
			this.actionType = actionType;
			this.labelKey = labelKey;
		}
	}

	static boolean isInitializer(@Nullable SpellAction action) {
		return action instanceof SetSpellHealthAction init && init.mode() == SetSpellHealthAction.Mode.SET;
	}

	@Nullable
	static SpellAction unwrap(@Nullable SpellAction action) {
		while (action instanceof SpellActions.DisabledAction disabled) action = disabled.inner();
		return action;
	}

	@Nullable
	static Kind kindOf(@Nullable SpellAction action) {
		action = unwrap(action);
		if (action instanceof SetInvulnerableAction) return Kind.INVULNERABILITY;
		if (action instanceof FreezeOnTickAction) return Kind.FREEZE;
		if (action instanceof ShowSpellTitleAction) return Kind.TITLE;
		if (action instanceof ShowSpellCardAction) return Kind.CARD;
		return null;
	}

	static boolean isEnabled(@Nullable SpellAction action) {
		return action != null && !(action instanceof SpellActions.DisabledAction);
	}

	@Nullable
	static SpellAction withEnabled(@Nullable SpellAction action, boolean enabled, Supplier<SpellAction> defaults) {
		if (action == null) return enabled ? defaults.get() : null;
		if (enabled) return unwrap(action);
		return isEnabled(action) ? new SpellActions.DisabledAction(action) : action;
	}

	/** The accessor returns null outside this sibling list. Never cross a wait, branch, or next initializer. */
	static int findIndex(IntFunction<SpellAction> siblings, int initIndex, Kind kind) {
		if (!isInitializer(siblings.apply(initIndex))) return -1;
		for (int index = initIndex + 1; ; index++) {
			Kind candidate = kindOf(siblings.apply(index));
			if (candidate == null) return -1;
			if (candidate == kind) return index;
		}
	}

	/** New links use protection → title → card order without rearranging existing authored actions. */
	static int insertionIndex(IntFunction<SpellAction> siblings, int initIndex, Kind kind) {
		if (!isInitializer(siblings.apply(initIndex))) return -1;
		int index = initIndex + 1;
		for (; ; index++) {
			Kind candidate = kindOf(siblings.apply(index));
			if (candidate == null || candidate.ordinal() >= kind.ordinal()) return index;
		}
	}
}
