package dev.xkmc.fastprojectileapi.render.virtual;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

import java.util.OptionalDouble;

/** Render state for client-only hit-box overlays that must ignore world depth. */
final class DanmakuHitboxRenderStates extends RenderStateShard {

	static final RenderType LINES = RenderType.create(
			"youkaishomecoming_danmaku_hitbox_lines",
			DefaultVertexFormat.POSITION_COLOR,
			VertexFormat.Mode.LINES,
			256,
			false,
			false,
			RenderType.CompositeState.builder()
					.setShaderState(new ShaderStateShard(GameRenderer::getRendertypeLinesShader))
					.setLineState(new LineStateShard(OptionalDouble.empty()))
					.setTransparencyState(NO_TRANSPARENCY)
					.setWriteMaskState(COLOR_WRITE)
					.setDepthTestState(NO_DEPTH_TEST)
					.setCullState(NO_CULL)
					.createCompositeState(false)
	);

	private DanmakuHitboxRenderStates(String name, Runnable setup, Runnable clear) {
		super(name, setup, clear);
	}
}
