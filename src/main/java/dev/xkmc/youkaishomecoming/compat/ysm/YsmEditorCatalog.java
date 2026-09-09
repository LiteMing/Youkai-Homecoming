package dev.xkmc.youkaishomecoming.compat.ysm;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;
import java.util.TreeSet;

/**
 * Live catalog inputs shared by the YSM workspace and spell property editors.
 * The editor must see both models known to the external runtime and models with
 * a locally cached YH profile, just like the dedicated YSM catalog page does.
 */
@OnlyIn(Dist.CLIENT)
public final class YsmEditorCatalog {

	private YsmEditorCatalog() {
	}

	/** Returns the same live model set used by the YSM catalog workspace. */
	public static List<String> modelIds() {
		TreeSet<String> ids = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		ids.addAll(YSMClientCompat.loadedModelIds());
		ids.addAll(YsmClientProfiles.models());
		return List.copyOf(ids);
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

	/** Matches the YSM workspace's animation-folder grouping convention. */
	public static String animationGroup(String clip) {
		if (clip == null || clip.isEmpty()) return "";
		String path = clip.replace('.', '/').replace('_', '/');
		if (path.contains("/")) return parent(path);
		String numberedFamily = clip.replaceFirst("[0-9]+$", "");
		return numberedFamily.equals(clip) ? "" : numberedFamily;
	}

}
