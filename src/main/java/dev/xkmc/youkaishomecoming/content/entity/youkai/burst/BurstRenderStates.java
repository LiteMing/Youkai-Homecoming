package dev.xkmc.youkaishomecoming.content.entity.youkai.burst;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Render types for the defeat burst. Both are plain position-color quads going
 * through vanilla core shaders (same pipeline family as the spell circle), so
 * shader packs handle them like any vanilla translucent geometry. Neither
 * writes depth, so water and terrain translucency behind the burst stay intact.
 */
@OnlyIn(Dist.CLIENT)
public class BurstRenderStates extends RenderStateShard {

	/** Alpha-blended layer for the dark scatter petals. */
	public static final RenderType TRANSLUCENT = RenderType.create(
			"yh_burst_translucent",
			DefaultVertexFormat.POSITION_COLOR,
			VertexFormat.Mode.QUADS, 256, false, true,
			RenderType.CompositeState.builder()
					.setShaderState(POSITION_COLOR_SHADER)
					.setCullState(NO_CULL)
					.setTransparencyState(TRANSLUCENT_TRANSPARENCY)
					.setWriteMaskState(COLOR_WRITE)
					.createCompositeState(false)
	);

	/** Additive layer for the star flower, core flash and shockwave rings. */
	public static final RenderType ADDITIVE = RenderType.create(
			"yh_burst_additive",
			DefaultVertexFormat.POSITION_COLOR,
			VertexFormat.Mode.QUADS, 256, false, false,
			RenderType.CompositeState.builder()
					.setShaderState(RENDERTYPE_LIGHTNING_SHADER)
					.setCullState(NO_CULL)
					.setTransparencyState(LIGHTNING_TRANSPARENCY)
					.setWriteMaskState(COLOR_WRITE)
					.createCompositeState(false)
	);

	private BurstRenderStates(String str, Runnable a, Runnable b) {
		super(str, a, b);
	}

}
