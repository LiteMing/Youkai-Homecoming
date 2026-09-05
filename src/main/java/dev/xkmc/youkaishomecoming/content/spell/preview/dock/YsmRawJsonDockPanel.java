package dev.xkmc.youkaishomecoming.content.spell.preview.dock;

import dev.xkmc.youkaishomecoming.content.spell.preview.YsmEditorController;
import dev.xkmc.youkaishomecoming.content.spell.preview.YsmEditorDocument;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import static dev.xkmc.youkaishomecoming.content.spell.preview.YsmEditorController.text;

/** Reuses the spell editor's clipboard, multiline selection and undo widget for the YSM draft. */
public final class YsmRawJsonDockPanel implements DockPanel {
	private final YsmEditorController controller;
	private RawJsonDockPanel.RawJsonEditBox editor;
	private Button apply, revert;
	private int x, y, w, h;
	private boolean suppressChange, editable;

	public YsmRawJsonDockPanel(YsmEditorController controller) { this.controller = controller; }
	@Override public String dockId() { return "ysm_raw_json"; }
	@Override public String dockTitle() { return "Raw JSON"; }
	@Override public int getX() { return x; }
	@Override public int getY() { return y; }
	@Override public int getWidth() { return w; }
	@Override public int getHeight() { return h; }
	@Override public void setBounds(int x, int y, int w, int h) {
		boolean resize = this.w != w || this.h != h;
		this.x = x; this.y = y; this.w = w; this.h = h;
		if (w < 20 || h < 70) return;
		if (editor == null || resize) createEditor();
		else { editor.setX(x + 5); editor.setY(y + 30); }
		int half = Math.max(20, (w - 12) / 2);
		apply = Button.builder(text("raw_apply"), ignored -> controller.applyRawDraft()).bounds(x + 4, y + 4, half, 20).build();
		revert = Button.builder(text("raw_revert"), ignored -> {
			controller.discardRawDraft();
			controller.prepareRawDraft();
		}).bounds(x + 8 + half, y + 4, half, 20).build();
	}
	private void createEditor() {
		var previous = editor;
		boolean focused = editor != null && editor.isFocused();
		editor = new RawJsonDockPanel.RawJsonEditBox(Minecraft.getInstance().font, x + 5, y + 30,
				Math.max(10, w - 10), Math.max(10, h - 82), Component.empty(), Component.literal("Raw JSON"));
		editor.setCharacterLimit(YsmEditorDocument.MAX_JSON_LENGTH);
		setText(controller.rawJson());
		editor.setValueListener(value -> {
			if (suppressChange) return;
			editor.recordUserChange(value);
			controller.rawJson(value);
		});
		editor.setFocused(focused);
		editor.restoreEditingState(previous);
	}
	private void setText(String value) {
		suppressChange = true;
		try { editor.setValue(value); editor.resetUndoHistory(value); }
		finally { suppressChange = false; }
	}
	@Override public void onActivated() { controller.prepareRawDraft(); }
	@Override public void onDeactivated() { if (editor != null) editor.setFocused(false); }
	@Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(x, y, x + w, y + h, 0xff1c2027);
		if (editor == null || w < 20 || h < 70) return;
		if (!controller.rawDirty() && !editor.getValue().equals(controller.rawJson())) setText(controller.rawJson());
		editable = canEdit();
		apply.active = editable && controller.rawDirty();
		revert.active = !controller.waiting() && controller.profile() != null;
		graphics.enableScissor(x, y, x + w, y + h);
		apply.render(graphics, mouseX, mouseY, partialTick);
		revert.render(graphics, mouseX, mouseY, partialTick);
		editor.render(graphics, mouseX, mouseY, partialTick);
		var font = Minecraft.getInstance().font;
		Component status = controller.profile() == null ? text("select_model_first") : controller.status();
		int top = y + h - 45;
		for (var line : font.split(status, Math.max(20, w - 12))) {
			if (top > y + h - 25) break;
			graphics.drawString(font, line, x + 5, top, 0xffc6d4df, false); top += 10;
		}
		graphics.drawString(font, font.plainSubstrByWidth(text("raw_help").getString(), Math.max(20, w - 12)), x + 5, y + h - 12, 0xff93abbc, false);
		graphics.disableScissor();
	}
	@Override public boolean mouseClicked(double mx, double my, int button) {
		if (!isMouseOver(mx, my) || editor == null) return false;
		if (apply.mouseClicked(mx, my, button) || revert.mouseClicked(mx, my, button)) return true;
		// Read the draft state at input time too; a parameter change may precede this frame's render.
		boolean handled = canEdit() && editor.mouseClicked(mx, my, button);
		if (!handled) editor.setFocused(false);
		return true;
	}
	@Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
		return editor != null && editor.isFocused() && canEdit() && editor.mouseDragged(mx, my, button, dx, dy);
	}
	@Override public boolean mouseReleased(double mx, double my, int button) {
		return editor != null && editor.mouseReleased(mx, my, button);
	}
	@Override public boolean mouseScrolled(double mx, double my, double amount) {
		return editor != null && isMouseOver(mx, my) && editor.mouseScrolled(mx, my, amount);
	}
	@Override public boolean keyPressed(int key, int scan, int modifiers) {
		if (editor == null || !editor.isFocused()) return false;
		if (key == GLFW.GLFW_KEY_ESCAPE) { editor.setFocused(false); return true; }
		return canEdit() && editor.keyPressed(key, scan, modifiers);
	}
	@Override public boolean charTyped(char character, int modifiers) {
		return editor != null && editor.isFocused() && canEdit() && editor.charTyped(character, modifiers);
	}
	private boolean canEdit() { return !controller.waiting() && controller.rawEditable(); }
}
