package dev.xkmc.youkaishomecoming.content.spell.preview.dock;

import dev.xkmc.youkaishomecoming.content.spell.preview.SpellEditorLocalization;
import dev.xkmc.youkaishomecoming.content.spell.preview.VirtualSpellScene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 性能监视面板。显示 FPS、tick 帧生成时间和 render 间隔。
 * 支持三种显示模式：简略文本 / 线表 / 两者兼显。
 * 默认与 Controls 面板共用一个 DockGroup（并列 Tab）。
 */
@OnlyIn(Dist.CLIENT)
public class PerfDockPanel implements DockPanel {

	private static final int SAMPLE_COUNT = 120;

	private final VirtualSpellScene scene;

	private int x, y, w, h;

	// Static so data survives rebuildScreen() which recreates the panel
	private static final long[] tickTimeSamples = new long[SAMPLE_COUNT];
	private static final long[] renderIntervalSamples = new long[SAMPLE_COUNT];
	private static int sampleIndex = 0;
	private static long lastRenderNano = 0;
	private static int fps = 0;
	private static int fpsCounter = 0;
	private static long fpsTimer = 0;

	public PerfDockPanel(VirtualSpellScene scene) {
		this.scene = scene;
	}

	// ---- DockPanel ----

	@Override
	public String dockTitle() {
		return "Perf";
	}

	@Override
	public String dockId() {
		return "perf";
	}

	@Override
	public void setBounds(int x, int y, int w, int h) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
	}

	@Override
	public int getX() { return x; }

	@Override
	public int getY() { return y; }

	@Override
	public int getWidth() { return w; }

	@Override
	public int getHeight() { return h; }

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(x, y, x + w, y + h, 0xCC000000);

		Font font = Minecraft.getInstance().font;

		// --- Sampling ---
		long now = System.nanoTime();
		fpsCounter++;
		if (now - fpsTimer >= 1_000_000_000L) {
			fps = fpsCounter;
			fpsCounter = 0;
			fpsTimer = now;
		}
		long tickNs = scene.getLastTickNanos();
		long renderInterval = lastRenderNano > 0 ? now - lastRenderNano : 0;
		lastRenderNano = now;
		tickTimeSamples[sampleIndex] = tickNs;
		renderIntervalSamples[sampleIndex] = renderInterval;
		sampleIndex = (sampleIndex + 1) % SAMPLE_COUNT;

		// --- Brief text ---
		double tickMs = tickNs / 1_000_000.0;
		double avgTickMs = calcAvgMs(tickTimeSamples);
		double maxTickMs = calcMaxMs(tickTimeSamples);

		int ty = y + 4;
		String briefText = SpellEditorLocalization.isChinese()
				? String.format("FPS: %d   tick: %.1fms   平均: %.1fms   最大: %.1fms", fps, tickMs, avgTickMs, maxTickMs)
				: String.format("FPS: %d   tick: %.1fms   avg: %.1fms   max: %.1fms", fps, tickMs, avgTickMs, maxTickMs);
		graphics.drawString(font, briefText, x + 4, ty, 0xFF88FF88, false);
		ty += 12;

		String entityText = SpellEditorLocalization.isChinese()
				? "实体: " + scene.getEntityCount() + "   速度: " + scene.getCurrentSpeed() + "x"
				: "entities: " + scene.getEntityCount() + "   speed: " + scene.getCurrentSpeed() + "x";
		graphics.drawString(font, entityText, x + 4, ty, 0xFFAAAACC, false);
		ty += 12;
		if (scene.isPilotEnabled()) {
			double pilotMs = scene.getLastPilotNanos() / 1_000_000.0;
			String pilotText = SpellEditorLocalization.isChinese()
					? String.format("AI试飞: ON   pilot: %.2fms", pilotMs)
					: String.format("AI pilot: ON   pilot: %.2fms", pilotMs);
			graphics.drawString(font, pilotText, x + 4, ty, 0xFFFFCC66, false);
			ty += 4;
		}
		ty += 12;

		// --- Graph ---
		int graphW = Math.min(SAMPLE_COUNT, w - 8);
		int graphH = Math.min(60, h - ty + y - 24);
		if (graphH < 16) return; // not enough space for graph

		int gx = x + 4;
		int gy = ty;

		// Legend
		graphics.drawString(font, SpellEditorLocalization.t("\u2588 tick"), gx, gy, 0xFF44FF44, false);
		graphics.drawString(font, SpellEditorLocalization.t("\u2588 render interval"), gx + 36, gy, 0xFF44CCCC, false);
		graphics.drawString(font, "-- 16ms (60fps)", gx + 112, gy, 0x88FFFF00, false);
		gy += 12;

		// Background
		graphics.fill(gx, gy, gx + graphW, gy + graphH, 0x88000000);

		// Find max for scaling
		long maxTick = 1_000_000; // 1ms minimum scale
		long maxRender = 1_000_000;
		for (int i = 0; i < SAMPLE_COUNT; i++) {
			if (tickTimeSamples[i] > maxTick) maxTick = tickTimeSamples[i];
			if (renderIntervalSamples[i] > maxRender) maxRender = renderIntervalSamples[i];
		}

		// Draw bars
		for (int i = 0; i < graphW; i++) {
			int si = (sampleIndex - graphW + i + SAMPLE_COUNT) % SAMPLE_COUNT;

			// Tick time bar (green/orange/red)
			long tNs = tickTimeSamples[si];
			int tH = (int) (tNs * (graphH - 1) / maxTick);
			tH = Math.min(tH, graphH - 1);
			if (tH > 0) {
				int color = tNs > 16_000_000 ? 0xFFFF4444 : (tNs > 8_000_000 ? 0xFFFFAA44 : 0xFF44FF44);
				graphics.fill(gx + i, gy + graphH - tH, gx + i + 1, gy + graphH, color);
			}

			// Render interval dot (cyan)
			long rNs = renderIntervalSamples[si];
			int rH = (int) (rNs * (graphH - 1) / maxRender);
			rH = Math.min(rH, graphH - 1);
			graphics.fill(gx + i, gy + graphH - rH - 1, gx + i + 1, gy + graphH - rH, 0xFF44CCCC);
		}

		// 16ms reference line
		int line16 = (int) (16_000_000L * (graphH - 1) / maxTick);
		if (line16 > 0 && line16 < graphH) {
			for (int i = 0; i < graphW; i += 3) {
				graphics.fill(gx + i, gy + graphH - line16, gx + i + 1, gy + graphH - line16 + 1, 0x88FFFF00);
			}
		}

		// Scale label
		String scaleLabel = SpellEditorLocalization.isChinese()
				? String.format("刻度: %.0fms", maxTick / 1_000_000.0)
				: String.format("scale: %.0fms", maxTick / 1_000_000.0);
		graphics.drawString(font, scaleLabel, gx, gy + graphH + 2, 0xFF666666, false);
	}

	// ---- Helpers ----

	private static double calcAvgMs(long[] samples) {
		long sum = 0;
		int count = 0;
		for (long s : samples) {
			if (s > 0) { sum += s; count++; }
		}
		return count > 0 ? sum / 1_000_000.0 / count : 0;
	}

	private static double calcMaxMs(long[] samples) {
		long max = 0;
		for (long s : samples) {
			if (s > max) max = s;
		}
		return max / 1_000_000.0;
	}
}
