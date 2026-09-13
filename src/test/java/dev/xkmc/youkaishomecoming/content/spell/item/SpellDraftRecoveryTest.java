package dev.xkmc.youkaishomecoming.content.spell.item;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.mojang.authlib.GameProfile;
import dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem;
import dev.xkmc.youkaishomecoming.content.item.danmaku.SpellAuraItem;
import dev.xkmc.youkaishomecoming.content.spell.SpellTestBootstrap;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellCardRank;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellDraftBudget;
import dev.xkmc.youkaishomecoming.content.spell.certification.CertifiedSpellValidator;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellCardType;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDisplay;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellItemForm;
import dev.xkmc.youkaishomecoming.content.spell.difficulty.DifficultyProfile;
import dev.xkmc.youkaishomecoming.content.spell.preview.SpellEditorSyncToServer;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRegistry;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.ForgeRegistry;
import sun.misc.Unsafe;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Real item NBT, registry and crafting recovery; no game window or live packet transport. */
public final class SpellDraftRecoveryTest {

	private static final GameProfile PROFILE = new GameProfile(UUID.fromString("00000000-0000-0000-0000-000000000030"), "DraftTester");
	private static DynamicSpellItem item;
	private static SpellAuraItem aura;
	private static int checks;

	public static void main(String[] args) throws Exception {
		if (SpellTestBootstrap.enter(SpellDraftRecoveryTest.class, args)) return;
		net.minecraft.SharedConstants.tryDetectVersion();
		net.minecraft.server.Bootstrap.bootStrap();
		var config = CommentedConfig.inMemory();
		YHModConfig.COMMON_SPEC.correct(config);
		YHModConfig.COMMON_SPEC.setConfig(config);
		var items = (ForgeRegistry<Item>) ForgeRegistries.ITEMS;
		items.unfreeze();
		((MappedRegistry<Item>) BuiltInRegistries.ITEM).unfreeze();
		item = new DynamicSpellItem(new Item.Properties());
		aura = new SpellAuraItem(new Item.Properties(), SpellCardType.LAST_SPELL);
		items.register(id("draft_item"), item);
		items.register(id("aura"), aura);
		items.freeze();
		BuiltInRegistries.ITEM.freeze();
		recoverStoredDraft();
		protectExistingCards();
		legacyBinding();
		deleteAndReuse();
		washAndUpgrade();
		System.out.println("SpellDraftRecoveryTest: " + checks + " checks passed");
	}

	private static void recoverStoredDraft() {
		var definition = definition(id("deleted"));
		SpellRegistry.register(definition);
		ItemStack draft = card(definition.id);
		CompoundTag original = draft.getTag().copy();
		check("live draft binding stays intact", !DynamicSpellItem.clearMissingDraftBinding(draft) && original.equals(draft.getTag()));
		SpellRegistry.remove(definition.id);
		draft = ItemStack.of(draft.save(new CompoundTag()));
		check("old saved orphan is recovered", DynamicSpellItem.clearMissingDraftBinding(draft));
		original.remove("spell_id");
		check("recovery removes only the missing binding", original.equals(draft.getTag()) && DynamicSpellItem.getSpellId(draft) == null);
		check("recovery is idempotent", !DynamicSpellItem.clearMissingDraftBinding(draft));
		check("recovered base can bind a different new id", DynamicSpellItem.bindCreatedSpellId(draft, id("replacement")));
		check("replacement is the actual stored id", id("replacement").equals(DynamicSpellItem.getSpellId(draft)));
		ItemStack malformed = card(id("malformed"));
		malformed.getTag().putString("spell_id", "Invalid ID!");
		check("malformed old id also becomes a blank draft", DynamicSpellItem.clearMissingDraftBinding(malformed) && !malformed.getTag().contains("spell_id"));
		ItemStack nonSpell = card(id("deleted_non_spell"));
		DynamicSpellItem.setCardType(nonSpell, SpellCardType.NON_SPELL);
		DynamicSpellItem.setExSpell(nonSpell, false);
		check("orphan non-spell retains its aura", DynamicSpellItem.clearMissingDraftBinding(nonSpell) && DynamicSpellItem.isNonSpell(nonSpell));
	}

