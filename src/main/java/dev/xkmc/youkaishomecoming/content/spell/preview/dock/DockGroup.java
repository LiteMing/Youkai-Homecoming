package dev.xkmc.youkaishomecoming.content.spell.preview.dock;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 标签页容器。持有多个 {@link DockPanel}，通过 Tab 栏切换当前活跃面板。
 * 作为 {@link DockNode} 的叶子节点参与分割树。
 */
@OnlyIn(Dist.CLIENT)
public final class DockGroup implements DockNode {

	public static final int TAB_HEIGHT = 16;
	private static final int TAB_PADDING = 4;
	private static final int TAB_MIN_WIDTH = 40;
	private static final int TAB_MAX_WIDTH = 100;

	private static final int TAB_BG_COLOR = 0xFF2D2D2D;
	private static final int TAB_ACTIVE_COLOR = 0xFF3C3C3C;
	private static final int TAB_HOVER_COLOR = 0xFF353535;
	private static final int TAB_TEXT_COLOR = 0xFFCCCCCC;
	private static final int TAB_ACTIVE_TEXT_COLOR = 0xFFFFFFFF;
	private static final int TAB_UNDERLINE_COLOR = 0xFF5599FF;

	private final List<DockPanel> panels = new ArrayList<>();
	private int activeIndex = 0;

	private int x, y, w, h;

	// Tab 拖拽状态
	private int dragTabIndex = -1;
	private double dragStartX, dragStartY;
	private boolean dragInitiated = false;

	public DockGroup(DockPanel... initialPanels) {
		for (DockPanel p : initialPanels) {
			panels.add(p);
		}
	}

	// ---- 面板管理 ----

	public void addPanel(DockPanel panel) {
		panels.add(panel);
		if (panels.size() == 1) {
			activeIndex = 0;
			panel.onActivated();
		}
		layoutPanelBounds();
	}

	public void addPanel(int index, DockPanel panel) {
		panels.add(index, panel);
		if (panels.size() == 1) {
			activeIndex = 0;
			panel.onActivated();
		} else if (activeIndex >= index) {
			activeIndex++;
		}
		layoutPanelBounds();
	}

	public boolean removePanel(DockPanel panel) {
		int idx = panels.indexOf(panel);
		if (idx < 0) return false;
		boolean wasActive = (idx == activeIndex);
		if (wasActive) panel.onDeactivated();
		panels.remove(idx);
		if (activeIndex > idx || activeIndex >= panels.size()) {
			activeIndex = Math.max(0, Math.min(activeIndex - 1, panels.size() - 1));
		}
		if (wasActive && !panels.isEmpty()) {
			panels.get(activeIndex).onActivated();
		}
		return true;
	}

	public void setActiveIndex(int index) {
		if (index < 0 || index >= panels.size() || index == activeIndex) return;
		panels.get(activeIndex).onDeactivated();
		activeIndex = index;
		panels.get(activeIndex).onActivated();
	}

	public int getActiveIndex() {
		return activeIndex;
	}

	@Nullable
	public DockPanel getActivePanel() {
		if (panels.isEmpty()) return null;
		return panels.get(activeIndex);
	}

	public List<DockPanel> getPanels() {
		return java.util.Collections.unmodifiableList(panels);
	}

	public boolean isEmpty() {
		return panels.isEmpty();
	}

	public int getPanelCount() {
		return panels.size();
	}

	public boolean containsPanel(DockPanel panel) {
		return panels.contains(panel);
	}

	// ---- Tab 拖拽查询 ----

	public boolean isDragInitiated() {
		return dragInitiated;
	}

	public int getDragTabIndex() {
		return dragTabIndex;
	}

	public void clearDragState() {
		dragTabIndex = -1;
		dragInitiated = false;
	}

	// ---- DockNode 实现 ----

