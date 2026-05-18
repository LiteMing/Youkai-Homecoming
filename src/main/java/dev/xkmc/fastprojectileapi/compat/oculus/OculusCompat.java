package dev.xkmc.fastprojectileapi.compat.oculus;

import net.minecraftforge.fml.ModList;

/**
 * Detects whether Oculus is loaded.
 * <p>
 * When Oculus is present, the danmaku rendering fast paths
 * (direct ByteBuffer writes in BulkDataWriter, PoseStack-bypass in
 * ClientDanmakuCache.renderAll) must fall back to the standard
 * VertexConsumer + PoseStack path. Oculus ships Embeddium, whose
 * BufferBuilder mixins replace the internal vertex-write state
 * machine even with no shader pack active — the fast paths
 * bypass that state machine and corrupt the buffer.
 */
public final class OculusCompat {

	private OculusCompat() {
	}

	private static final boolean LOADED = ModList.get().isLoaded("oculus");

	/**
	 * @return true when Oculus is loaded (regardless of shader pack state).
	 */
	public static boolean shouldFallback() {
		return LOADED;
	}

}
