package dev.xkmc.youkaishomecoming.content.spell.preview.dock;

import dev.xkmc.youkaishomecoming.content.spell.preview.YsmEditorController;

import static dev.xkmc.youkaishomecoming.content.spell.preview.YsmEditorController.text;

public final class YsmHelpDockPanel extends YsmEditorPanel {
	public YsmHelpDockPanel(YsmEditorController editor) { super(editor); }
	@Override public String dockId() { return "ysm_help"; }
	@Override public String dockTitle() { return text("help").getString(); }
	@Override protected void build() {
		for (int i = 1; i <= 9; i++) label(text("help." + i));
	}
}
