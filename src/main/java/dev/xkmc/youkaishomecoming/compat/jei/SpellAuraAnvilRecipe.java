package dev.xkmc.youkaishomecoming.compat.jei;

import dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem;
import dev.xkmc.youkaishomecoming.content.item.danmaku.SpellAuraItem;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellCardType;
import dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku;
import net.minecraft.world.item.ItemStack;

/** A display-only description of the event-driven spell aura anvil operation. */
public record SpellAuraAnvilRecipe(ItemStack input, ItemStack aura, ItemStack output, int experienceLevels) {

	public static SpellAuraAnvilRecipe of(SpellAuraItem auraItem, int levels) {
		ItemStack aura = new ItemStack(auraItem);
		ItemStack input = YHDanmaku.DYNAMIC_SPELL.asStack();
		ItemStack output = input.copy();
		if (auraItem.isEx()) {
			DynamicSpellItem.setExSpell(output, true);
		} else {
			DynamicSpellItem.setCardType(output, auraItem.type());
		}
		return new SpellAuraAnvilRecipe(input, aura, output, levels);
	}

	public boolean isEx() {
		return aura.getItem() instanceof SpellAuraItem item && item.isEx();
	}

	public SpellCardType cardType() {
		return aura.getItem() instanceof SpellAuraItem item ? item.type() : SpellCardType.NORMAL;
	}
}
