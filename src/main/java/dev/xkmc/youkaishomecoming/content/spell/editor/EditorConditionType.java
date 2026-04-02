package dev.xkmc.youkaishomecoming.content.spell.editor;

import dev.xkmc.youkaishomecoming.content.spell.condition.SpellCondition;
import dev.xkmc.youkaishomecoming.content.spell.condition.SpellConditions;

public enum EditorConditionType {
	ALWAYS("Always", true) {
		@Override
		public SpellCondition create() {
			return new SpellConditions.AlwaysCondition(true);
		}

		@Override
		public boolean matches(SpellCondition condition) {
			return condition instanceof SpellConditions.AlwaysCondition;
		}
	},
	HEALTH_BELOW("Health <", true) {
		@Override
		public SpellCondition create() {
			return new SpellConditions.HealthBelow(0.5f);
		}

		@Override
		public boolean matches(SpellCondition condition) {
			return condition instanceof SpellConditions.HealthBelow;
		}
	},
	HEALTH_ABOVE("Health >", true) {
		@Override
		public SpellCondition create() {
			return new SpellConditions.HealthAbove(0.5f);
		}

		@Override
		public boolean matches(SpellCondition condition) {
			return condition instanceof SpellConditions.HealthAbove;
		}
	},
	TICK_ELAPSED("Tick Elapsed", true) {
		@Override
		public SpellCondition create() {
			return new SpellConditions.TickElapsed(100);
		}

		@Override
		public boolean matches(SpellCondition condition) {
			return condition instanceof SpellConditions.TickElapsed;
		}
	},
	DISTANCE_ABOVE("Distance >", true) {
		@Override
		public SpellCondition create() {
			return new SpellConditions.DistanceAbove(8);
		}

		@Override
		public boolean matches(SpellCondition condition) {
			return condition instanceof SpellConditions.DistanceAbove;
		}
	},
	DISTANCE_BELOW("Distance <", true) {
		@Override
		public SpellCondition create() {
			return new SpellConditions.DistanceBelow(8);
		}

		@Override
		public boolean matches(SpellCondition condition) {
			return condition instanceof SpellConditions.DistanceBelow;
		}
	},
	HIT_COUNT("Hit Count", true) {
		@Override
		public SpellCondition create() {
			return new SpellConditions.HitCountCondition(1);
		}

		@Override
		public boolean matches(SpellCondition condition) {
			return condition instanceof SpellConditions.HitCountCondition;
		}
	},
	VARIABLE_CHECK("Variable Check", true) {
		@Override
		public SpellCondition create() {
			return new SpellConditions.VariableCheck("var", ">=", 1);
		}

		@Override
		public boolean matches(SpellCondition condition) {
			return condition instanceof SpellConditions.VariableCheck;
		}
	},
	NOT("Not", false) {
		@Override
		public SpellCondition create() {
			return new SpellConditions.NotCondition(ALWAYS.create());
		}

		@Override
		public boolean matches(SpellCondition condition) {
			return condition instanceof SpellConditions.NotCondition;
		}
	},
	AND("And", false) {
		@Override
		public SpellCondition create() {
			return new SpellConditions.AndCondition(java.util.List.of(ALWAYS.create()));
		}

		@Override
		public boolean matches(SpellCondition condition) {
			return condition instanceof SpellConditions.AndCondition;
		}
	},
	OR("Or", false) {
		@Override
		public SpellCondition create() {
			return new SpellConditions.OrCondition(java.util.List.of(ALWAYS.create()));
		}

		@Override
		public boolean matches(SpellCondition condition) {
			return condition instanceof SpellConditions.OrCondition;
		}
	};

	private final String label;
	private final boolean fieldEditable;

	EditorConditionType(String label, boolean fieldEditable) {
		this.label = label;
		this.fieldEditable = fieldEditable;
	}

	public String label() {
		return label;
	}

	public boolean fieldEditable() {
		return fieldEditable;
	}

	public abstract SpellCondition create();

	public abstract boolean matches(SpellCondition condition);

	public EditorConditionType next() {
		EditorConditionType[] values = values();
		return values[(ordinal() + 1) % values.length];
	}

	public static EditorConditionType fromCondition(SpellCondition condition) {
		for (EditorConditionType type : values()) {
			if (type.matches(condition)) {
				return type;
			}
		}
		return ALWAYS;
	}
}
