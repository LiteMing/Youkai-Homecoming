package dev.xkmc.youkaishomecoming.compat.kubejs.spell;

import dev.xkmc.youkaishomecoming.content.spell.condition.SpellCondition;
import dev.xkmc.youkaishomecoming.content.spell.condition.SpellConditions;

public class KubeJSSpellConditions {

	public SpellCondition healthBelow(double threshold) {
		return new SpellConditions.HealthBelow((float) threshold);
	}

	public SpellCondition healthAbove(double threshold) {
		return new SpellConditions.HealthAbove((float) threshold);
	}

	public SpellCondition tickElapsed(int ticks) {
		return new SpellConditions.TickElapsed(ticks);
	}

	public SpellCondition distanceBelow(double distance) {
		return new SpellConditions.DistanceBelow(distance);
	}

	public SpellCondition distanceAbove(double distance) {
		return new SpellConditions.DistanceAbove(distance);
	}

	public SpellCondition hitCount(int count) {
		return new SpellConditions.HitCountCondition(count);
	}

	public SpellCondition variableCheck(String key, String op, double value) {
		return new SpellConditions.VariableCheck(key, op, value);
	}

	public SpellCondition always() {
		return new SpellConditions.AlwaysCondition(true);
	}

	public SpellCondition never() {
		return new SpellConditions.AlwaysCondition(false);
	}

	public SpellCondition not(Object condition) {
		return new SpellConditions.NotCondition(KubeJSSpellSupport.toCondition(condition));
	}

	public SpellCondition and(Object... conditions) {
		return new SpellConditions.AndCondition(KubeJSSpellSupport.toConditionList(conditions));
	}

	public SpellCondition or(Object... conditions) {
		return new SpellConditions.OrCondition(KubeJSSpellSupport.toConditionList(conditions));
	}
}
