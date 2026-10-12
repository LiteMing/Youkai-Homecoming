package dev.xkmc.youkaishomecoming.compat.ysm;

import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

/** Registers YH's packaged model assets with OYSM without extracting them. */
public final class YsmModelPackRegistration {

	private static final String RESOURCE_DIR = "assets/youkaishomecoming/models/ysm";
	private static boolean attempted;

	private YsmModelPackRegistration() {
	}

	/** Safe to call from common setup; a missing OYSM is a supported configuration. */
	public static void register() {
		if (attempted || !ModList.get().isLoaded("yes_steve_model") || !YsmOpenYsmSupport.available()) return;
		attempted = true;
		try {
			var modFile = ModList.get().getModFileById(YoukaisHomecoming.MODID);
			if (modFile == null) return;
			Path source = modFile.getFile().getSecureJar().getPath("/" + RESOURCE_DIR);
			if (!Files.isDirectory(source)) return;
			Class<?> api = Class.forName("rip.ysm.api.ExternalModelPackAPI", false, YsmModelPackRegistration.class.getClassLoader());
			Method register = api.getMethod("registerDirectory", String.class, Path.class, String.class);
			register.invoke(null, YoukaisHomecoming.MODID, source, "youkaishomecoming/");
			YoukaisHomecoming.LOGGER.info("Registered YH YSM model resources with OpenYSM: {}", source);
		} catch (ClassNotFoundException | NoSuchMethodException ignored) {
			// The resource registration contract is introduced with the decoupled
			// OYSM line; older builds require the matching YH/OYSM pair.
		} catch (IllegalAccessException | InvocationTargetException | RuntimeException ex) {
			YoukaisHomecoming.LOGGER.warn("Failed to register YH YSM model resources with OpenYSM", ex);
		}
	}
}
