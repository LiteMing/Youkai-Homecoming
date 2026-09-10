package dev.xkmc.youkaishomecoming.content.spell.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellTitleStyle;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** Shared drawing for an in-game title cue and the editor's independent preview. */
public final class SpellTitleRenderer {

	private static int resourceVersion;
	private int imageVersion = -1;
	private ResourceLocation imageId;
	private BackgroundImage image;

	public static void onResourceReload(ResourceManager resources) {
		resourceVersion++;
	}

	/** A fully entered frame before docking, useful when arranging a portrait. */
	public static float layoutProgress() {
		return Math.max(0.2f, YHModConfig.CLIENT.spellTitleDockStart.get().floatValue() * 0.6f);
	}

	public void render(GuiGraphics g, Font font, String title, String description, SpellTitleStyle style,
			float progress, int width, int height, int index, @Nullable ActiveSpellHudOverlay.Row destination) {
		progress = Mth.clamp(progress, 0, 1);
		float dockStart = YHModConfig.CLIENT.spellTitleDockStart.get().floatValue();
		float dock = smooth((progress - dockStart) / (1 - dockStart));
		float enter = 1 - (float) Math.pow(1 - Mth.clamp(progress / 0.2f, 0, 1), 3);
		float alpha = smooth(progress / 0.12f);
		if (destination == null) alpha *= 1 - dock;
		float introScale = YHModConfig.CLIENT.spellTitleScale.get().floatValue();
		int panelWidth = Math.max(1, Math.min(width - 28, 430));
		String text = destination == null ? ActiveSpellHudOverlay.fit(font, title, (int) (panelWidth / introScale))
				: destination.text();
		introScale = Math.min(introScale, panelWidth / (float) Math.max(1, font.width(text)));
		float introX = (width - font.width(text) * introScale) / 2f;
		float introY = Math.max(22, height * 0.18f) + index * (font.lineHeight * introScale + 24);
		float x = introX + (1 - enter) * 90;
		float y = introY;
		float scale = introScale;
		int color = 0xFFFFFFFF;
		if (destination != null) {
			x = Mth.lerp(dock, x, destination.x());
			y = Mth.lerp(dock, y, destination.y());
			scale = Mth.lerp(dock, scale, destination.scale());
			color = mixColor(dock, color, destination.color());
		}
		float decorationAlpha = alpha * (1 - dock);
		drawBackground(g, style, width / 2f + (1 - enter) * 45, introY, panelWidth, decorationAlpha, enter);
		drawTitle(g, font, text, x, y, scale, color, style, alpha);
		if (!description.isBlank() && decorationAlpha > 0.02f) {
			String desc = ActiveSpellHudOverlay.fit(font, description, panelWidth);
			g.drawString(font, desc, Math.round((width - font.width(desc)) / 2f),
					Math.round(introY + font.lineHeight * introScale + 9), withAlpha(0xFFD8E6FF, decorationAlpha), true);
		}
	}

	public static void drawTitle(GuiGraphics g, Font font, String text, float x, float y, float scale,
			int color, SpellTitleStyle style, float alpha) {
		// Vanilla Font treats alpha 0..3 as an unspecified (opaque) alpha.
		if (alpha <= 0.02f) return;
		int start = withAlpha(style.gradientStart().orElseGet(() -> YHModConfig.CLIENT.spellTitleGradientStart.get()), alpha);
		int end = withAlpha(style.gradientEnd().orElseGet(() -> YHModConfig.CLIENT.spellTitleGradientEnd.get()), alpha);
		g.pose().pushPose();
		g.pose().translate(x, y, 0);
		g.pose().scale(scale, scale, 1);
		// Rotate the built-in vertical gradient into a horizontal one.
		g.pose().pushPose();
		g.pose().translate(-4, font.lineHeight + 2, 0);
		g.pose().mulPose(Axis.ZP.rotationDegrees(-90));
		g.fillGradient(0, 0, font.lineHeight + 4, font.width(text) + 7, start, end);
		g.pose().popPose();
		g.drawString(font, text, 0, 0, withAlpha(color, alpha), true);
		g.pose().popPose();
	}

	public boolean isBackgroundMissing(SpellTitleStyle style) {
		return style.background().isPresent() && background(style.background().get()) == null;
	}

	private void drawBackground(GuiGraphics g, SpellTitleStyle style, float x, float y, int panelWidth,
			float alpha, float enter) {
		if (alpha <= 0.01f || style.background().isEmpty()) return;
		BackgroundImage background = background(style.background().get());
		if (background == null) return;
		float size = style.backgroundScale().orElseGet(() -> YHModConfig.CLIENT.spellTitleBackgroundScale.get().floatValue());
		x += style.backgroundX().orElseGet(() -> YHModConfig.CLIENT.spellTitleBackgroundX.get().floatValue());
		y += style.backgroundY().orElseGet(() -> YHModConfig.CLIENT.spellTitleBackgroundY.get().floatValue());
		int imageWidth = Math.max(1, Math.round(panelWidth * size * Mth.lerp(enter, 1.08f, 1)));
		int imageHeight = Math.max(1, Math.round(imageWidth * background.height / (float) background.width));
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		g.setColor(1, 1, 1, alpha);
		try {
			g.blit(background.texture, Math.round(x - imageWidth / 2f), Math.round(y - imageHeight / 2f),
					imageWidth, imageHeight, 0, 0, background.width, background.height, background.width, background.height);
		} finally {
			g.setColor(1, 1, 1, 1);
			RenderSystem.disableBlend();
		}
	}

	private BackgroundImage background(ResourceLocation texture) {
		// Editing scale/offset/color reuses the decoded dimensions. Changing the ID or
		// reloading resource packs invalidates both successful and missing lookups.
		if (imageVersion != resourceVersion || !Objects.equals(imageId, texture)) {
			imageVersion = resourceVersion;
			imageId = texture;
			image = loadBackground(texture);
		}
		return image;
	}

	private static BackgroundImage loadBackground(ResourceLocation texture) {
		var resource = Minecraft.getInstance().getResourceManager().getResource(texture);
		if (resource.isEmpty()) return null;
		try (var stream = resource.get().open(); var image = NativeImage.read(stream)) {
			return new BackgroundImage(texture, image.getWidth(), image.getHeight());
		} catch (Exception ignored) {
			return null;
		}
	}

	private static float smooth(float t) {
		t = Mth.clamp(t, 0, 1);
		return t * t * (3 - 2 * t);
	}

	private static int withAlpha(int color, float alpha) {
		return Math.round((color >>> 24) * Mth.clamp(alpha, 0, 1)) << 24 | color & 0xFFFFFF;
	}

	private static int mixColor(float t, int from, int to) {
		return 0xFF000000 | Math.round(Mth.lerp(t, (from >> 16) & 255, (to >> 16) & 255)) << 16
				| Math.round(Mth.lerp(t, (from >> 8) & 255, (to >> 8) & 255)) << 8
				| Math.round(Mth.lerp(t, from & 255, to & 255));
	}

	private record BackgroundImage(ResourceLocation texture, int width, int height) {}
}
