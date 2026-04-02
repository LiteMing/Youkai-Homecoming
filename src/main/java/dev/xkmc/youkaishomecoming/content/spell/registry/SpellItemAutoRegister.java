package dev.xkmc.youkaishomecoming.content.spell.registry;

import dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRegistry;
import dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/**
 * Populates creative tabs with DynamicSpellItem stacks for all SpellDefinitions
 * that have itemForm.generate() == true.
 */
public class SpellItemAutoRegister {

	/**
	 * Call during creative tab build event to add dynamic spell item stacks.
	 */
	public static void populateCreativeTab(CreativeModeTab.Output output) {
		for (var entry : SpellRegistry.getAll().entrySet()) {
			SpellDefinition def = entry.getValue();
			if (def.itemForm.generate()) {
				ItemStack stack = DynamicSpellItem.createStack(
						YHDanmaku.DYNAMIC_SPELL.get(), entry.getKey());
				output.accept(stack);
			}
		}
	}
}
