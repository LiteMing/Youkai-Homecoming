package dev.xkmc.youkaishomecoming.compat.ysm;

/** Optional dependency gate; only checks a public common API, never OYSM implementation classes. */
final class YsmOpenYsmSupport {
	private static final boolean AVAILABLE = available(YsmOpenYsmSupport.class.getClassLoader());

	private YsmOpenYsmSupport() { }

	static boolean available() { return AVAILABLE; }

	static boolean available(ClassLoader loader) {
		try {
			Class.forName("rip.ysm.api.ExternalModelPackAPI", false, loader);
			return true;
		} catch (ClassNotFoundException | LinkageError ignored) {
			return false;
		}
	}
}
