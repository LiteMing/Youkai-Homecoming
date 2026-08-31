package dev.xkmc.youkaishomecoming.content.spell.item;

import com.google.gson.JsonObject;
import dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem;
import dev.xkmc.youkaishomecoming.content.item.danmaku.SpellAuraItem;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellCardType;
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
		SpellCardType auraType = null;
		boolean exAura = false;
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack item = container.getItem(i);
			if (item.getItem() instanceof DynamicSpellItem) {
				baseTier = Math.max(baseTier, DynamicSpellItem.getRank(item).tierNumber());
				existingCard = item;
			} else if (item.getItem() instanceof dev.xkmc.youkaishomecoming.content.item.danmaku.SpellItem) {
				addedBossSpells++;
			} else if (item.getItem() instanceof SpellAuraItem aura) {
				if (!aura.isEx()) auraType = aura.type();
				exAura |= aura.isEx();
			}
		}
		if (bossSpell != null) {
			addedBossSpells = Math.max(1, addedBossSpells);
		}
		int targetTier = baseTier + (addedBossSpells > 0 ? addedBossSpells : 0);
		boolean auraConversion = !existingCard.isEmpty() && (auraType != null || exAura)
				&& addedBossSpells == 0 && bossSpell == null;
		dev.xkmc.youkaishomecoming.content.spell.analysis.SpellCardRank rank =
				dev.xkmc.youkaishomecoming.content.spell.analysis.SpellCardRank.fromTier(targetTier);
		
		// Aura conversion is only legal before certification. Preserve the bound
		// draft and its budget instead of laundering a completed certificate.
		if (!existingCard.isEmpty() && existingCard.hasTag()) {
			stack.setTag(existingCard.getTag().copy());
			if (!auraConversion) {
				// Preserve the historical wash/upgrade behavior. Only aura conversion
				// refuses completed or certified cards.
				DynamicSpellItem.setComplete(stack, false);
				stack.getTag().remove(dev.xkmc.youkaishomecoming.content.spell.certification.CertifiedSpellValidator.TAG_CERTIFIED_HASH);
				stack.getTag().remove(dev.xkmc.youkaishomecoming.content.spell.certification.CertifiedSpellValidator.TAG_CERTIFICATE_ID);
				stack.getTag().remove(dev.xkmc.youkaishomecoming.content.spell.certification.CertifiedSpellValidator.TAG_CERTIFIED_DURATION);
				stack.getTag().remove(dev.xkmc.youkaishomecoming.content.spell.certification.CertifiedSpellValidator.TAG_CERTIFIED_COST);
			}
		}
		DynamicSpellItem.setRank(stack, rank);
		if (!auraConversion) DynamicSpellItem.setDraftBudget(stack, rank.createBudget());
		if (auraType != null) DynamicSpellItem.setCardType(stack, auraType);
		if (exAura) DynamicSpellItem.setExSpell(stack, true);
		if (DynamicSpellItem.getCardType(stack) == SpellCardType.NON_SPELL
				&& DynamicSpellItem.isExSpell(stack)) return ItemStack.EMPTY;
		return stack;
	}

	@Override
	public boolean matches(CraftingContainer container, net.minecraft.world.level.Level level) {
		if (!super.matches(container, level)) return false;
		ItemStack card = ItemStack.EMPTY;
		boolean ex = false;
		SpellCardType type = null;
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (stack.getItem() instanceof DynamicSpellItem) card = stack;
			if (stack.getItem() instanceof SpellAuraItem aura) {
				ex |= aura.isEx();
				if (!aura.isEx()) type = aura.type();
			}
		}
		if (card.isEmpty()) return true;
		boolean auraConversion = type != null || ex;
		if (auraConversion && (DynamicSpellItem.isComplete(card)
				|| dev.xkmc.youkaishomecoming.content.spell.certification.CertifiedSpellValidator
				.isCertified(card))) return false;
		SpellCardType resultType = type == null ? DynamicSpellItem.getCardType(card) : type;
		return !(resultType == SpellCardType.NON_SPELL && (ex || DynamicSpellItem.isExSpell(card)));
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
