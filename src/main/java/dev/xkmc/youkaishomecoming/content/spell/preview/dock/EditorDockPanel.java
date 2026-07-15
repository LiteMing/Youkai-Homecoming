package dev.xkmc.youkaishomecoming.content.spell.preview.dock;

import dev.xkmc.youkaishomecoming.content.spell.preview.ActionEditorPanel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 可停靠的属性编辑器面板。包装 {@link ActionEditorPanel}，
 * 处理 widget 生命周期和 dropdown overlay 渲染层级。
 */
@OnlyIn(Dist.CLIENT)
public class EditorDockPanel implements DockPanel {

	private final ActionEditorPanel panel;
	private int x, y, w, h;

	public EditorDockPanel(ActionEditorPanel panel) {
		this.panel = panel;
	}

	@Override
	public String dockTitle() {
		return "Properties";
	}

	@Override
	public String dockId() {
		return "properties";
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
		panel.render(graphics, mouseX, mouseY, partialTick, false);
	}

	/**
	 * 渲染 dropdown/补全覆盖层，需要在所有面板之上绘制。
	 * DockLayout 统一在 renderOverlay 阶段调用。
	 */
	@Override
	public void renderOverlay(GuiGraphics graphics, int mouseX, int mouseY) {
		panel.renderDropdown(graphics, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		return panel.mouseClicked(mouseX, mouseY, button);
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
	public void onActivated() {
		panel.setAllWidgetsVisible(true);
	}

	@Override
	public void onDeactivated() {
		panel.setAllWidgetsVisible(false);
	}

	/** 获取内部的 ActionEditorPanel 实例 */
	public ActionEditorPanel getPanel() {
		return panel;
	}
}
