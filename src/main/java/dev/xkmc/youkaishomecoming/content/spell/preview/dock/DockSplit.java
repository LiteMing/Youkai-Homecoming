package dev.xkmc.youkaishomecoming.content.spell.preview.dock;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;

/**
 * 二叉分割节点。将区域水平或垂直分割为两个子区域，
 * 每个子区域可以是 {@link DockGroup} 或另一个 {@link DockSplit}。
 */
@OnlyIn(Dist.CLIENT)
public final class DockSplit implements DockNode {

	/** 分割线像素宽度 */
	public static final int DIVIDER_SIZE = 4;
	private static final int DIVIDER_COLOR = 0xFF1E1E1E;
	private static final int DIVIDER_HOVER_COLOR = 0xFF5599FF;
	private static final float MIN_RATIO = 0.1f;
	private static final float MAX_RATIO = 0.9f;
	private static final int MIN_CHILD_SIZE = 50;

	private boolean horizontal; // true = 左右分割（divider 竖线）, false = 上下分割（divider 横线）
	private float ratio;        // 分割比例 [0.1, 0.9]
	private DockNode first;     // horizontal: 左侧 / vertical: 上方
	private DockNode second;    // horizontal: 右侧 / vertical: 下方

	private int x, y, w, h;

	// 分割线拖拽状态
	private boolean dividerHovered = false;
	private boolean dividerDragging = false;

	public DockSplit(boolean horizontal, float ratio, DockNode first, DockNode second) {
		this.horizontal = horizontal;
		this.ratio = clampRatio(ratio);
		this.first = first;
		this.second = second;
	}

	// ---- 属性访问 ----

	public boolean isHorizontal() {
		return horizontal;
	}

	public float getRatio() {
		return ratio;
	}

	public void setRatio(float ratio) {
		this.ratio = clampRatio(ratio);
	}

	public DockNode getFirst() {
		return first;
	}

	public void setFirst(DockNode first) {
		this.first = first;
	}

	public DockNode getSecond() {
		return second;
	}

	public void setSecond(DockNode second) {
		this.second = second;
	}

	public boolean isDividerDragging() {
		return dividerDragging;
	}

	// ---- DockNode 实现 ----

	@Override
	public void layout(int x, int y, int w, int h) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;

		if (horizontal) {
			int firstW = (int) ((w - DIVIDER_SIZE) * ratio);
			firstW = Math.max(MIN_CHILD_SIZE, Math.min(w - DIVIDER_SIZE - MIN_CHILD_SIZE, firstW));
			int secondX = x + firstW + DIVIDER_SIZE;
			int secondW = w - firstW - DIVIDER_SIZE;

			first.layout(x, y, firstW, h);
			second.layout(secondX, y, secondW, h);
		} else {
			int firstH = (int) ((h - DIVIDER_SIZE) * ratio);
			firstH = Math.max(MIN_CHILD_SIZE, Math.min(h - DIVIDER_SIZE - MIN_CHILD_SIZE, firstH));
			int secondY = y + firstH + DIVIDER_SIZE;
			int secondH = h - firstH - DIVIDER_SIZE;

			first.layout(x, y, w, firstH);
			second.layout(x, secondY, w, secondH);
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
		first.render(graphics, mouseX, mouseY, partialTick);
		second.render(graphics, mouseX, mouseY, partialTick);

		// 渲染分割线
		updateDividerHover(mouseX, mouseY);
		int color = (dividerHovered || dividerDragging) ? DIVIDER_HOVER_COLOR : DIVIDER_COLOR;
		int[] divBounds = getDividerBounds();
		graphics.fill(divBounds[0], divBounds[1], divBounds[0] + divBounds[2], divBounds[1] + divBounds[3], color);
	}

