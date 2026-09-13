package dev.xkmc.youkaishomecoming.content.spell;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Arrays;

/** Run real spell Codecs without starting Forge's mod-registration entry point. */
public final class SpellTestBootstrap {
	private SpellTestBootstrap() {}

	public static boolean enter(Class<?> test, String[] args) throws Exception {
		if (Arrays.asList(args).contains("--headless")) return false;
		var urls = Arrays.stream(System.getProperty("java.class.path").split(java.io.File.pathSeparator)).map(path -> {
			try { return Path.of(path).toUri().toURL(); }
			catch (Exception error) { throw new IllegalArgumentException(error); }
		}).toArray(java.net.URL[]::new);
		try (var loader = new URLClassLoader(urls, test.getClassLoader()) {
			@Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
				if (!name.startsWith("dev.xkmc.")) return super.loadClass(name, resolve);
				synchronized (getClassLoadingLock(name)) {
					Class<?> type = findLoadedClass(name);
					if (type == null) type = findClass(name);
					if (resolve) resolveClass(type);
					return type;
				}
			}
			@Override protected Class<?> findClass(String name) throws ClassNotFoundException {
				if (!name.equals("dev.xkmc.youkaishomecoming.init.YoukaisHomecoming")) return super.findClass(name);
				try (var input = getResourceAsStream(name.replace('.', '/') + ".class")) {
					var writer = new ClassWriter(0);
					new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9, writer) {
						@Override public MethodVisitor visitMethod(int access, String method, String desc, String signature, String[] exceptions) {
							return method.equals("<clinit>") ? null : super.visitMethod(access, method, desc, signature, exceptions);
						}
					}, 0);
					byte[] bytes = writer.toByteArray();
					return defineClass(name, bytes, 0, bytes.length);
				} catch (java.io.IOException error) { throw new ClassNotFoundException(name, error); }
			}
		}) {
			loader.loadClass(test.getName()).getMethod("main", String[].class).invoke(null, (Object) new String[]{"--headless"});
		}
		return true;
	}
}