	private static void protectExistingCards() {
		for (boolean certified : List.of(false, true)) {
			ItemStack card = card(id("missing_completed"));
			if (certified) card.getOrCreateTag().putString(CertifiedSpellValidator.TAG_CERTIFIED_HASH, "snapshot-hash");
			else DynamicSpellItem.setComplete(card, true);
			CompoundTag before = card.getTag().copy();
			check("completed and certificate-backed cards are not reset", !DynamicSpellItem.clearMissingDraftBinding(card) && before.equals(card.getTag()));
		}
		check("empty stacks are unchanged", !DynamicSpellItem.clearMissingDraftBinding(ItemStack.EMPTY));
	}

	private static void legacyBinding() throws Exception {
		var definition = definition(new ResourceLocation("drafttester", "legacy"));
		SpellRegistry.register(definition);
		ItemStack draft = card(new ResourceLocation("minecraft", "legacy"));
		var resolve = DynamicSpellItem.class.getDeclaredMethod("resolveEditableDefinition", ItemStack.class, Player.class);
		resolve.setAccessible(true);
		check("existing namespace migration still resolves the custom spell", resolve.invoke(null, draft, player()) == definition);
		check("migrated binding survives orphan cleanup", !DynamicSpellItem.clearMissingDraftBinding(draft) && definition.id.equals(DynamicSpellItem.getSpellId(draft)));
		SpellRegistry.remove(definition.id);
	}

	private static void deleteAndReuse() throws Exception {
		var player = player();
		var deleted = definition(id("delete_in_editor"));
		var unrelated = definition(id("other"));
		SpellRegistry.register(deleted);
		SpellRegistry.register(unrelated);
		ItemStack held = card(deleted.id);
		player.inventory.items.set(0, held);
		player.inventory.offhand.set(0, held.copy());
		player.inventory.items.set(1, card(unrelated.id));
		ItemStack certified = held.copy();
		certified.getTag().putString(CertifiedSpellValidator.TAG_CERTIFIED_HASH, "snapshot-hash");
		player.inventory.items.set(2, certified);
		SpellRegistry.remove(deleted.id);
		var clear = SpellEditorSyncToServer.class.getDeclaredMethod("clearDeletedDraftBindings", ServerPlayer.class, ResourceLocation.class);
		clear.setAccessible(true);
		clear.invoke(null, player, deleted.id);
		check("deletion releases the held base", DynamicSpellItem.getSpellId(held) == null);
		check("deletion includes offhand bases", DynamicSpellItem.getSpellId(player.inventory.offhand.get(0)) == null);
		check("deletion preserves unrelated cards", unrelated.id.equals(DynamicSpellItem.getSpellId(player.inventory.items.get(1))));
		check("deletion preserves certified snapshot bindings", deleted.id.equals(DynamicSpellItem.getSpellId(certified)));
		check("same editor can immediately bind a new spell", DynamicSpellItem.bindCreatedSpellId(held, id("created_after_delete")));
		SpellRegistry.remove(unrelated.id);
	}

