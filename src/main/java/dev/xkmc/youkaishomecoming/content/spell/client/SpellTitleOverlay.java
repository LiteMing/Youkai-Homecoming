package dev.xkmc.youkaishomecoming.content.spell.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public class SpellTitleOverlay implements IGuiOverlay {

	private static Entry current;

	public static void show(String name, String description, int duration) {
		String title = localize(name);
		String desc = description == null || description.isBlank() ? "" : localize(description);
		current = new Entry(title, desc, Util.getMillis(), Math.max(20, duration) * 50L);
	}

	@Override
	public void render(ForgeGui gui, GuiGraphics g, float pTick, int width, int height) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.screen != null || current == null) {
			return;
		}
		long elapsed = Util.getMillis() - current.startedAt();
		if (elapsed >= current.durationMs()) {
			current = null;
			return;
		}
		float progress = Mth.clamp(elapsed / (float) current.durationMs(), 0.0f, 1.0f);
		float alpha = Math.min(smooth(progress / 0.16f), 1.0f - smooth((progress - 0.82f) / 0.18f));
		if (alpha <= 0) {
			return;
		}
		float enter = easeOut(Mth.clamp(progress / 0.28f, 0.0f, 1.0f));
		float exit = easeIn(Mth.clamp((progress - 0.86f) / 0.14f, 0.0f, 1.0f));
		int panelW = Math.min(width - 28, 430);
		int panelH = current.description().isBlank() ? 32 : 46;
		int x = Math.round((width - panelW) / 2.0f + (1.0f - enter) * 90.0f - exit * 140.0f);
		int y = Math.max(22, Math.round(height * 0.18f));
		int revealW = Math.round(panelW * Mth.clamp(enter * 1.1f, 0.0f, 1.0f));
		if (revealW <= 0) {
			return;
		}

		RenderSystem.enableBlend();
		int bg = argb(alpha * 0.48f, 0x08080C);
		int line = argb(alpha * 0.9f, 0xFFE080);
		int red = argb(alpha * 0.85f, 0xB92834);
		g.fill(x, y, x + revealW, y + panelH, bg);
		g.fill(x, y, x + revealW, y + 1, line);
		g.fill(x, y + panelH - 1, x + revealW, y + panelH, line);
		g.fill(x + 3, y + 3, x + 6, y + panelH - 3, red);
		g.fill(x + revealW - 40, y + 2, x + revealW - 38, y + panelH - 2, argb(alpha * 0.35f, 0xFFFFFF));

		Font font = gui.getFont();
		int textX = x + 16 + Math.round((1.0f - enter) * 18.0f);
		int titleY = y + (current.description().isBlank() ? 12 : 9);
		drawScaled(g, font, fit(font, current.title(), panelW - 34, 1.25f), textX, titleY,
				1.25f, argb(alpha, 0xFFFFFF), true);
		if (!current.description().isBlank()) {
			g.drawString(font, fit(font, current.description(), panelW - 38, 1.0f), textX + 2, y + 29,
					argb(alpha * 0.86f, 0xD8E6FF), true);
		}
		RenderSystem.disableBlend();
	}

	private static String localize(String keyOrText) {
		if (keyOrText == null || keyOrText.isBlank()) {
			return "";
		}
		return Component.translatableWithFallback(keyOrText, keyOrText).getString();
	}

	private static void drawScaled(GuiGraphics g, Font font, String text, int x, int y,
								   float scale, int color, boolean shadow) {
		g.pose().pushPose();
		g.pose().translate(x, y, 0);
		g.pose().scale(scale, scale, scale);
		g.drawString(font, text, 0, 0, color, shadow);
		g.pose().popPose();
	}

	private static String fit(Font font, String text, int maxWidth, float scale) {
		int limit = Math.max(8, Math.round(maxWidth / scale));
		if (font.width(text) <= limit) {
			return text;
		}
		return font.plainSubstrByWidth(text, Math.max(0, limit - font.width("..."))) + "...";
	}

	private static float smooth(float t) {
		t = Mth.clamp(t, 0.0f, 1.0f);
		return t * t * (3.0f - 2.0f * t);
	}

	private static float easeOut(float t) {
		return 1.0f - (float) Math.pow(1.0f - t, 3.0);
	}

	private static float easeIn(float t) {
		return t * t * t;
	}

	private static int argb(float alpha, int rgb) {
		int a = Math.round(Mth.clamp(alpha, 0.0f, 1.0f) * 255.0f);
		return a << 24 | rgb & 0xFFFFFF;
	}

	private record Entry(String title, String description, long startedAt, long durationMs) {
	}
}
