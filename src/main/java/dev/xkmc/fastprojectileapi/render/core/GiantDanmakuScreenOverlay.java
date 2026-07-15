package dev.xkmc.fastprojectileapi.render.core;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = YoukaisHomecoming.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class GiantDanmakuScreenOverlay {

	private static ResourceLocation bestTexture;
	private static int bestColor = 0xffffffff;
	private static float bestScore = -1;

	private static boolean currentSeen;
	private static int currentSeenColor = 0xffffffff;
	private static float currentSeenScore = -1;

	private static ResourceLocation currentTexture;
	private static int currentColor = 0xffffffff;
	private static float currentScore = 0;
	private static float alpha = 0;

	public static void beginFrame() {
		bestTexture = null;
		bestColor = 0xffffffff;
		bestScore = -1;
		currentSeen = false;
		currentSeenColor = 0xffffffff;
		currentSeenScore = -1;
	}

	public static void accept(ResourceLocation texture, int color, float insideScore) {
		if (insideScore <= 0) return;
		if (texture.equals(currentTexture) && insideScore > currentSeenScore) {
			currentSeen = true;
			currentSeenColor = color;
			currentSeenScore = insideScore;
		}
		float score = insideScore + (texture.equals(currentTexture) ? 0.25f : 0);
		if (score > bestScore) {
			bestTexture = texture;
			bestColor = color;
			bestScore = score;
		}
	}

	@SubscribeEvent
	public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
		if (event.getOverlay() != VanillaGuiOverlay.CROSSHAIR.type()) return;
		if (Minecraft.getInstance().level == null) {
			alpha = 0;
			return;
		}
		updateSelection();
		if (alpha <= 0.01f || currentTexture == null) return;
		render(event);
	}

	private static void updateSelection() {
		if (currentSeen) {
			currentColor = currentSeenColor;
			currentScore = currentSeenScore;
		} else if (bestTexture != null) {
			currentTexture = bestTexture;
			currentColor = bestColor;
			currentScore = Math.max(0, bestScore);
		}
		float target = bestTexture == null ? 0 : Mth.clamp(0.16f + currentScore * 0.24f, 0.16f, 0.42f);
		alpha = Mth.lerp(target > alpha ? 0.45f : 0.20f, alpha, target);
		if (alpha <= 0.01f && bestTexture == null) {
			currentTexture = null;
		}
	}

	private static void render(RenderGuiOverlayEvent.Post event) {
		RenderSystem.disableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.enableBlend();
		RenderSystem.blendFuncSeparate(
				GlStateManager.SourceFactor.SRC_ALPHA,
				GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
				GlStateManager.SourceFactor.ONE,
				GlStateManager.DestFactor.ZERO);

		float r = FastColor.ARGB32.red(currentColor) / 255f;
		float g = FastColor.ARGB32.green(currentColor) / 255f;
		float b = FastColor.ARGB32.blue(currentColor) / 255f;
		float a = alpha * (FastColor.ARGB32.alpha(currentColor) / 255f);
		RenderSystem.setShaderColor(r, g, b, a);
		RenderSystem.setShader(GameRenderer::getPositionTexShader);
		RenderSystem.setShaderTexture(0, currentTexture);

		int width = event.getWindow().getGuiScaledWidth();
		int height = event.getWindow().getGuiScaledHeight();
		float aspect = width / (float) Math.max(1, height);
		float repeat = 1.35f;
		float uMax = repeat * Math.max(1, aspect);
		float vMax = repeat;

		BufferBuilder buffer = Tesselator.getInstance().getBuilder();
		buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
		buffer.vertex(0, height, -90).uv(0, vMax).endVertex();
		buffer.vertex(width, height, -90).uv(uMax, vMax).endVertex();
		buffer.vertex(width, 0, -90).uv(uMax, 0).endVertex();
		buffer.vertex(0, 0, -90).uv(0, 0).endVertex();
		Tesselator.getInstance().end();

		RenderSystem.setShaderColor(1, 1, 1, 1);
		RenderSystem.disableBlend();
		RenderSystem.depthMask(true);
		RenderSystem.enableDepthTest();
	}

}
