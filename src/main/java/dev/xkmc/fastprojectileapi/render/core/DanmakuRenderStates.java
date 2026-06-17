package dev.xkmc.fastprojectileapi.render.core;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.xkmc.fastprojectileapi.compat.oculus.OculusRenderCompat;
import dev.xkmc.fastprojectileapi.entity.SimplifiedProjectile;
import net.minecraft.Util;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.BiFunction;

public abstract class DanmakuRenderStates extends RenderType {


	public DanmakuRenderStates(String pName, VertexFormat pFormat, VertexFormat.Mode pMode, int pBufferSize, boolean pAffectsCrumbling, boolean pSortOnUpload, Runnable pSetupState, Runnable pClearState) {
		super(pName, pFormat, pMode, pBufferSize, pAffectsCrumbling, pSortOnUpload, pSetupState, pClearState);
	}

	protected static final ShaderStateShard DANMAKU_SHADER = new ShaderStateShard(GameRenderer::getPositionTexColorShader);

	private static RenderType create(String name, ResourceLocation tex, boolean cull, DisplayType type, boolean noDepthWrite) {
		// sortOnUpload=false: skip per-frame quad distance sorting for danmaku.
		// Sorting 30,000 quads costs ~9% frame time (putSortedQuadIndices).
		// Danmaku are small particles where depth sort order is visually negligible.
		//
		// Laser inner-core strategy:
		// - noDepthWrite=true: don't write depth (avoid z-fighting with shell)
		// - NO_DEPTH_TEST: ignore depth buffer (render even if shell is in front)
		// - Oculus DECAL bucket: render after GENERAL_TRANSPARENT shell
		//
		// This ensures the core is always visible regardless of shell depth values,
		// solving the Oculus batching issue where shell+core in the same bucket
		// would have undefined rendering order.
		boolean opaque = type == DisplayType.SOLID;
		boolean laserCore = noDepthWrite && !opaque; // only laser core has noDepthWrite=true
		return create(name,
				DefaultVertexFormat.POSITION_TEX_COLOR,
				VertexFormat.Mode.QUADS,
				256, true, false,
				CompositeState.builder()
						.setShaderState(DANMAKU_SHADER)
						.setTextureState(new TextureStateShard(tex, false, false))
						.setTransparencyState(switch (type) {
							case SOLID -> NO_TRANSPARENCY;
							case TRANSPARENT -> TRANSLUCENT_TRANSPARENCY;
							case ADDITIVE -> ADDITIVE_TRANSPARENCY;
						})
						.setWriteMaskState((opaque || !noDepthWrite) ? COLOR_DEPTH_WRITE : COLOR_WRITE)
						.setDepthTestState(laserCore ? NO_DEPTH_TEST : LEQUAL_DEPTH_TEST)
						.setCullState(cull ? CULL : NO_CULL)
						.createCompositeState(false));
	}

	private static final BiFunction<ResourceLocation, DisplayType, RenderType> DANMAKU =
			Util.memoize((rl, type) -> create("danmaku_" + type.getName(), rl, false, type, false));
	private static final BiFunction<ResourceLocation, DisplayType, RenderType> LASER =
			Util.memoize((rl, type) -> create("laser_" + type.getName(), rl, true, type, false));
	// Laser inner-core uses a separate RenderType so it can be routed into Oculus's DECAL
	// bucket — drawn AFTER the GENERAL_TRANSPARENT shell, so the translucent shell never
	// hides the core under Oculus's batched-entity-rendering pipeline. Vanilla just sees
	// two RenderTypes flushed in submission order, which is also correct.
	// Additionally, uses COLOR_WRITE (no depth write) to avoid shell/core z-fighting.
	private static final BiFunction<ResourceLocation, DisplayType, RenderType> LASER_CORE =
			Util.memoize((rl, type) -> {
				RenderType rt = create("laser_core_" + type.getName(), rl, true, type, true);
				OculusRenderCompat.markAsDecal(rt);
				return rt;
			});

	public static RenderType danmaku(ResourceLocation rl, DisplayType type) {
		if (type == DisplayType.SOLID) type = DisplayType.TRANSPARENT;
		return DANMAKU.apply(rl, type);
	}

	public static RenderType laser(ResourceLocation rl, DisplayType type) {
		return LASER.apply(rl, type);
	}

	public static RenderType laserCore(ResourceLocation rl, DisplayType type) {
		return LASER_CORE.apply(rl, type);
	}

	public static int fading(DisplayType display, int col, ProjectileRenderer<?> r, SimplifiedProjectile e) {
		double perc = r.fading(e);
		if (perc == 0) return col;
		int alpha = (int) ((col >>> 24) * perc);
		if (display == DisplayType.ADDITIVE) {
			return 0xff000000 | alpha << 16 | alpha << 8 | alpha;
		}
		return (alpha << 24) | col & 0xffffff;
	}

}
