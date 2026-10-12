package dev.xkmc.youkaishomecoming.compat.ysm;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;
import java.util.TreeSet;

/** Narrow YH view of OYSM's loaded model-entry catalog. */
@OnlyIn(Dist.CLIENT)
public final class YsmModelCatalogBridge {
	private YsmModelCatalogBridge() { }

	public static List<String> modelIds() {
		TreeSet<String> ids = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		ids.addAll(YSMClientCompat.loadedModelIds());
		ids.addAll(YSMCompatConfig.configuredModelIds());
		return List.copyOf(ids);
	}

	public static String modelReference(String model) {
		if (model == null || model.isBlank()) return "";
		if (YSMClientCompat.isLoaded()) {
			var resolved = rip.ysm.api.client.ExternalModelRenderAPI.resolve(model);
			if (resolved.isPresent()) return resolved.get().path();
		}
		return model;
	}

	public static String parent(String path) {
		if (path == null || path.isEmpty()) return "";
		int slash = path.lastIndexOf('/');
		return slash < 0 ? "" : path.substring(0, slash);
	}

	public static String leaf(String path) {
		if (path == null || path.isEmpty()) return "";
		return path.substring(path.lastIndexOf('/') + 1);
	}

	public static boolean inside(String path, String folder) {
		return folder == null || folder.isEmpty() || path != null && path.startsWith(folder + "/");
	}

	public static String animationGroup(String clip) {
		if (clip == null || clip.isEmpty()) return "";
		String path = clip.replace('.', '/').replace('_', '/');
		if (path.contains("/")) return parent(path);
		String numberedFamily = clip.replaceFirst("[0-9]+$", "");
		return numberedFamily.equals(clip) ? "" : numberedFamily;
	}
}