	private static void washAndUpgrade() {
		for (Item solvent : List.of(Items.WATER_BUCKET, Items.POTION)) {
			ItemStack input = card(id("wash_missing"));
			DynamicSpellItem.setComplete(input, true);
			input.getTag().putString(CertifiedSpellValidator.TAG_CERTIFIED_HASH, "old-hash");
			input.getTag().putString(CertifiedSpellValidator.TAG_CERTIFICATE_ID, "old-certificate");
			CompoundTag before = input.getTag().copy();
			ItemStack result = craft(input, solvent, null);
			check("wash output releases the missing spell id", DynamicSpellItem.getSpellId(result) == null);
			check("wash output is unfinished", !DynamicSpellItem.isComplete(result) && !CertifiedSpellValidator.isCertified(result));
			check("wash preserves tier and aura traits", DynamicSpellItem.getRank(result) == DynamicSpellItem.getRank(input)
					&& DynamicSpellItem.getCardType(result) == SpellCardType.TIMEOUT_SPELL && DynamicSpellItem.isExSpell(result));
			check("assembling a wash never mutates the ingredient", before.equals(input.getTag()));
		}
		var live = definition(id("wash_live"));
		SpellRegistry.register(live);
		check("washing a live spell retains its editable definition", live.id.equals(DynamicSpellItem.getSpellId(craft(card(live.id), Items.WATER_BUCKET, null))));
		SpellRegistry.remove(live.id);
		ItemStack upgraded = craft(card(id("upgrade_missing")), Items.PAPER, id("boss"));
		check("upgrade does not carry an orphan binding forward", DynamicSpellItem.getSpellId(upgraded) == null && DynamicSpellItem.getRank(upgraded).tierNumber() == 4);
		ItemStack converted = craft(card(id("aura_missing")), aura, null);
		check("aura conversion retains the binding for normal legacy repair on use", id("aura_missing").equals(DynamicSpellItem.getSpellId(converted)));
	}

	private static ItemStack craft(ItemStack input, Item reagent, ResourceLocation boss) {
		var container = new TransientCraftingContainer(menu(), 2, 2);
		container.setItem(0, input);
		container.setItem(1, new ItemStack(reagent));
		var recipe = new SpellDraftRecipe(id("recipe"), "", CraftingBookCategory.EQUIPMENT, new ItemStack(item),
				NonNullList.of(Ingredient.EMPTY, Ingredient.of(item), Ingredient.of(reagent)), boss);
		check("the real recipe accepts the fixture", recipe.matches(container, null));
		return recipe.assemble(container, RegistryAccess.EMPTY);
	}

	private static ItemStack card(ResourceLocation id) {
		ItemStack stack = DynamicSpellItem.createStack(item, id);
		DynamicSpellItem.setRank(stack, SpellCardRank.fromTier(3));
		DynamicSpellItem.setDraftBudget(stack, SpellDraftBudget.legacy(4));
		DynamicSpellItem.setCardType(stack, SpellCardType.TIMEOUT_SPELL);
		DynamicSpellItem.setExSpell(stack, true);
		stack.getOrCreateTag().putString("other_mod_data", "keep");
		return stack;
	}

	private static SpellDefinition definition(ResourceLocation id) {
		return new SpellDefinition(id, new SpellDisplay("Draft", "", Optional.empty(), Optional.empty()),
				SpellItemForm.NONE, id, Map.of(), DifficultyProfile.DEFAULT);
	}

	private static AbstractContainerMenu menu() {
		return new AbstractContainerMenu(null, 0) {
			@Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
			@Override public boolean stillValid(Player player) { return true; }
		};
	}

	private static TestPlayer player() throws Exception {
		var field = Unsafe.class.getDeclaredField("theUnsafe");
		field.setAccessible(true);
		var player = (TestPlayer) ((Unsafe) field.get(null)).allocateInstance(TestPlayer.class);
		player.inventory = new Inventory(player);
		player.containerMenu = menu();
		return player;
	}

	private static final class TestPlayer extends ServerPlayer {
		Inventory inventory;
		private TestPlayer() { super(null, null, PROFILE); }
		@Override public Inventory getInventory() { return inventory; }
		@Override public GameProfile getGameProfile() { return PROFILE; }
	}

	private static ResourceLocation id(String path) { return new ResourceLocation("yh_draft_test", path); }
	private static void check(String label, boolean pass) { if (!pass) throw new AssertionError(label); checks++; }
}
