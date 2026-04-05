package dev.xkmc.youkaishomecoming.content.spell.preview.dock;

import dev.xkmc.youkaishomecoming.content.spell.preview.ActionListPanel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 可停靠的节点树面板。薄包装 {@link ActionListPanel}，
 * 直接委托所有操作到内部面板。
 */
@OnlyIn(Dist.CLIENT)
public class ActionListDockPanel implements DockPanel {

	private final ActionListPanel panel;
	private int x, y, w, h;

	public ActionListDockPanel(ActionListPanel panel) {
		this.panel = panel;
	}

	@Override
	public String dockTitle() {
		return "Actions";
	}

	@Override
	public String dockId() {
		return "actions";
	}

	@Override
	public void setBounds(int x, int y, int w, int h) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
		panel.setBounds(x, y, w, h);
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
		panel.render(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		return panel.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		return panel.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		return panel.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
		return panel.mouseScrolled(mouseX, mouseY, delta);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		return panel.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char codePoint, int modifiers) {
		return panel.charTyped(codePoint, modifiers);
	}

	/** 获取内部的 ActionListPanel 实例 */
	public ActionListPanel getPanel() {
		return panel;
	}
}
