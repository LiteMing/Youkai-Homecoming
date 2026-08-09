package dev.xkmc.youkaishomecoming.content.spell.recipe;

import dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem;
import dev.xkmc.youkaishomecoming.content.item.danmaku.SpellItem;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Converts a boss-drop spell card (SpellItem carrying {@code spell_id} /
 * {@code single_use} / {@code yh_op_quota} NBT) into a bound dynamic-spell
 * draft. The draft inherits single-use and the OP node quota; right-clicking it
 * opens the spell editor. The certification chain enforces the quota.
 */
public class SpellDraftConversionRecipe extends CustomRecipe {

	public SpellDraftConversionRecipe(ResourceLocation id, CraftingBookCategory category) {
		super(id, category);
	}

	@Override
	public boolean matches(CraftingContainer container, Level level) {
		ItemStack drop = findDrop(container);
		return drop != null && !drop.isEmpty() && container.getContainerSize() == 1;
	}

	@Override
	public ItemStack assemble(CraftingContainer container, RegistryAccess access) {
		ItemStack drop = findDrop(container);
		if (drop == null || drop.isEmpty()) {
			return ItemStack.EMPTY;
		}
		ResourceLocation spellId = ResourceLocation.tryParse(
				drop.getOrCreateTag().getString("spell_id"));
		if (spellId == null) {
			return ItemStack.EMPTY;
		}
		return DynamicSpellItem.draftFromDrop(drop, spellId);
	}

	private static ItemStack findDrop(CraftingContainer container) {
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (stack.getItem() instanceof SpellItem
					&& stack.hasTag() && stack.getTag().contains("spell_id")) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	@Override
	public boolean canCraftInDimensions(int width, int height) {
		return width * height >= 1;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return SpellDraftConversionSerializer.INSTANCE;
	}

	public static class SpellDraftConversionSerializer extends SimpleCraftingRecipeSerializer<SpellDraftConversionRecipe> {

		public static final SpellDraftConversionSerializer INSTANCE = new SpellDraftConversionSerializer();

		private SpellDraftConversionSerializer() {
			super(SpellDraftConversionRecipe::new);
		}
	}
}