	@Override
	public void layout(int x, int y, int w, int h) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
		layoutPanelBounds();
	}

	private void layoutPanelBounds() {
		int panelY = y + TAB_HEIGHT;
		int panelH = Math.max(0, h - TAB_HEIGHT);
		for (DockPanel p : panels) {
			p.setBounds(x, panelY, w, panelH);
		}
	}

	@Override
	public int getX() {
		return x;
	}

	@Override
	public int getY() {
		return y;
	}

	@Override
	public int getWidth() {
		return w;
	}

	@Override
	public int getHeight() {
		return h;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		if (panels.isEmpty()) return;

		renderTabBar(graphics, mouseX, mouseY);

		DockPanel active = panels.get(activeIndex);
		active.render(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public void renderOverlay(GuiGraphics graphics, int mouseX, int mouseY) {
		if (panels.isEmpty()) return;
		panels.get(activeIndex).renderOverlay(graphics, mouseX, mouseY);
	}

	private void renderTabBar(GuiGraphics graphics, int mouseX, int mouseY) {
		// Tab 栏背景
		graphics.fill(x, y, x + w, y + TAB_HEIGHT, TAB_BG_COLOR);

		Font font = Minecraft.getInstance().font;
		int tabX = x;

		for (int i = 0; i < panels.size(); i++) {
			DockPanel panel = panels.get(i);
			String title = panel.dockTitle();
			int textWidth = font.width(title);
			int tabWidth = Math.min(TAB_MAX_WIDTH, Math.max(TAB_MIN_WIDTH, textWidth + TAB_PADDING * 2));

			// 如果超出宽度则截断
			if (tabX + tabWidth > x + w) break;

			boolean isActive = (i == activeIndex);
			boolean isHovered = mouseX >= tabX && mouseX < tabX + tabWidth
					&& mouseY >= y && mouseY < y + TAB_HEIGHT;

			// Tab 背景
			int bgColor = isActive ? TAB_ACTIVE_COLOR : (isHovered ? TAB_HOVER_COLOR : TAB_BG_COLOR);
			graphics.fill(tabX, y, tabX + tabWidth, y + TAB_HEIGHT, bgColor);

			// 活跃 Tab 底部高亮线
			if (isActive) {
				graphics.fill(tabX, y + TAB_HEIGHT - 2, tabX + tabWidth, y + TAB_HEIGHT, TAB_UNDERLINE_COLOR);
			}

			// Tab 标题文字
			int textColor = isActive ? TAB_ACTIVE_TEXT_COLOR : TAB_TEXT_COLOR;
			int textX = tabX + (tabWidth - textWidth) / 2;
			int textY = y + (TAB_HEIGHT - font.lineHeight) / 2;
			graphics.drawString(font, title, textX, textY, textColor, false);

			tabX += tabWidth;
		}
	}

	// ---- 事件分发 ----

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (!isMouseOver(mouseX, mouseY)) return false;

		// Tab 栏点击
		if (mouseY >= y && mouseY < y + TAB_HEIGHT && button == 0) {
			int tabIndex = getTabIndexAt(mouseX);
			if (tabIndex >= 0) {
				setActiveIndex(tabIndex);
				// 记录拖拽起点
				dragTabIndex = tabIndex;
				dragStartX = mouseX;
				dragStartY = mouseY;
				dragInitiated = false;
				return true;
			}
		}

		// 转发给活跃面板
		DockPanel active = getActivePanel();
		if (active != null) {
			return active.mouseClicked(mouseX, mouseY, button);
		}
		return false;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		// Tab 拖拽检测
		if (dragTabIndex >= 0 && button == 0) {
			double dist = Math.abs(mouseX - dragStartX) + Math.abs(mouseY - dragStartY);
			if (dist > 4) {
				dragInitiated = true;
				return true; // 拖拽由 DockLayout 接管
			}
			return true;
		}

		DockPanel active = getActivePanel();
		if (active != null) {
			return active.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
		}
		return false;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (dragTabIndex >= 0 && button == 0) {
			dragTabIndex = -1;
			dragInitiated = false;
			return true; // 拖拽释放已处理，不转发给面板
		}

		DockPanel active = getActivePanel();
		if (active != null) {
			return active.mouseReleased(mouseX, mouseY, button);
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
		if (!isMouseOver(mouseX, mouseY)) return false;

		DockPanel active = getActivePanel();
		if (active != null) {
			return active.mouseScrolled(mouseX, mouseY, delta);
		}
		return false;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		DockPanel active = getActivePanel();
		if (active != null) {
			return active.keyPressed(keyCode, scanCode, modifiers);
		}
		return false;
	}

	@Override
	public boolean charTyped(char codePoint, int modifiers) {
		DockPanel active = getActivePanel();
		if (active != null) {
			return active.charTyped(codePoint, modifiers);
		}
		return false;
	}

	@Override
	@Nullable
	public DockGroup findGroupAt(double mouseX, double mouseY) {
		if (isMouseOver(mouseX, mouseY)) return this;
		return null;
	}

	@Override
	@Nullable
	public DockGroup findGroupContaining(DockPanel panel) {
		if (panels.contains(panel)) return this;
		return null;
	}

	// ---- Tab hit-test ----

	/**
	 * 根据鼠标 X 坐标找到对应的 Tab 索引。
	 *
	 * @return Tab 索引，-1 表示不在任何 Tab 上
	 */
	public int getTabIndexAt(double mouseX) {
		Font font = Minecraft.getInstance().font;
		int tabX = x;
		for (int i = 0; i < panels.size(); i++) {
			String title = panels.get(i).dockTitle();
			int textWidth = font.width(title);
			int tabWidth = Math.min(TAB_MAX_WIDTH, Math.max(TAB_MIN_WIDTH, textWidth + TAB_PADDING * 2));
			if (tabX + tabWidth > x + w) break;
			if (mouseX >= tabX && mouseX < tabX + tabWidth) {
				return i;
			}
			tabX += tabWidth;
		}
		return -1;
	}

	/** Tab 栏区域的 Y 范围 */
	public boolean isInTabBar(double mouseY) {
		return mouseY >= y && mouseY < y + TAB_HEIGHT;
	}
}
