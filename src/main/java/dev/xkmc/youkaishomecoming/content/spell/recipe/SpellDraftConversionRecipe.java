package dev.xkmc.youkaishomecoming.content.spell.recipe;

import dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem;
import dev.xkmc.youkaishomecoming.init.data.YHTagGen;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.Level;

import java.util.Map;

/**
 * Custom_spell-style conversion: a preset spell card (boss drop or creative
 * preset, {@code youkaishomecoming:preset_spell}) + paper + ink sac yields a
 * bound dynamic-spell draft. The draft inherits single-use and derives its
 * special-node quota from the boss's own spell definition. Right-clicking the
 * draft opens the editor; the certification chain enforces the quota.
 */
public class SpellDraftConversionRecipe extends CustomRecipe {

	/** Preset item -> registered spell definition id (fallback when no NBT). */
	private static final Map<Item, ResourceLocation> PRESET_SPELL_IDS = Map.ofEntries(
			Map.entry(dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku.REIMU_SPELL.get(),
					new ResourceLocation("touhou_little_maid", "hakurei_reimu")),
			Map.entry(dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku.MARISA_SPELL.get(),
					new ResourceLocation("touhou_little_maid", "kirisame_marisa")),
			Map.entry(dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku.SANAE_SPELL.get(),
					new ResourceLocation("touhou_little_maid", "kochiya_sanae")),
			Map.entry(dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku.MYSTIA_SPELL.get(),
					new ResourceLocation("touhou_little_maid", "mystia_lorelei")),
			Map.entry(dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku.KOISHI_SPELL.get(),
					new ResourceLocation("touhou_little_maid", "komeiji_koishi")),
			Map.entry(dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku.REMILIA_SPELL.get(),
					new ResourceLocation("touhou_little_maid", "remilia_scarlet")),
			Map.entry(dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku.YUKARI_SPELL_LASER.get(),
					new ResourceLocation("touhou_little_maid", "yukari_yakumo")),
			Map.entry(dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku.YUKARI_SPELL_BUTTERFLY.get(),
					new ResourceLocation("touhou_little_maid", "yukari_yakumo")),
			Map.entry(dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku.CLOWNPIECE_SPELL.get(),
					new ResourceLocation("touhou_little_maid", "clownpiece"))
	);

	public SpellDraftConversionRecipe(ResourceLocation id, CraftingBookCategory category) {
		super(id, category);
	}

	@Override
	public boolean matches(CraftingContainer container, Level level) {
		ItemStack card = null;
		boolean paper = false;
		boolean ink = false;
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (stack.isEmpty()) continue;
			if (stack.is(YHTagGen.PRESET_SPELL)) {
				if (card != null) return false;
				card = stack;
			} else if (stack.is(Items.PAPER)) {
				if (paper) return false;
				paper = true;
			} else if (stack.is(Items.INK_SAC)) {
				if (ink) return false;
				ink = true;
			} else {
				return false;
			}
		}
		return card != null && paper && ink && resolveSpellId(card) != null;
	}

	@Override
	public ItemStack assemble(CraftingContainer container, RegistryAccess access) {
		ItemStack card = null;
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (!stack.isEmpty() && stack.is(YHTagGen.PRESET_SPELL)) {
				card = stack;
				break;
			}
		}
		if (card == null) {
			return ItemStack.EMPTY;
		}
		ResourceLocation spellId = resolveSpellId(card);
		if (spellId == null) {
			return ItemStack.EMPTY;
		}
		return DynamicSpellItem.draftFromDrop(card, spellId);
	}

	/** NBT spell_id (boss drop) first, then the static preset-item mapping. */
	private static ResourceLocation resolveSpellId(ItemStack card) {
		if (card.hasTag() && card.getTag().contains("spell_id")) {
			ResourceLocation id = ResourceLocation.tryParse(card.getTag().getString("spell_id"));
			if (id != null) {
				return id;
			}
		}
		return PRESET_SPELL_IDS.get(card.getItem());
	}

	@Override
	public boolean canCraftInDimensions(int width, int height) {
		return width * height >= 3;
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
