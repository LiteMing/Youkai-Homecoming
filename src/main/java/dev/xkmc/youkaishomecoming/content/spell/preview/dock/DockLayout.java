package dev.xkmc.youkaishomecoming.content.spell.preview.dock;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;

/**
 * Dock 布局管理器。持有分割树的根节点，负责全局 layout 计算、
 * 事件分发、拖拽吸附检测和 dock 操作执行。
 */
@OnlyIn(Dist.CLIENT)
public class DockLayout {

	private static final int PREVIEW_COLOR = 0x405599FF; // 半透明蓝色吸附预览

	private DockNode root;
	private int x, y, w, h;

	// 拖拽状态
	private boolean dragging = false;
	@Nullable
	private DockPanel dragPanel = null;
	@Nullable
	private DockGroup dragSourceGroup = null;
	private double dragMouseX, dragMouseY;
	@Nullable
	private DockGroup dragTargetGroup = null;
	@Nullable
	private DockZone dragTargetZone = null;

	// 活跃面板追踪（用于键盘事件路由）
	@Nullable
	private DockGroup activeGroup = null;
	/**
	 * Dock group that owns the current mouse/keyboard focus. This is separate from
	 * {@link #activeGroup}: setup may select a tab without giving it a visible
	 * interaction focus until the user actually clicks it.
	 */
	@Nullable
	private DockGroup focusedGroup = null;

	public DockLayout(DockNode root) {
		this.root = root;
	}

	public DockNode getRoot() {
		return root;
	}

	public void setRoot(DockNode root) {
		this.root = root;
		this.focusedGroup = null;
	}

	// ---- 布局 ----

