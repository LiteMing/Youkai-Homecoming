package dev.xkmc.youkaishomecoming.content.spell.preview;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 编辑器的两种工作模式。符卡编辑与魔法阵编辑共用同一个 Screen，
 * 但各自拥有独立的面板集合、顶栏按钮与停靠布局。
 *
 * <p>模式是 raw json 面板内容与 viewport 渲染路径的唯一真源；不要再从
 * “哪个 dock tab 处于激活状态”反推当前上下文。
 */
@OnlyIn(Dist.CLIENT)
public enum EditorMode {

	SPELL("spell", "Spell", "Mode: Spell"),
	MAGIC_CIRCLE("magic_circle", "Magic Circle", "Mode: Circle");

	private final String key;
	private final String displayName;
	private final String buttonLabel;

	EditorMode(String key, String displayName, String buttonLabel) {
		this.key = key;
		this.displayName = displayName;
		this.buttonLabel = buttonLabel;
	}

	/** 布局持久化使用的稳定键名，不随显示语言变化。 */
	public String key() {
		return key;
	}

	public String displayName() {
		return displayName;
	}

	/** 顶栏模式按钮上显示的文本（经 {@link SpellEditorLocalization#t} 翻译）。 */
	public String buttonLabel() {
		return buttonLabel;
	}

	/** 点击模式按钮后切换到的模式。 */
	public EditorMode next() {
		return this == SPELL ? MAGIC_CIRCLE : SPELL;
	}

}
