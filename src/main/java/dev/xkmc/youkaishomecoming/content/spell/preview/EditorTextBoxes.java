package dev.xkmc.youkaishomecoming.content.spell.preview;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class EditorTextBoxes {

	private static final int TEXT_COLOR = 0xFFE6E6E6;
	private static final int DISABLED_TEXT_COLOR = 0xFF888888;
	private static final String COPIED_KEY = "youkaishomecoming.spell_editor.copied";
	private static SelectableEditBox activeSelectionBox;

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

	/** Shows the short-lived in-game overlay used for editor clipboard feedback. */
	public static void notifyCopied() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player != null) {
			minecraft.player.displayClientMessage(Component.translatable(COPIED_KEY), true);
		}
	}

	/** Collapse the last editor selection before another text widget receives focus. */
	public static void clearActiveSelection() {
		SelectableEditBox box = activeSelectionBox;
		if (box != null) {
			box.collapseSelection();
		}
		activeSelectionBox = null;
	}

	/**
	 * Vanilla EditBox supports keyboard selection but has no mouse-drag handler.
	 * This small wrapper supplies the expected text-field behaviour used by all
	 * editor docks without changing the widget's responder or formatting rules.
	 */
	private static final class SelectableEditBox extends EditBox {
		private static final int HISTORY_LIMIT = 100;
		private final List<String> undoHistory = new ArrayList<>();
		private final List<String> redoHistory = new ArrayList<>();
		private Consumer<String> changeListener = ignored -> {};
		private String lastHistoryValue = "";
		private boolean userInput;
		private boolean applyingHistory;
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
					notifyCopied();
				}
				return true;
			}
			if (button != 0) return false;
			clearActiveSelection();
			boolean result = super.mouseClicked(mouseX, mouseY, button);
			if (result) {
				dragAnchor = getCursorPosition();
				dragging = true;
				if (!Screen.hasShiftDown()) setHighlightPos(dragAnchor);
			}
			return result;
		}

		@Override
		public void setResponder(Consumer<String> responder) {
			changeListener = responder == null ? ignored -> {} : responder;
			super.setResponder(value -> {
				if (userInput && !applyingHistory) {
					recordUserChange(value);
				}
				changeListener.accept(value);
			});
		}

		@Override
		public void setValue(String value) {
			super.setValue(value);
			if (!userInput && !applyingHistory) {
				resetUndoHistory(value);
			}
		}

		@Override
		public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
			if (handleUndoRedoKey(keyCode)) {
				return true;
			}
			// Keep vanilla Ctrl+C clipboard semantics, but provide the same feedback
			// as the editor's right-click copy shortcut.
			if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_C) {
				boolean hasSelection = !getHighlighted().isEmpty();
				boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
				if (handled && hasSelection) {
					notifyCopied();
				}
				return handled;
			}
			userInput = true;
			try {
				return super.keyPressed(keyCode, scanCode, modifiers);
			} finally {
				userInput = false;
			}
		}

		@Override
		public boolean charTyped(char codePoint, int modifiers) {
			userInput = true;
			try {
				return super.charTyped(codePoint, modifiers);
			} finally {
				userInput = false;
			}
		}

		private boolean handleUndoRedoKey(int keyCode) {
			if (!Screen.hasControlDown()) {
				return false;
			}
			if (keyCode == GLFW.GLFW_KEY_Z && Screen.hasShiftDown()) {
				return redoEdit();
			}
			if (keyCode == GLFW.GLFW_KEY_Z) {
				return undoEdit();
			}
			if (keyCode == GLFW.GLFW_KEY_Y) {
				return redoEdit();
			}
			return false;
		}

		@Override
		public void setFocused(boolean focused) {
			if (focused) {
				clearActiveSelection();
				activeSelectionBox = this;
			} else {
				collapseSelection();
				if (activeSelectionBox == this) {
					activeSelectionBox = null;
				}
			}
			super.setFocused(focused);
		}

		private void collapseSelection() {
			setHighlightPos(getCursorPosition());
			dragAnchor = -1;
			dragging = false;
		}

		private void resetUndoHistory(String value) {
			undoHistory.clear();
			redoHistory.clear();
			lastHistoryValue = value == null ? "" : value;
		}

		private void recordUserChange(String value) {
			String next = value == null ? "" : value;
			if (next.equals(lastHistoryValue)) {
				return;
			}
			undoHistory.add(lastHistoryValue);
			if (undoHistory.size() > HISTORY_LIMIT) {
				undoHistory.remove(0);
			}
			redoHistory.clear();
			lastHistoryValue = next;
		}

		private boolean undoEdit() {
			if (undoHistory.isEmpty()) {
				return false;
			}
			String current = getValue();
			String previous = undoHistory.remove(undoHistory.size() - 1);
			redoHistory.add(current);
			applyHistoryValue(previous);
			return true;
		}

		private boolean redoEdit() {
			if (redoHistory.isEmpty()) {
				return false;
			}
			String current = getValue();
			String next = redoHistory.remove(redoHistory.size() - 1);
			undoHistory.add(current);
			if (undoHistory.size() > HISTORY_LIMIT) {
				undoHistory.remove(0);
			}
			applyHistoryValue(next);
			return true;
		}

		private void applyHistoryValue(String value) {
			applyingHistory = true;
			try {
				setValue(value);
			} finally {
				applyingHistory = false;
			}
			lastHistoryValue = value == null ? "" : value;
			setHighlightPos(getCursorPosition());
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
