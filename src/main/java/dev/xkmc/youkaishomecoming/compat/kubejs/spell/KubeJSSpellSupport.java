package dev.xkmc.youkaishomecoming.compat.kubejs.spell;

import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.util.JSObjectType;
import dev.latvian.mods.kubejs.util.UtilsJS;
import dev.latvian.mods.rhino.BaseFunction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellAction;
import dev.xkmc.youkaishomecoming.content.spell.condition.SpellCondition;
import dev.xkmc.youkaishomecoming.content.spell.definition.TransitionMode;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class KubeJSSpellSupport {

	@FunctionalInterface
	interface SpellPhaseBuilderCallback {
		void accept(SpellPhaseBuilder builder);
	}

	@FunctionalInterface
	interface SpellActionCallback {
		void accept(SpellContext ctx);
	}

	@FunctionalInterface
	interface SpellConditionCallback {
		boolean test(SpellContext ctx);
	}

	private KubeJSSpellSupport() {
	}

	static ResourceLocation parseId(String id) {
		return new ResourceLocation(id);
	}

	static ResourceLocation parsePhaseId(ResourceLocation spellId, String id) {
		if (id.indexOf(':') >= 0) {
			return new ResourceLocation(id);
		}
		return new ResourceLocation(spellId.getNamespace(), spellId.getPath() + "/" + id);
	}

	@Nullable
	static ResourceLocation parseNullableId(@Nullable Object value) {
		if (value == null) {
			return null;
		}
		String id = value.toString();
		return id.isBlank() ? null : new ResourceLocation(id);
	}

	static SpellAction toAction(Object value) {
		if (value instanceof SpellAction action) {
			return action;
		}
		if (value instanceof BaseFunction function) {
			SpellActionCallback callback = UtilsJS.makeFunctionProxy(ScriptType.STARTUP, SpellActionCallback.class, function);
			return new JSAction(callback);
		}
		throw new IllegalArgumentException("Unsupported spell action: " + value);
	}

	static SpellCondition toCondition(Object value) {
		if (value instanceof SpellCondition condition) {
			return condition;
		}
		if (value instanceof BaseFunction function) {
			SpellConditionCallback callback = UtilsJS.makeFunctionProxy(ScriptType.STARTUP, SpellConditionCallback.class, function);
			return new JSCondition(callback);
		}
		throw new IllegalArgumentException("Unsupported spell condition: " + value);
	}

	static List<SpellAction> toActionList(@Nullable Object value) {
		List<SpellAction> actions = new ArrayList<>();
		appendActions(actions, value);
		return actions;
	}

	static void appendActions(List<SpellAction> actions, @Nullable Object value) {
		if (value == null) {
			return;
		}
		if (value instanceof Iterable<?> iterable) {
			for (Object obj : iterable) {
				appendActions(actions, obj);
			}
			return;
		}
		if (value.getClass().isArray()) {
			int len = java.lang.reflect.Array.getLength(value);
			for (int i = 0; i < len; i++) {
				appendActions(actions, java.lang.reflect.Array.get(value, i));
			}
			return;
		}
		actions.add(toAction(value));
	}

	static List<SpellCondition> toConditionList(Object... values) {
		List<SpellCondition> conditions = new ArrayList<>();
		for (Object value : values) {
			if (value == null) continue;
			if (value instanceof Iterable<?> iterable) {
				for (Object obj : iterable) {
					conditions.add(toCondition(obj));
				}
			} else if (value.getClass().isArray()) {
				int len = java.lang.reflect.Array.getLength(value);
				for (int i = 0; i < len; i++) {
					conditions.add(toCondition(java.lang.reflect.Array.get(value, i)));
				}
			} else {
				conditions.add(toCondition(value));
			}
		}
		return conditions;
	}

	static TransitionMode toMode(@Nullable Object value) {
		if (value == null) {
			return TransitionMode.IMMEDIATE;
		}
		if (value instanceof TransitionMode mode) {
			return mode;
		}
		return TransitionMode.valueOf(value.toString().trim().toUpperCase(Locale.ROOT));
	}

	@SuppressWarnings("unchecked")
	static Map<String, Object> unwrapMap(Object value) {
		Object wrapped = UtilsJS.wrap(value, JSObjectType.MAP);
		if (wrapped instanceof Map<?, ?> map) {
			return (Map<String, Object>) map;
		}
		throw new IllegalArgumentException("Expected object map, got: " + value);
	}

	static boolean getBoolean(Map<String, Object> map, String camel, String snake, boolean def) {
		Object value = map.containsKey(camel) ? map.get(camel) : map.get(snake);
		if (value == null) {
			return def;
		}
		if (value instanceof Boolean bool) {
			return bool;
		}
		return Boolean.parseBoolean(value.toString());
	}

	static int getInt(Map<String, Object> map, String camel, String snake, int def) {
		Object value = map.containsKey(camel) ? map.get(camel) : map.get(snake);
		if (value instanceof Number num) {
			return num.intValue();
		}
		if (value == null) {
			return def;
		}
		return Integer.parseInt(value.toString());
	}

	record JSAction(SpellActionCallback callback) implements SpellAction {
		@Override
		public void execute(SpellContext ctx) {
			callback.accept(ctx);
		}
	}

	record JSCondition(SpellConditionCallback callback) implements SpellCondition {
		@Override
		public boolean test(SpellContext ctx) {
			return callback.test(ctx);
		}
	}
}
