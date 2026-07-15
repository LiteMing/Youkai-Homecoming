package dev.xkmc.youkaishomecoming.content.spell.preview.dock;

import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class HelpDockPanel implements DockPanel {

	private static final String KEY_PREFIX = YoukaisHomecoming.MODID + ".spell_editor.help.";
	private static final int LINE_COUNT = 110;

	private int x, y, w, h;
	private int scrollOffset = 0;
	private boolean scrollbarDragging = false;

	private String[] cachedLines = null;
	private String cachedLang = null;

	private String[] getLines() {
		String lang = Minecraft.getInstance().getLanguageManager().getSelected();
		if (cachedLines != null && lang.equals(cachedLang)) return cachedLines;
		cachedLang = lang;
		cachedLines = new String[LINE_COUNT];
		for (int i = 0; i < LINE_COUNT; i++) {
			String key = KEY_PREFIX + "line." + i;
			String val = I18n.get(key);
			cachedLines[i] = val.equals(key) ? "" : val;
		}
		return cachedLines;
	}

	@Override
	public String dockTitle() {
		return I18n.get(KEY_PREFIX + "title");
	}

	@Override
	public String dockId() {
		return "help";
	}

	@Override
	public void setBounds(int x, int y, int w, int h) {
		this.x = x; this.y = y; this.w = w; this.h = h;
	}

	@Override public int getX() { return x; }
	@Override public int getY() { return y; }
	@Override public int getWidth() { return w; }
	@Override public int getHeight() { return h; }

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		Font font = Minecraft.getInstance().font;
		String[] lines = getLines();

		graphics.fill(x, y, x + w, y + h, 0xEE111122);
		graphics.fill(x, y, x + w, y + 1, 0xFF444488);
		graphics.fill(x, y + h - 1, x + w, y + h, 0xFF444488);
		graphics.fill(x, y, x + 1, y + h, 0xFF444488);
		graphics.fill(x + w - 1, y, x + w, y + h, 0xFF444488);

		String title = I18n.get(KEY_PREFIX + "title");
		graphics.drawString(font, title, x + (w - font.width(title)) / 2, y + 4, 0xFFFFFF88, false);

		int contentY = y + 18;
		int contentH = h - 22;
		graphics.enableScissor(x + 4, contentY, x + w - 8, contentY + contentH);

		int lineH = 10;
		int actualLines = lines.length;
		while (actualLines > 0 && lines[actualLines - 1].isEmpty()) actualLines--;

		int maxScroll = Math.max(0, actualLines * lineH - contentH);
		scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));

		for (int i = 0; i < actualLines; i++) {
			int ly = contentY + i * lineH - scrollOffset;
			if (ly + lineH < contentY || ly > contentY + contentH) continue;
			graphics.drawString(font, lines[i], x + 8, ly, 0xFFCCCCCC, false);
		}
		graphics.disableScissor();

		if (maxScroll > 0) {
			int sbX = x + w - 6;
			int trackH = contentH - 2;
			int thumbH = Math.max(10, trackH * contentH / (actualLines * lineH));
			int thumbY = contentY + 1 + (trackH - thumbH) * scrollOffset / maxScroll;
			graphics.fill(sbX, contentY, sbX + 4, contentY + contentH, 0x33FFFFFF);
			graphics.fill(sbX + 1, thumbY, sbX + 3, thumbY + thumbH, 0x88AAAACC);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0 || !isMouseOver(mouseX, mouseY)) return false;
		String[] lines = getLines();
		int actualLines = lines.length;
		while (actualLines > 0 && lines[actualLines - 1].isEmpty()) actualLines--;
		int contentY = y + 18;
		int contentH = h - 22;
		int maxScroll = Math.max(0, actualLines * 10 - contentH);
		if (maxScroll > 0) {
			int sbX = x + w - 6;
			if (mouseX >= sbX && mouseX < sbX + 4) {
				scrollbarDragging = true;
				updateScrollbarDrag(mouseY, maxScroll, contentY, contentH, actualLines);
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (scrollbarDragging && button == 0) {
			String[] lines = getLines();
			int actualLines = lines.length;
			while (actualLines > 0 && lines[actualLines - 1].isEmpty()) actualLines--;
			int contentY = y + 18;
			int contentH = h - 22;
			int maxScroll = Math.max(0, actualLines * 10 - contentH);
			updateScrollbarDrag(mouseY, maxScroll, contentY, contentH, actualLines);
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (scrollbarDragging && button == 0) {
			scrollbarDragging = false;
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
		if (!isMouseOver(mouseX, mouseY)) return false;
		scrollOffset -= (int) (delta * 30);
		return true;
	}

	private void updateScrollbarDrag(double mouseY, int maxScroll, int contentY, int contentH, int lineCount) {
		if (maxScroll <= 0) return;
		int trackH = contentH - 2;
		int lineH = 10;
		int thumbH = Math.max(10, trackH * contentH / (lineCount * lineH));
		int thumbTravel = trackH - thumbH;
		if (thumbTravel <= 0) return;
		double relY = mouseY - (contentY + 1) - thumbH / 2.0;
		double ratio = Math.max(0, Math.min(1, relY / thumbTravel));
		scrollOffset = (int) (ratio * maxScroll);
	}
}