	@Override
	public void renderOverlay(GuiGraphics graphics, int mouseX, int mouseY) {
		first.renderOverlay(graphics, mouseX, mouseY);
		second.renderOverlay(graphics, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (!isMouseOver(mouseX, mouseY)) return false;

		// 检查是否点击在分割线上
		if (button == 0 && isDividerHit(mouseX, mouseY)) {
			dividerDragging = true;
			return true;
		}

		// 转发给子节点
		if (first.isMouseOver(mouseX, mouseY)) {
			return first.mouseClicked(mouseX, mouseY, button);
		}
		if (second.isMouseOver(mouseX, mouseY)) {
			return second.mouseClicked(mouseX, mouseY, button);
		}
		return false;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (dividerDragging && button == 0) {
			if (horizontal) {
				float newRatio = (float) (mouseX - x) / (w - DIVIDER_SIZE);
				setRatio(newRatio);
			} else {
				float newRatio = (float) (mouseY - y) / (h - DIVIDER_SIZE);
				setRatio(newRatio);
			}
			layout(x, y, w, h); // 重新布局
			return true;
		}

		if (first.isMouseOver(mouseX, mouseY)) {
			return first.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
		}
		if (second.isMouseOver(mouseX, mouseY)) {
			return second.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
		}
		return false;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (dividerDragging && button == 0) {
			dividerDragging = false;
			return true;
		}

		// 两个子节点都需要收到释放事件
		boolean handled = false;
		handled |= first.mouseReleased(mouseX, mouseY, button);
		handled |= second.mouseReleased(mouseX, mouseY, button);
		return handled;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
		if (first.isMouseOver(mouseX, mouseY)) {
			return first.mouseScrolled(mouseX, mouseY, delta);
		}
		if (second.isMouseOver(mouseX, mouseY)) {
			return second.mouseScrolled(mouseX, mouseY, delta);
		}
		return false;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		// 键盘事件转发给包含焦点的子节点 — 这里转发给两侧
		boolean handled = first.keyPressed(keyCode, scanCode, modifiers);
		if (!handled) {
			handled = second.keyPressed(keyCode, scanCode, modifiers);
		}
		return handled;
	}

	@Override
	public boolean charTyped(char codePoint, int modifiers) {
		boolean handled = first.charTyped(codePoint, modifiers);
		if (!handled) {
			handled = second.charTyped(codePoint, modifiers);
		}
		return handled;
	}

	@Override
	@Nullable
	public DockGroup findGroupAt(double mouseX, double mouseY) {
		if (!isMouseOver(mouseX, mouseY)) return null;

		DockGroup g = first.findGroupAt(mouseX, mouseY);
		if (g != null) return g;
		return second.findGroupAt(mouseX, mouseY);
	}

	@Override
	@Nullable
	public DockGroup findGroupContaining(DockPanel panel) {
		DockGroup g = first.findGroupContaining(panel);
		if (g != null) return g;
		return second.findGroupContaining(panel);
	}

	// ---- 分割线 hit-test ----

	/**
	 * 返回分割线的 [x, y, w, h]。
	 */
	public int[] getDividerBounds() {
		if (horizontal) {
			int divX = x + (int) ((w - DIVIDER_SIZE) * ratio);
			return new int[]{divX, y, DIVIDER_SIZE, h};
		} else {
			int divY = y + (int) ((h - DIVIDER_SIZE) * ratio);
			return new int[]{x, divY, w, DIVIDER_SIZE};
		}
	}

	private boolean isDividerHit(double mouseX, double mouseY) {
		int[] d = getDividerBounds();
		return mouseX >= d[0] && mouseX < d[0] + d[2]
				&& mouseY >= d[1] && mouseY < d[1] + d[3];
	}

	private void updateDividerHover(int mouseX, int mouseY) {
		dividerHovered = isDividerHit(mouseX, mouseY);
	}

	private static float clampRatio(float r) {
		return Math.max(MIN_RATIO, Math.min(MAX_RATIO, r));
	}

	// ---- 树操作辅助 ----

	/**
	 * 用新节点替换当前的子节点。用于 dock 操作后重建分割树。
	 *
	 * @return true 如果替换成功
	 */
	public boolean replaceChild(DockNode oldChild, DockNode newChild) {
		if (first == oldChild) {
			first = newChild;
			return true;
		}
		if (second == oldChild) {
			second = newChild;
			return true;
		}
		return false;
	}
}
