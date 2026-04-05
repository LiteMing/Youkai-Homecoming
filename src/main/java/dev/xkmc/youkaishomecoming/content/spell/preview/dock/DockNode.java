package dev.xkmc.youkaishomecoming.content.spell.preview.dock;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;

/**
 * Dock 分割树的多态节点。叶子节点为 {@link DockGroup}，
 * 内部节点为 {@link DockSplit}。
 */
@OnlyIn(Dist.CLIENT)
public sealed interface DockNode permits DockGroup, DockSplit {

	/** 递归计算子区域的 bounds */
	void layout(int x, int y, int w, int h);

	int getX();

	int getY();

	int getWidth();

	int getHeight();

	/** 渲染此节点及所有子节点 */
	void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

	/** 渲染覆盖层（dropdown 等） */
	void renderOverlay(GuiGraphics graphics, int mouseX, int mouseY);

	boolean mouseClicked(double mouseX, double mouseY, int button);

	boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY);

	boolean mouseReleased(double mouseX, double mouseY, int button);

	boolean mouseScrolled(double mouseX, double mouseY, double delta);

	boolean keyPressed(int keyCode, int scanCode, int modifiers);

	boolean charTyped(char codePoint, int modifiers);

	/** 查找鼠标所在位置的 DockGroup（用于拖拽吸附检测） */
	@Nullable
	DockGroup findGroupAt(double mouseX, double mouseY);

	/** 查找包含指定 DockPanel 的 DockGroup */
	@Nullable
	DockGroup findGroupContaining(DockPanel panel);

	/** 判定鼠标是否在此节点区域内 */
	default boolean isMouseOver(double mouseX, double mouseY) {
		return mouseX >= getX() && mouseX < getX() + getWidth()
				&& mouseY >= getY() && mouseY < getY() + getHeight();
	}
}