	public void layout(int x, int y, int w, int h) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
		root.layout(x, y, w, h);
		syncActivePanelStates();
	}

	public void relayout() {
		root.layout(x, y, w, h);
		syncActivePanelStates();
	}

	// ---- 渲染 ----

	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		root.render(graphics, mouseX, mouseY, partialTick);

		// 拖拽吸附预览
		if (dragging && dragTargetGroup != null && dragTargetZone != null) {
			int[] preview = computePreviewRect(dragTargetGroup, dragTargetZone);
			graphics.fill(preview[0], preview[1], preview[0] + preview[2], preview[1] + preview[3], PREVIEW_COLOR);
		}

		// 拖拽时鼠标旁的面板标题
		if (dragging && dragPanel != null) {
			Font font = Minecraft.getInstance().font;
			String title = dragPanel.dockTitle();
			int tx = (int) dragMouseX + 8;
			int ty = (int) dragMouseY - 4;
			graphics.fill(tx - 2, ty - 1, tx + font.width(title) + 2, ty + font.lineHeight + 1, 0xCC222222);
			graphics.drawString(font, title, tx, ty, 0xFFFFFFFF, false);
		}
	}

	public void renderOverlay(GuiGraphics graphics, int mouseX, int mouseY) {
		root.renderOverlay(graphics, mouseX, mouseY);
		renderFocusIndicator(graphics);
	}

	/**
	 * Draw one consistent focus frame around the currently focused dock group.
	 * Rendering at the layout overlay layer keeps it above viewport content,
	 * dropdowns, completion lists and other dock-specific overlays.
	 */
	private void renderFocusIndicator(GuiGraphics graphics) {
		DockGroup group = focusedGroup;
		DockPanel panel = group == null ? null : group.getActivePanel();
		if (panel == null) return;
		int gx = panel.getX();
		int gy = panel.getY();
		int gw = panel.getWidth();
		int gh = panel.getHeight();
		graphics.pose().pushPose();
		graphics.pose().translate(0, 0, 500);
		int glow = 0x805599FF;
		int blue = 0xFF55AAFF;
		graphics.renderOutline(gx - 3, gy - 3, gw + 6, gh + 6, glow);
		graphics.fill(gx - 2, gy - 2, gx + gw + 2, gy, blue);
		graphics.fill(gx - 2, gy + gh, gx + gw + 2, gy + gh + 2, blue);
		graphics.fill(gx - 2, gy, gx, gy + gh, blue);
		graphics.fill(gx + gw, gy, gx + gw + 2, gy + gh, blue);
		graphics.renderOutline(gx, gy, gw, gh, blue);
		graphics.pose().popPose();
	}

	// ---- 事件分发 ----

	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		// Any mouse button establishes dock focus. Viewport orbit/pan commonly starts
		// with RMB/MMB, and those gestures must unfocus edit boxes just like LMB.
		DockGroup clicked = root.findGroupAt(mouseX, mouseY);
		if (clicked != null && clicked != activeGroup) {
			activeGroup = clicked;
		}
		boolean handled = root.mouseClicked(mouseX, mouseY, button);
		if (clicked != null) {
			// A tab click may switch the active panel during dispatch; the group is
			// still the interaction owner regardless of which tab became active.
			focusedGroup = clicked;
		}
		return handled;
	}

	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		// 检查是否有 DockGroup 发起了 Tab 拖拽
		if (!dragging && button == 0) {
			DockGroup source = findDraggingGroup(root);
			if (source != null && source.isDragInitiated()) {
				startDrag(source);
			}
		}

		if (dragging) {
			dragMouseX = mouseX;
			dragMouseY = mouseY;
			updateDragTarget(mouseX, mouseY);
			return true;
		}

		// 普通拖拽转发（包括分割线拖拽）
		return root.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (dragging && button == 0) {
			executeDrop();
			return true;
		}
		return root.mouseReleased(mouseX, mouseY, button);
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
		return root.mouseScrolled(mouseX, mouseY, delta);
	}

	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		// 键盘事件优先发给活跃 Group
		if (activeGroup != null) {
			if (activeGroup.keyPressed(keyCode, scanCode, modifiers)) {
				return true;
			}
		}
		return false;
	}

	public boolean charTyped(char codePoint, int modifiers) {
		if (activeGroup != null) {
			if (activeGroup.charTyped(codePoint, modifiers)) {
				return true;
			}
		}
		return false;
	}

	// ---- 活跃面板管理 ----

	@Nullable
	public DockGroup getActiveGroup() {
		return activeGroup;
	}

	public void setActiveGroup(@Nullable DockGroup group) {
		this.activeGroup = group;
	}

	/** Mark a dock group as focused without changing its active tab. */
	public void setFocusedGroup(@Nullable DockGroup group) {
		this.focusedGroup = group;
	}

	/** Mark the group containing a panel as focused. */
	public void focusPanel(@Nullable DockPanel panel) {
		this.focusedGroup = panel == null ? null : findGroupContaining(panel);
	}

	/** Clear the visible dock focus frame. */
	public void clearFocusedGroup() {
		this.focusedGroup = null;
	}

	/** The panel currently owning the visible dock focus, if any. */
	@Nullable
	public DockPanel getFocusedPanel() {
		return focusedGroup == null ? null : focusedGroup.getActivePanel();
	}

	@Nullable
	public DockPanel getActivePanel() {
		return activeGroup != null ? activeGroup.getActivePanel() : null;
	}

	/** 查找包含指定面板的 DockGroup */
	@Nullable
	public DockGroup findGroupContaining(DockPanel panel) {
		return root.findGroupContaining(panel);
	}

	public void syncActivePanelStates() {
		syncActivePanelStates(root);
	}

	private void syncActivePanelStates(DockNode node) {
		if (node instanceof DockGroup group) {
			group.syncActivePanelState();
			return;
		}
		if (node instanceof DockSplit split) {
			syncActivePanelStates(split.getFirst());
			syncActivePanelStates(split.getSecond());
		}
	}

	// ---- 拖拽系统 ----

	private void startDrag(DockGroup source) {
		int tabIdx = source.getDragTabIndex();
		if (tabIdx < 0 || tabIdx >= source.getPanelCount()) return;

		dragPanel = source.getPanels().get(tabIdx);
		dragSourceGroup = source;
		dragging = true;
		source.clearDragState();
	}

	private void updateDragTarget(double mouseX, double mouseY) {
		DockGroup target = root.findGroupAt(mouseX, mouseY);
		if (target != null) {
			double relX = mouseX - target.getX();
			double relY = mouseY - target.getY();
			dragTargetGroup = target;
			dragTargetZone = DockZone.detect(relX, relY, target.getWidth(), target.getHeight());
		} else {
			dragTargetGroup = null;
			dragTargetZone = null;
		}
	}

	private void executeDrop() {
		if (dragPanel == null || dragSourceGroup == null) {
			cancelDrag();
			return;
		}

		if (dragTargetGroup != null && dragTargetZone != null) {
			if (dragTargetZone == DockZone.CENTER) {
				// 添加为目标 Group 的新 Tab
				if (dragTargetGroup != dragSourceGroup || dragSourceGroup.getPanelCount() > 1) {
					dragSourceGroup.removePanel(dragPanel);
					dragTargetGroup.addPanel(dragPanel);
					dragTargetGroup.setActiveIndex(dragTargetGroup.getPanelCount() - 1);
					cleanupEmptyGroups();
				}
			} else {
				// 创建新 Split
				executeSplitDock();
			}
			activeGroup = dragTargetGroup;
		}

		cancelDrag();
		relayout();
	}

	private void executeSplitDock() {
		if (dragPanel == null || dragSourceGroup == null || dragTargetGroup == null || dragTargetZone == null)
			return;

		// 从源 Group 移除面板
		dragSourceGroup.removePanel(dragPanel);

		// 创建新 Group 容纳被拖拽的面板
		DockGroup newGroup = new DockGroup(dragPanel);

		// 确定分割方向和顺序
		boolean horizontal = (dragTargetZone == DockZone.LEFT || dragTargetZone == DockZone.RIGHT);
		boolean newFirst = (dragTargetZone == DockZone.LEFT || dragTargetZone == DockZone.TOP);
		float splitRatio = newFirst ? 0.3f : 0.7f;

		DockNode firstChild = newFirst ? newGroup : dragTargetGroup;
		DockNode secondChild = newFirst ? dragTargetGroup : newGroup;
		DockSplit newSplit = new DockSplit(horizontal, splitRatio, firstChild, secondChild);

		// 替换分割树中的目标 Group
		replaceNodeInTree(dragTargetGroup, newSplit);

		dragTargetGroup = newGroup;

		// 清理空 Group
		cleanupEmptyGroups();
	}

	/**
	 * 在分割树中将 oldNode 替换为 newNode。
	 */
	private void replaceNodeInTree(DockNode oldNode, DockNode newNode) {
		if (root == oldNode) {
			root = newNode;
			return;
		}
		replaceNodeRecursive(root, oldNode, newNode);
	}

	private boolean replaceNodeRecursive(DockNode current, DockNode oldNode, DockNode newNode) {
		if (current instanceof DockSplit split) {
			if (split.getFirst() == oldNode) {
				split.setFirst(newNode);
				return true;
			}
			if (split.getSecond() == oldNode) {
				split.setSecond(newNode);
				return true;
			}
			return replaceNodeRecursive(split.getFirst(), oldNode, newNode)
					|| replaceNodeRecursive(split.getSecond(), oldNode, newNode);
		}
		return false;
	}

	/**
	 * 清理分割树中的空 DockGroup（移除面板后可能产生）。
	 * 如果某个 DockSplit 的一个子节点是空 Group，用另一个子节点替代该 Split。
	 */
	private void cleanupEmptyGroups() {
		root = cleanupNode(root);
	}

	private DockNode cleanupNode(DockNode node) {
		if (node instanceof DockSplit split) {
			split.setFirst(cleanupNode(split.getFirst()));
			split.setSecond(cleanupNode(split.getSecond()));

			// 如果某一侧为空 Group，提升另一侧
			if (split.getFirst() instanceof DockGroup g && g.isEmpty()) {
				return split.getSecond();
			}
			if (split.getSecond() instanceof DockGroup g && g.isEmpty()) {
				return split.getFirst();
			}
		}
		return node;
	}

	private void cancelDrag() {
		dragging = false;
		dragPanel = null;
		dragSourceGroup = null;
		dragTargetGroup = null;
		dragTargetZone = null;
	}

	@Nullable
	private DockGroup findDraggingGroup(DockNode node) {
		if (node instanceof DockGroup group) {
			if (group.isDragInitiated()) return group;
			return null;
		}
		if (node instanceof DockSplit split) {
			DockGroup g = findDraggingGroup(split.getFirst());
			if (g != null) return g;
			return findDraggingGroup(split.getSecond());
		}
		return null;
	}

	// ---- 吸附预览计算 ----

	private int[] computePreviewRect(DockGroup target, DockZone zone) {
		int tx = target.getX();
		int ty = target.getY();
		int tw = target.getWidth();
		int th = target.getHeight();

		return switch (zone) {
			case CENTER -> new int[]{tx, ty, tw, th};
			case LEFT -> new int[]{tx, ty, tw / 3, th};
			case RIGHT -> new int[]{tx + tw * 2 / 3, ty, tw / 3, th};
			case TOP -> new int[]{tx, ty, tw, th / 3};
			case BOTTOM -> new int[]{tx, ty + th * 2 / 3, tw, th / 3};
		};
	}

	// ---- 公共辅助 ----

	public boolean isDragging() {
		return dragging;
	}
}
