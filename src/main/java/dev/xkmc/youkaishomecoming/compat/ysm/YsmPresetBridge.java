package dev.xkmc.youkaishomecoming.compat.ysm;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only adapter for the OYSM-owned preset registry. */
final class YsmPresetBridge {
	/** A value view only; YH does not own or persist a second preset definition. */
	record Preset(String id, String model, String description, String clip, int ticks, Map<String, Float> parameters) {
		Preset {
			parameters = Map.copyOf(parameters == null ? Map.of() : parameters);
		}
	}
	private static final Class<?> API = load("rip.ysm.api.ExternalPresetAPI");
	private static final Method FIND = method("find", String.class, String.class);
	private static final Method PRESETS = method("presets", String.class);
	private static final Method BINDING = method("binding", String.class, String.class);

	private YsmPresetBridge() { }

	static boolean available() { return API != null && FIND != null && PRESETS != null; }

	static String binding(String model, String key) {
		Object value = invoke(BINDING, model, key);
		return value instanceof String id ? id : "";
	}

	static Preset find(String model, String id) {
		Object value = invoke(FIND, model, id);
		return value == null ? null : convert(value);
	}

	static Map<String, Preset> presets(String model) {
		Map<String, Preset> result = new LinkedHashMap<>();
		Object value = invoke(PRESETS, model);
		if (!(value instanceof List<?> list)) return result;
		for (Object item : list) {
			if (item == null) continue;
			try {
				java.lang.reflect.Method id = item.getClass().getMethod("id");
				result.put(id.invoke(item).toString(), convert(item));
			} catch (ReflectiveOperationException ignored) { }
		}
		return result;
	}

	private static Preset convert(Object value) {
		try {
			Class<?> type = value.getClass();
			String description = type.getMethod("description").invoke(value).toString();
			String clip = type.getMethod("clip").invoke(value).toString();
			int ticks = ((Number) type.getMethod("ticks").invoke(value)).intValue();
			@SuppressWarnings("unchecked") Map<String, Float> parameters = (Map<String, Float>) type.getMethod("parameters").invoke(value);
			String id = type.getMethod("id").invoke(value).toString();
			String model = type.getMethod("model").invoke(value).toString();
			return new Preset(id, model, description, clip, ticks, parameters);
		} catch (ReflectiveOperationException | ClassCastException ex) {
			throw new IllegalArgumentException("Invalid OYSM preset response", ex);
		}
	}

	private static Class<?> load(String name) {
		try { return Class.forName(name, false, YsmPresetBridge.class.getClassLoader()); }
		catch (ClassNotFoundException | LinkageError ignored) { return null; }
	}

	private static Method method(String name, Class<?>... types) {
		if (API == null) return null;
		try { return API.getMethod(name, types); }
		catch (NoSuchMethodException ignored) { return null; }
	}

	private static Object invoke(Method method, Object... args) {
		if (method == null) return null;
		try { return method.invoke(null, args); }
		catch (IllegalAccessException | InvocationTargetException ignored) { return null; }
	}
}
