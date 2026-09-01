package dev.xkmc.youkaishomecoming.compat.exposure;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import io.github.mortuusars.exposure.Exposure;
import io.github.mortuusars.exposure.ExposureClient;
import io.github.mortuusars.exposure.camera.capture.CapturedFramesHistory;
import io.github.mortuusars.exposure.render.PhotographRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;

/**
 * Client-side overlay that displays a photo thumbnail and score notification
 * after photographing danmaku. Uses Exposure's PhotographRenderer to render
 * the actual captured photo as a thumbnail in the configured corner.
 */
@OnlyIn(Dist.CLIENT)
public class DanmakuPhotoOverlay {

	private static int displayTicksRemaining = 0;
	private static int totalDisplayTicks = 0;
	private static int lastTotalErased = 0;
	private static int lastScore = 0;

	/**
	 * Called from the network packet handler to trigger the overlay display.
	 */
	public static void trigger(int totalErased, int score) {
		totalDisplayTicks = YHModConfig.CLIENT.photoOverlayDuration.get();
		displayTicksRemaining = totalDisplayTicks;
		lastTotalErased = totalErased;
		lastScore = score;
	}

	@SubscribeEvent
	public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
		if (event.getOverlay() != VanillaGuiOverlay.CROSSHAIR.type()) return;
		if (displayTicksRemaining <= 0) return;

		GuiGraphics graphics = event.getGuiGraphics();
		int screenWidth = event.getWindow().getGuiScaledWidth();
		int screenHeight = event.getWindow().getGuiScaledHeight();

		renderPhotoPanel(graphics, screenWidth, screenHeight);
	}

	public static void tick() {
		if (displayTicksRemaining > 0) {
			displayTicksRemaining--;
		}
	}

	private static void renderPhotoPanel(GuiGraphics graphics, int screenWidth, int screenHeight) {
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		float partialTick = mc.getFrameTime();

		// Animation progress: 0 = just appeared, 1 = about to disappear
		float progress = 1f - (float) (displayTicksRemaining - partialTick) / totalDisplayTicks;

		// Slide-in for first 10%, fade-out for last 20%
		float slideIn = Math.min(1f, progress * 10f);
		float alpha = (float) (double) YHModConfig.CLIENT.photoOverlayAlpha.get();
		if (progress > 0.8f) {
			alpha *= (1f - progress) / 0.2f;
		}
		if (alpha <= 0.01f) return;

		// Photo thumbnail size (from Exposure's renderer)
		int photoSize = ExposureClient.getExposureRenderer().getSize();
		float scale = (float) (double) YHModConfig.CLIENT.photoOverlayScale.get();
		int scaledPhotoSize = (int) (photoSize * scale);

		// Panel dimensions: photo + text area below
		int panelWidth = scaledPhotoSize + 8;
		int textHeight = 44;
		int panelHeight = scaledPhotoSize + textHeight + 8;
		int margin = 10;

		// Position based on configured corner
		int corner = YHModConfig.CLIENT.photoOverlayCorner.get();
		int baseX, baseY;
		float slideOffsetX = 0;

		switch (corner) {
			case 1 -> { // top-right
				baseX = screenWidth - panelWidth - margin;
				baseY = margin;
				slideOffsetX = panelWidth * (1f - slideIn);
			}
			case 2 -> { // bottom-left
				baseX = margin;
				baseY = screenHeight - panelHeight - margin;
				slideOffsetX = -panelWidth * (1f - slideIn);
			}
			case 3 -> { // bottom-right
				baseX = screenWidth - panelWidth - margin;
				baseY = screenHeight - panelHeight - margin;
				slideOffsetX = panelWidth * (1f - slideIn);
			}
			default -> { // 0 = top-left
				baseX = margin;
				baseY = margin;
				slideOffsetX = -panelWidth * (1f - slideIn);
			}
		}

		int x = baseX + (int) slideOffsetX;
		int y = baseY;

		PoseStack pose = graphics.pose();
		pose.pushPose();

		// Background panel with alpha
		int bgAlpha = (int) (alpha * 180);
		int bgColor = (bgAlpha << 24) | 0x000000;
		graphics.fill(x, y, x + panelWidth, y + panelHeight, bgColor);

		// Gold border
		int borderAlpha = (int) (alpha * 255);
		int borderColor = (borderAlpha << 24) | 0xFFD700;
		graphics.fill(x, y, x + panelWidth, y + 1, borderColor);
		graphics.fill(x, y + panelHeight - 1, x + panelWidth, y + panelHeight, borderColor);
		graphics.fill(x, y, x + 1, y + panelHeight, borderColor);
		graphics.fill(x + panelWidth - 1, y, x + panelWidth, y + panelHeight, borderColor);

		// Render the photo thumbnail using Exposure's renderer
		renderPhotoThumbnail(graphics, x + 4, y + 4, scale, (int) (alpha * 255));

		// Text below the photo
		int textY = y + scaledPhotoSize + 8;
		int textColor = (borderAlpha << 24) | 0xFFFFFF;
		int scoreColor = (borderAlpha << 24) | 0xFFD700;

		// Danmaku count
		Component countText = Component.translatable("exposure.youkaishomecoming.danmaku_count", lastTotalErased);
		graphics.drawString(font, countText, x + 4, textY, textColor, true);

		// Score + rating
		String rating = getScoreRating(lastScore);
		Component scoreText = Component.translatable("exposure.youkaishomecoming.score", lastScore);
		graphics.drawString(font, scoreText, x + 4, textY + 12, scoreColor, true);

		int ratingColor = (borderAlpha << 24) | 0xFF4444;
		Component ratingText = Component.literal(rating);
		int ratingWidth = font.width(ratingText);
		graphics.drawString(font, ratingText, x + panelWidth - ratingWidth - 4, textY + 24, ratingColor, true);

		pose.popPose();
	}

	/**
	 * Render the latest captured photo as a thumbnail using Exposure's PhotographRenderer.
	 */
	private static void renderPhotoThumbnail(GuiGraphics graphics, int x, int y, float scale, int alpha) {
		List<CompoundTag> frames = CapturedFramesHistory.get();
		if (frames.isEmpty()) return;

		// Get the most recent frame
		CompoundTag latestFrame = frames.get(0);

		// Create a photograph ItemStack with the frame data
		ItemStack photoStack = new ItemStack(Exposure.Items.PHOTOGRAPH.get());
		photoStack.setTag(latestFrame.copy());

		PoseStack pose = graphics.pose();
		pose.pushPose();
		pose.translate(x, y, 100); // Z offset to render above background
		pose.scale(scale, scale, scale);

		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();

		MultiBufferSource.BufferSource bufferSource = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
		PhotographRenderer.render(photoStack, false, false, pose, bufferSource,
				LightTexture.FULL_BRIGHT, 255, 255, 255, alpha);
		bufferSource.endBatch();

		pose.popPose();
	}

	/**
	 * Get a rating string based on score, inspired by 文花帖 grading.
	 */
	private static String getScoreRating(int score) {
		if (score >= 50000) return "★★★";
		if (score >= 20000) return "★★";
		if (score >= 10000) return "★";
		if (score >= 5000) return "A";
		if (score >= 2000) return "B";
		if (score >= 1000) return "C";
		return "D";
	}
}
