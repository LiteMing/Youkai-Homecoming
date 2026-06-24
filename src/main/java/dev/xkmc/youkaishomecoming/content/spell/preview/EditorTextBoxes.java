package dev.xkmc.youkaishomecoming.content.spell.preview;

import net.minecraft.client.gui.components.EditBox;

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

	public static void collapseSelection(EditBox box) {
		box.setHighlightPos(box.getCursorPosition());
	}

}
