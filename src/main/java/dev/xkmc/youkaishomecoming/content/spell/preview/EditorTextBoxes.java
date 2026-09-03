package dev.xkmc.youkaishomecoming.content.spell.preview;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.Screen;

public final class EditorTextBoxes {

	private static final int TEXT_COLOR = 0xFFE6E6E6;
	private static final int DISABLED_TEXT_COLOR = 0xFF888888;

	private EditorTextBoxes() {
	}

	public static EditBox configure(EditBox box) {
		box.setCanLoseFocus(true);
		box.setTextColor(TEXT_COLOR);
		box.setTextColorUneditable(DISABLED_TEXT_COLOR);
		collapseSelection(box);
		return box;
	}

	/** Creates the standard editor field with drag selection and RMB copy. */
	public static EditBox create(Font font, int x, int y, int width, int height, Component message) {
		return configure(new SelectableEditBox(font, x, y, width, height, message));
	}

	/**
	 * Vanilla EditBox supports keyboard selection but has no mouse-drag handler.
	 * This small wrapper supplies the expected text-field behaviour used by all
	 * editor docks without changing the widget's responder or formatting rules.
	 */
	private static final class SelectableEditBox extends EditBox {
		private int dragAnchor = -1;
		private boolean dragging;

		private SelectableEditBox(Font font, int x, int y, int width, int height, Component message) {
			super(font, x, y, width, height, message);
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			if (button == 1 && isMouseOver(mouseX, mouseY)) {
				String selected = getHighlighted();
				if (!selected.isEmpty()) {
					Minecraft.getInstance().keyboardHandler.setClipboard(selected);
				}
				return true;
			}
			if (button != 0) return false;
			boolean result = super.mouseClicked(mouseX, mouseY, button);
			if (result) {
				dragAnchor = getCursorPosition();
				dragging = true;
				if (!Screen.hasShiftDown()) setHighlightPos(dragAnchor);
			}
			return result;
		}

		@Override
		public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
			if (button != 0 || dragAnchor < 0 || !isFocused()) return false;
			if (dragging) {
				onClick(mouseX, mouseY);
				setHighlightPos(dragAnchor);
				return true;
			}
			return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
		}

		@Override
		public boolean mouseReleased(double mouseX, double mouseY, int button) {
			if (button == 0) {
				dragging = false;
				dragAnchor = -1;
			}
			return super.mouseReleased(mouseX, mouseY, button);
		}
	}

	public static void collapseSelection(EditBox box) {
		box.setHighlightPos(box.getCursorPosition());
	}

}
