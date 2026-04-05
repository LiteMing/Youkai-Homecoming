package dev.xkmc.youkaishomecoming.content.spell.preview.dock;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 可停靠面板接口。每个编辑器面板实现此接口后即可参与 Dock 布局系统。
 */
@OnlyIn(Dist.CLIENT)
public interface DockPanel {

	/** Tab 栏显示的面板标题 */
	String dockTitle();

	/** 面板的唯一标识符，用于布局持久化 */
	String dockId();

	/** 设置面板的屏幕空间边界 */
	void setBounds(int x, int y, int w, int h);

	int getX();

	int getY();

	int getWidth();

	int getHeight();

	/** 渲染面板主体内容 */
	void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

	/** 渲染需要绘制在所有面板之上的覆盖层（如 dropdown、补全弹出） */
	default void renderOverlay(GuiGraphics graphics, int mouseX, int mouseY) {
	}

	default boolean mouseClicked(double mouseX, double mouseY, int button) {
		return false;
	}

	default boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		return false;
	}

	default boolean mouseReleased(double mouseX, double mouseY, int button) {
		return false;
	}

	default boolean mouseScrolled(double mouseX, double mouseY, double delta) {
		return false;
	}

	default boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		return false;
	}

	default boolean charTyped(char codePoint, int modifiers) {
		return false;
	}

	/** 鼠标是否在面板区域内 */
	default boolean isMouseOver(double mouseX, double mouseY) {
		return mouseX >= getX() && mouseX < getX() + getWidth()
				&& mouseY >= getY() && mouseY < getY() + getHeight();
	}

	/** 当面板成为当前活跃 Tab 时调用 */
	default void onActivated() {
	}

	/** 当面板失去当前活跃 Tab 时调用 */
	default void onDeactivated() {
	}
}
