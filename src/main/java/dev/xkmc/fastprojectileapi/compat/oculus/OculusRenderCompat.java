package dev.xkmc.fastprojectileapi.compat.oculus;

import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

/**
 * Routes a RenderType into Oculus's DECAL transparency bucket via reflection.
 * <p>
 * Oculus replaces {@code MultiBufferSource.immediate()} with a fully-buffered source
 * that batches per-RenderType and reorders draws by TransparencyType. Within a single
 * bucket, geometry submitted to the same RenderType is concatenated into one draw call
 * with no defined inter-quad ordering, so a translucent shell submitted after a translucent
 * core ends up blending on top of the core regardless of camera position.
 * <p>
 * Calling {@link #markAsDecal(RenderType)} on the inner-core RenderType pushes it from the
 * default GENERAL_TRANSPARENT bucket into DECAL, which Oculus guarantees to draw AFTER
 * GENERAL_TRANSPARENT. The shell paints first, the core paints on top.
 * <p>
 * Reflection keeps Youkai-Homecoming free of a compile-time Oculus dependency. Without
 * Oculus loaded, the call is a silent no-op.
 */
public final class OculusRenderCompat {

	private OculusRenderCompat() {
	}

	private static final boolean LOADED = ModList.get().isLoaded("oculus");

	private static final Method SET_TRANSPARENCY_TYPE;
	private static final Object DECAL_BUCKET;

	static {
		Method setter = null;
		Object decal = null;
		if (LOADED) {
			try {
				Class<?> holder = Class.forName("net.irisshaders.batchedentityrendering.impl.BlendingStateHolder");
				Class<?> bucket = Class.forName("net.irisshaders.batchedentityrendering.impl.TransparencyType");
				setter = holder.getMethod("setTransparencyType", bucket);
				decal = Enum.valueOf(bucket.asSubclass(Enum.class), "DECAL");
			} catch (ReflectiveOperationException ignored) {
			}
		}
		SET_TRANSPARENCY_TYPE = setter;
		DECAL_BUCKET = decal;
	}

	public static void markAsDecal(RenderType type) {
		if (SET_TRANSPARENCY_TYPE == null || DECAL_BUCKET == null) return;
		try {
			SET_TRANSPARENCY_TYPE.invoke(type, DECAL_BUCKET);
		} catch (ReflectiveOperationException ignored) {
		}
	}

}
