package dev.xkmc.youkaishomecoming.content.spell.editor;

import dev.xkmc.youkaishomecoming.content.spell.action.SpellAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellActions;
import net.minecraft.resources.ResourceLocation;

public enum EditorActionType {
	NOOP("Noop", true) {
		@Override
		public SpellAction create(ResourceLocation selectedPhase) {
			return new SpellActions.NoopAction();
		}

		@Override
		public boolean matches(SpellAction action) {
			return action instanceof SpellActions.NoopAction;
		}
	},
	SET_VARIABLE("Set Variable", true) {
		@Override
		public SpellAction create(ResourceLocation selectedPhase) {
			return new SpellActions.SetVariable("var", 0);
		}

		@Override
		public boolean matches(SpellAction action) {
			return action instanceof SpellActions.SetVariable;
		}
	},
	ADD_VARIABLE("Add Variable", true) {
		@Override
		public SpellAction create(ResourceLocation selectedPhase) {
			return new SpellActions.AddVariable("var", 1);
		}

		@Override
		public boolean matches(SpellAction action) {
			return action instanceof SpellActions.AddVariable;
		}
	},
	CLEAR_SCREEN("Clear Screen", true) {
		@Override
		public SpellAction create(ResourceLocation selectedPhase) {
			return new SpellActions.ClearScreen();
		}

		@Override
		public boolean matches(SpellAction action) {
			return action instanceof SpellActions.ClearScreen;
		}
	},
	FORCE_PHASE("Force Phase", true) {
		@Override
		public SpellAction create(ResourceLocation selectedPhase) {
			return new SpellActions.ForcePhase(selectedPhase);
		}

		@Override
		public boolean matches(SpellAction action) {
			return action instanceof SpellActions.ForcePhase;
		}
	},
	PLAY_SOUND("Play Sound", true) {
		@Override
		public SpellAction create(ResourceLocation selectedPhase) {
			return new SpellActions.PlaySoundAction(new ResourceLocation("minecraft", "entity.arrow.hit_player"), 1.0f, 1.0f);
		}

		@Override
		public boolean matches(SpellAction action) {
			return action instanceof SpellActions.PlaySoundAction;
		}
	},
	CONDITIONAL("Conditional", false) {
		@Override
		public SpellAction create(ResourceLocation selectedPhase) {
			return new SpellActions.ConditionalAction(
					EditorConditionType.ALWAYS.create(),
					java.util.List.of(new SpellActions.NoopAction()),
					java.util.List.of()
			);
		}

		@Override
		public boolean matches(SpellAction action) {
			return action instanceof SpellActions.ConditionalAction;
		}
	},
	SEQUENCE("Sequence", false) {
		@Override
		public SpellAction create(ResourceLocation selectedPhase) {
			return new SpellActions.SequenceAction(java.util.List.of(new SpellActions.NoopAction()));
		}

		@Override
		public boolean matches(SpellAction action) {
			return action instanceof SpellActions.SequenceAction;
		}
	},
	LEGACY_TICKER("Legacy Ticker", false) {
		@Override
		public SpellAction create(ResourceLocation selectedPhase) {
			throw new IllegalStateException("Legacy ticker actions cannot be created from the editor");
		}

		@Override
		public boolean matches(SpellAction action) {
			return action instanceof dev.xkmc.youkaishomecoming.content.spell.action.LegacyTickerAction;
		}
	};

	private final String label;
	private final boolean fieldEditable;

	EditorActionType(String label, boolean fieldEditable) {
		this.label = label;
		this.fieldEditable = fieldEditable;
	}

	public String label() {
		return label;
	}

	public boolean fieldEditable() {
		return fieldEditable;
	}

	public abstract SpellAction create(ResourceLocation selectedPhase);

	public abstract boolean matches(SpellAction action);

	public EditorActionType next() {
		EditorActionType[] values = values();
		return values[(ordinal() + 1) % values.length];
	}

	public static EditorActionType fromAction(SpellAction action) {
		for (EditorActionType type : values()) {
			if (type.matches(action)) {
				return type;
			}
		}
		return NOOP;
	}
}
