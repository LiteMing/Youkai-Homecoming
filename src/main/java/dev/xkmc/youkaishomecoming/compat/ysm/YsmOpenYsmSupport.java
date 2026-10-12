package dev.xkmc.youkaishomecoming.compat.ysm;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Optional dependency gate; only checks a public common API, never OYSM implementation classes. */
public final class YsmOpenYsmSupport {
	private static final boolean AVAILABLE = available(YsmOpenYsmSupport.class.getClassLoader());

	private YsmOpenYsmSupport() { }

	static boolean available() { return AVAILABLE; }

	static boolean available(ClassLoader loader) {
		try {
			Class.forName("rip.ysm.api.ExternalModelPackAPI", false, loader);
			Class.forName("rip.ysm.api.ModelReference", false, loader);
			Class.forName("rip.ysm.api.ExternalPresetAPI", false, loader);
			return true;
		} catch (ClassNotFoundException | LinkageError ignored) {
			return false;
		}
	}

	/** Calls only the public OYSM editor contract; YH never constructs an OYSM screen. */
	public static boolean openEditor(String initialModel) {
		if (!AVAILABLE) return false;
		try {
			Class<?> api = Class.forName("rip.ysm.api.client.ExternalModelEditorAPI", false, YsmOpenYsmSupport.class.getClassLoader());
			Method open = api.getMethod("open", String.class);
			return Boolean.TRUE.equals(open.invoke(null, initialModel == null ? "" : initialModel));
		} catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException | LinkageError ignored) {
			return false;
		}
	}
}
