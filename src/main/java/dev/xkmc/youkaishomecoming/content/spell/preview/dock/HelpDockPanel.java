package dev.xkmc.youkaishomecoming.content.spell.preview.dock;

import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class HelpDockPanel implements DockPanel {

	private static final String KEY_PREFIX = YoukaisHomecoming.MODID + ".spell_editor.help.";
	private static final int SURVIVAL_LINE_COUNT = 9;
	private static final int BUDGET_LINE_COUNT = 27;
	private static final int LINE_COUNT = 110;
	private static final int LINE_HEIGHT = 10;

	private int x, y, w, h;
	private int scrollOffset = 0;
	private boolean scrollbarDragging = false;

	private List<FormattedCharSequence> cachedLines;
	private Language cachedLanguage;
	private int cachedWidth = -1;

	private List<FormattedCharSequence> getLines() {
		Language language = Language.getInstance();
		int width = Math.max(1, w - 16);
		if (cachedLines != null && language == cachedLanguage && width == cachedWidth) return cachedLines;
		cachedLanguage = language;
		cachedWidth = width;
		List<String> paragraphs = new ArrayList<>();
		appendSection(paragraphs, "survival.", SURVIVAL_LINE_COUNT);
		appendSection(paragraphs, "budget.", BUDGET_LINE_COUNT);
		appendSection(paragraphs, "line.", LINE_COUNT);
		while (!paragraphs.isEmpty() && paragraphs.get(paragraphs.size() - 1).isEmpty()) {
			paragraphs.remove(paragraphs.size() - 1);
		}
		Font font = Minecraft.getInstance().font;
		cachedLines = new ArrayList<>();
		for (String paragraph : paragraphs) {
			if (paragraph.isEmpty()) cachedLines.add(FormattedCharSequence.EMPTY);
			else cachedLines.addAll(font.split(Component.literal(paragraph), width));
		}
		return cachedLines;
	}

	private static void appendSection(List<String> lines, String section, int count) {
		for (int i = 0; i < count; i++) {
			String key = KEY_PREFIX + section + i;
			String value = I18n.get(key);
			lines.add(value.equals(key) ? "" : value);
		}
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
		List<FormattedCharSequence> lines = getLines();

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

		int lineH = LINE_HEIGHT;
		int actualLines = lines.size();

		int maxScroll = Math.max(0, actualLines * lineH - contentH);
		scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));

		for (int i = 0; i < actualLines; i++) {
			int ly = contentY + i * lineH - scrollOffset;
			if (ly + lineH < contentY || ly > contentY + contentH) continue;
			graphics.drawString(font, lines.get(i), x + 8, ly, 0xFFCCCCCC, false);
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
		int actualLines = getLines().size();
		int contentY = y + 18;
		int contentH = h - 22;
		int maxScroll = Math.max(0, actualLines * LINE_HEIGHT - contentH);
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
			int actualLines = getLines().size();
			int contentY = y + 18;
			int contentH = h - 22;
			int maxScroll = Math.max(0, actualLines * LINE_HEIGHT - contentH);
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
		int lineH = LINE_HEIGHT;
		int thumbH = Math.max(10, trackH * contentH / (lineCount * lineH));
		int thumbTravel = trackH - thumbH;
		if (thumbTravel <= 0) return;
		double relY = mouseY - (contentY + 1) - thumbH / 2.0;
		double ratio = Math.max(0, Math.min(1, relY / thumbTravel));
		scrollOffset = (int) (ratio * maxScroll);
	}
}
