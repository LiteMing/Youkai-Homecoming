package dev.xkmc.youkaishomecoming.content.client.beaten;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

final class BeatenRenderStates extends RenderStateShard {

	static final RenderType TRANSLUCENT = create("youkai_beaten_translucent", TRANSLUCENT_TRANSPARENCY, true);
	static final RenderType ADDITIVE = create("youkai_beaten_additive", ADDITIVE_TRANSPARENCY, false);

	private static RenderType create(String name, TransparencyStateShard transparency, boolean sort) {
		return RenderType.create(
				name,
				DefaultVertexFormat.POSITION_COLOR,
				VertexFormat.Mode.QUADS,
				512,
				false,
				sort,
				RenderType.CompositeState.builder()
						.setShaderState(new ShaderStateShard(GameRenderer::getPositionColorShader))
						.setTransparencyState(transparency)
						.setWriteMaskState(COLOR_WRITE)
						.setDepthTestState(LEQUAL_DEPTH_TEST)
						.setCullState(NO_CULL)
						.createCompositeState(false)
		);
	}

	private BeatenRenderStates(String name, Runnable setup, Runnable clear) {
		super(name, setup, clear);
	}
}
