package dev.xkmc.youkaishomecoming.compat.jei;

import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import dev.xkmc.youkaishomecoming.init.data.YHLangData;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.network.chat.Component;

/** JEI view for the Forge AnvilUpdateEvent-backed aura conversion. */
public final class SpellAuraAnvilCategory implements IRecipeCategory<SpellAuraAnvilRecipe> {
	public static final RecipeType<SpellAuraAnvilRecipe> TYPE = RecipeType.create(
			YoukaisHomecoming.MODID, "spell_aura_anvil", SpellAuraAnvilRecipe.class);

	private final mezz.jei.api.gui.drawable.IDrawable background;
	private final mezz.jei.api.gui.drawable.IDrawable icon;

	public SpellAuraAnvilCategory(IGuiHelper helper) {
		background = helper.createBlankDrawable(140, 40);
		icon = helper.createDrawableIngredient(VanillaTypes.ITEM_STACK,
				dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku.NON_SPELL_AURA.asStack());
	}

	@Override
	public RecipeType<SpellAuraAnvilRecipe> getRecipeType() {
		return TYPE;
	}

	@Override
	public Component getTitle() {
		return YHLangData.JEI_SPELL_AURA.get();
	}

	@Override
	public mezz.jei.api.gui.drawable.IDrawable getBackground() {
		return background;
	}

	@Override
	public mezz.jei.api.gui.drawable.IDrawable getIcon() {
		return icon;
	}

	@Override
	public void setRecipe(IRecipeLayoutBuilder builder, SpellAuraAnvilRecipe recipe, IFocusGroup focuses) {
		builder.addSlot(RecipeIngredientRole.INPUT, 10, 11)
				.setStandardSlotBackground().addItemStack(recipe.input());
		builder.addSlot(RecipeIngredientRole.INPUT, 46, 11)
				.setStandardSlotBackground().addItemStack(recipe.aura());
		builder.addSlot(RecipeIngredientRole.OUTPUT, 106, 11)
				.setOutputSlotBackground().addItemStack(recipe.output());
	}

	@Override
	public void createRecipeExtras(IRecipeExtrasBuilder builder, SpellAuraAnvilRecipe recipe, IFocusGroup focuses) {
		builder.addRecipeArrow().setPosition(76, 12);
		builder.addText(Component.translatable("container.repair.cost", recipe.experienceLevels()), 52, 30);
	}

}
