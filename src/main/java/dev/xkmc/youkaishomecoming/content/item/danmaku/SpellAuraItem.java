package dev.xkmc.youkaishomecoming.content.item.danmaku;

import dev.xkmc.youkaishomecoming.content.spell.definition.SpellCardType;
import net.minecraft.world.item.Item;

/** One-use crafting reagent that changes an unfinished card's declared type. */
public class SpellAuraItem extends Item {
	private final SpellCardType type;
	private final boolean ex;

	public SpellAuraItem(Properties properties, SpellCardType type) {
		this(properties, type, false);
	}

	public SpellAuraItem(Properties properties, SpellCardType type, boolean ex) {
		super(properties.stacksTo(16));
		this.type = type;
		this.ex = ex;
	}

	public SpellCardType type() {
		return type;
	}

	public boolean isEx() {
		return ex;
	}
}
