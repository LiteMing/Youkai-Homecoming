package dev.xkmc.youkaishomecoming.content.spell.registry;

import dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRegistry;
import dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;

/**
 * Populates creative tabs with DynamicSpellItem stacks for all SpellDefinitions
 * that have itemForm.generate() == true.
 */
public class SpellItemAutoRegister {

	private static final Comparator<ResourceLocation> SPELL_ID_ORDER =
			Comparator.comparing(ResourceLocation::getNamespace)
					.thenComparing(ResourceLocation::getPath);

	/**
	 * Call during creative tab build event to add dynamic spell item stacks.
	 */
	public static void populateCreativeTab(CreativeModeTab.Output output) {
		SpellRegistry.getAll().entrySet().stream()
				.sorted(java.util.Map.Entry.comparingByKey(SPELL_ID_ORDER))
				.filter(entry -> entry.getValue().itemForm.generate())
				.forEach(entry -> output.accept(createStack(entry.getKey())));
	}

	/**
	 * Development/testing tab population: expose every registered spell as a ready-to-cast stack,
	 * regardless of itemForm.generate().
	 */
	public static void populateTestingTab(CreativeModeTab.Output output) {
		SpellRegistry.getAll().keySet().stream()
				.sorted(SPELL_ID_ORDER)
				.forEach(id -> output.accept(createStack(id)));
	}

	private static ItemStack createStack(ResourceLocation spellId) {
		return DynamicSpellItem.createStack(YHDanmaku.DYNAMIC_SPELL.get(), spellId);
	}
}
