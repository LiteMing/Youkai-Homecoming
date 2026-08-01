package dev.xkmc.youkaishomecoming.compat.ysm;

import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Installs the YH built-in YSM model pack ("YH内置/remilia") into OpenYSM's custom model
 * folder so the model id stays stable and the pack survives OpenYSM's built-in cleanup.
 * Re-extracts when the mod version changes (guarded by a marker file).
 */
public final class YsmModelPackInstaller {

	private static final String RESOURCE_DIR = "assets/youkaishomecoming/yhysm/remilia";
	private static final String VERSION_FILE = ".yh_version";
	private static final Path CUSTOM_ROOT = FMLPaths.CONFIGDIR.get()
			.resolve("yes_steve_model/custom/YH内置/remilia");

	private YsmModelPackInstaller() {
	}

	public static void install() {
		try {
			var modFile = ModList.get().getModFileById(YoukaisHomecoming.MODID);
			if (modFile == null) return;
			Path source = modFile.getFile().getSecureJar().getPath("/" + RESOURCE_DIR);
			if (!Files.isDirectory(source)) return;
			String version = ModList.get().getModContainerById(YoukaisHomecoming.MODID)
					.map(c -> c.getModInfo().getVersion().toString()).orElse("unknown");
			Path marker = CUSTOM_ROOT.resolve(VERSION_FILE);
			if (Files.isDirectory(CUSTOM_ROOT) && Files.isRegularFile(marker)
					&& version.equals(Files.readString(marker))) {
				return;
			}
			if (Files.exists(CUSTOM_ROOT)) {
				try (Stream<Path> walk = Files.walk(CUSTOM_ROOT)) {
					walk.sorted(Comparator.reverseOrder()).forEach(p -> {
						try {
							Files.deleteIfExists(p);
						} catch (IOException ignored) {
						}
					});
				}
			}
			Files.createDirectories(CUSTOM_ROOT);
			try (Stream<Path> walk = Files.walk(source)) {
				walk.forEach(src -> {
					Path rel = source.relativize(src);
					Path dest = CUSTOM_ROOT.resolve(rel.toString());
					try {
						if (Files.isDirectory(src)) {
							Files.createDirectories(dest);
						} else {
							Files.createDirectories(dest.getParent());
							Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
						}
					} catch (IOException e) {
						YoukaisHomecoming.LOGGER.warn("Failed to install YH built-in YSM model file {}", rel, e);
					}
				});
			}
			Files.writeString(marker, version);
			YoukaisHomecoming.LOGGER.info("Installed YH built-in YSM model pack to {}", CUSTOM_ROOT);
		} catch (Exception e) {
			YoukaisHomecoming.LOGGER.warn("Failed to install YH built-in YSM model pack", e);
		}
	}
}
