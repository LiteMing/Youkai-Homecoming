package dev.xkmc.youkaishomecoming.content.spell.item;

import com.google.gson.JsonObject;
import dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpecialNodeCounter;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellAnalysisLimits;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellAnalyzer;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellDraftBudget;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellHealthPlan;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRegistry;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Shapeless draft-card recipe whose output budget is resolved at craft time. */
public class SpellDraftRecipe extends ShapelessRecipe {

	public static final ResourceLocation SERIALIZER_ID =
			new ResourceLocation(YoukaisHomecoming.MODID, "spell_draft");
	private static final Set<ResourceLocation> WARNED_MISSING = ConcurrentHashMap.newKeySet();
	@Nullable
	private final ResourceLocation bossSpell;

	public SpellDraftRecipe(ResourceLocation id, String group, CraftingBookCategory category,
			ItemStack result, NonNullList<Ingredient> ingredients, @Nullable ResourceLocation bossSpell) {
		super(id, group, category, result, ingredients);
		this.bossSpell = bossSpell;
	}

	@Override
	public ItemStack assemble(CraftingContainer container, RegistryAccess access) {
		ItemStack stack = super.assemble(container, access);
		int baseTier = 1;
		int addedBossSpells = 0;
		ItemStack existingCard = ItemStack.EMPTY;
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack item = container.getItem(i);
			if (item.getItem() instanceof DynamicSpellItem) {
				baseTier = Math.max(baseTier, DynamicSpellItem.getRank(item).tierNumber());
				existingCard = item;
			} else if (item.getItem() instanceof dev.xkmc.youkaishomecoming.content.item.danmaku.SpellItem) {
				addedBossSpells++;
			}
		}
		if (bossSpell != null) {
			addedBossSpells = Math.max(1, addedBossSpells);
		}
		int targetTier = baseTier + (addedBossSpells > 0 ? addedBossSpells : 0);
		dev.xkmc.youkaishomecoming.content.spell.analysis.SpellCardRank rank =
				dev.xkmc.youkaishomecoming.content.spell.analysis.SpellCardRank.fromTier(targetTier);
		
		// 若是基于已有成品符卡升级或水洗，解除成品与认证状态，退回草稿可修改状态
		if (!existingCard.isEmpty() && existingCard.hasTag()) {
			stack.setTag(existingCard.getTag().copy());
			DynamicSpellItem.setComplete(stack, false);
			if (stack.getTag() != null) {
				stack.getTag().remove(dev.xkmc.youkaishomecoming.content.spell.certification.CertifiedSpellValidator.TAG_CERTIFIED_HASH);
				stack.getTag().remove(dev.xkmc.youkaishomecoming.content.spell.certification.CertifiedSpellValidator.TAG_CERTIFICATE_ID);
				stack.getTag().remove(dev.xkmc.youkaishomecoming.content.spell.certification.CertifiedSpellValidator.TAG_CERTIFIED_DURATION);
				stack.getTag().remove(dev.xkmc.youkaishomecoming.content.spell.certification.CertifiedSpellValidator.TAG_CERTIFIED_COST);
			}
		}
		DynamicSpellItem.setRank(stack, rank);
		DynamicSpellItem.setDraftBudget(stack, rank.createBudget());
		return stack;
	}

	@Override
	public ItemStack getResultItem(RegistryAccess access) {
		ItemStack stack = super.getResultItem(access).copy();
		int tier = bossSpell != null ? 2 : 1;
		dev.xkmc.youkaishomecoming.content.spell.analysis.SpellCardRank rank =
				dev.xkmc.youkaishomecoming.content.spell.analysis.SpellCardRank.fromTier(tier);
		DynamicSpellItem.setRank(stack, rank);
		DynamicSpellItem.setDraftBudget(stack, rank.createBudget());
		return stack;
	}

	private SpellDraftBudget resolveBudget() {
		SpellDraftBudget base = SpellDraftBudget.defaults();
		if (bossSpell == null) return base;
		var definition = SpellRegistry.get(bossSpell);
		if (definition == null) {
			if (WARNED_MISSING.add(bossSpell)) {
				YoukaisHomecoming.LOGGER.warn("Boss draft {} has no migrated spell definition; using default budget", bossSpell);
			}
			return base;
		}
		try {
			var limits = SpellAnalysisLimits.certification();
			var plan = SpellHealthPlan.analyzeIfPresent(definition, SpellRegistry::get);
			if (plan.isPresent() && plan.get().totalDurationTicks() > 0) {
				limits = limits.withCertificationWindow(Math.min(limits.certificationWindowTicks(),
						plan.get().totalDurationTicks()));
			}
			var analysis = SpellAnalyzer.analyzePreview(definition, limits);
			return base.expandedForBoss(analysis, SpecialNodeCounter.summarize(definition));
		} catch (IllegalArgumentException e) {
			if (WARNED_MISSING.add(bossSpell)) {
				YoukaisHomecoming.LOGGER.warn("Boss draft {} cannot be analyzed; using default budget: {}",
						bossSpell, e.getMessage());
			}
			return base;
		}
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return Serializer.INSTANCE;
	}

	public static final class Serializer implements RecipeSerializer<SpellDraftRecipe> {

		public static final Serializer INSTANCE = new Serializer();

		@Override
		public SpellDraftRecipe fromJson(ResourceLocation id, JsonObject json) {
			ShapelessRecipe base = RecipeSerializer.SHAPELESS_RECIPE.fromJson(id, json);
			ResourceLocation boss = json.has("boss_spell")
					? ResourceLocation.tryParse(GsonHelper.getAsString(json, "boss_spell")) : null;
			return wrap(base, boss);
		}

		@Override
		public SpellDraftRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
			ShapelessRecipe base = RecipeSerializer.SHAPELESS_RECIPE.fromNetwork(id, buffer);
			ResourceLocation boss = buffer.readBoolean() ? buffer.readResourceLocation() : null;
			return wrap(base, boss);
		}

		@Override
		public void toNetwork(FriendlyByteBuf buffer, SpellDraftRecipe recipe) {
			RecipeSerializer.SHAPELESS_RECIPE.toNetwork(buffer, recipe);
			buffer.writeBoolean(recipe.bossSpell != null);
			if (recipe.bossSpell != null) buffer.writeResourceLocation(recipe.bossSpell);
		}

		private static SpellDraftRecipe wrap(ShapelessRecipe base, @Nullable ResourceLocation boss) {
			NonNullList<Ingredient> ingredients = NonNullList.create();
			ingredients.addAll(base.getIngredients());
			return new SpellDraftRecipe(base.getId(), base.getGroup(), base.category(),
					base.getResultItem(RegistryAccess.EMPTY).copy(), ingredients, boss);
		}
	}
}
