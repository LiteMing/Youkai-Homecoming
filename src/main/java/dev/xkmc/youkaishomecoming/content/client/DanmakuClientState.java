package dev.xkmc.youkaishomecoming.content.client;

import dev.xkmc.youkaishomecoming.content.capability.GrazeCapability;
import dev.xkmc.youkaishomecoming.content.capability.GrazeHelper;
import dev.xkmc.youkaishomecoming.compat.curios.CuriosManager;
import dev.xkmc.youkaishomecoming.content.item.danmaku.SpellItemCost;
import dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem;
import dev.xkmc.youkaishomecoming.content.spell.certification.CertifiedSpellValidator;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellCardType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Client-only projection of the local player's danmaku visibility state. */
public final class DanmakuClientState {
	private DanmakuClientState() {
	}

	public static boolean isLocalPlayerSuppressed() {
		Player player = Minecraft.getInstance().player;
		if (player == null) return false;
		var cap = GrazeCapability.HOLDER.get(player);
		return cap.isPlayerSpellActive() || cap.isInvul() || cap.isWeak();
	}

	public static boolean isNonSpellActive(Player player, ItemStack stack) {
		if (player == null || stack.isEmpty() || !DynamicSpellItem.isNonSpell(stack)) return false;
		return GrazeCapability.HOLDER.get(player).isNonSpellActive(GrazeHelper.spellCardKey(stack));
	}

	public static boolean isBoundSpellDraft(ItemStack stack) {
		return stack.getItem() instanceof DynamicSpellItem
				&& DynamicSpellItem.getSpellId(stack) != null
				&& !DynamicSpellItem.isComplete(stack)
				&& !CertifiedSpellValidator.isCertified(stack);
	}

	public static float getLastSpellCooldownProgress(Player player, ItemStack stack, float partialTick) {
		if (player == null || !(stack.getItem() instanceof DynamicSpellItem)
				|| DynamicSpellItem.getCardType(stack) != SpellCardType.LAST_SPELL) return 0;
		return GrazeCapability.HOLDER.get(player).getLastSpellCooldownProgress(partialTick);
	}

	public static boolean isSpellCardCastable(Player player, ItemStack stack) {
		if (player == null || stack.isEmpty()) return false;
		var cap = GrazeCapability.HOLDER.get(player);
		String cardKey = GrazeHelper.spellCardKey(stack);
		if (cap.isSpellCardUnavailable(cardKey)) {
			return false;
		}
		SpellCardType type = stack.getItem() instanceof DynamicSpellItem
				? DynamicSpellItem.getCardType(stack) : SpellCardType.NORMAL;
		if (type == SpellCardType.NON_SPELL) return false;
		if (type == SpellCardType.LAST_SPELL) {
			// Automatic fallback only considers a Last Spell after the player's
			// bomb stock is exhausted. Manual item use remains server-authoritative.
			return cap.getBomb() <= 0 && cap.canActivateLastSpell() && !cap.isPlayerSpellActive();
		}

		// 检查资源是否足够支付 (弹幕战内看 BOMB，战外看经验等级)
		boolean inCombat = cap.isInDanmakuCombat();
		if (player.getAbilities().instabuild) return true;
		long costUnits = SpellItemCost.getPayableStackCostUnits(stack, inCombat);
		if (inCombat) {
			// Bombs are stored in fifths (raw units): 20 abstract units = 1 raw.
			long requiredRawBomb = Math.max(1, (long) Math.ceil(costUnits / 20.0));
			return cap.getBomb() >= requiredRawBomb;
		} else {
			// 1 XP Level = 20 units
			int xpLevels = (int) Math.ceil(costUnits / 20.0);
			return player.experienceLevel >= xpLevels;
		}
	}

	/**
	 * Finds the next card that can actually be released. This deliberately
	 * mirrors the server's main-hand, off-hand, inventory, then Curios order so a
	 * red card never prevents a later usable card from being selected.
	 */
	public static ItemStack findNextCastableSpellCard(Player player) {
		if (player == null) return ItemStack.EMPTY;
		ItemStack mainhand = player.getMainHandItem();
		if (isCastableSpellStack(player, mainhand)) return mainhand;
		ItemStack offhand = player.getOffhandItem();
		if (isCastableSpellStack(player, offhand)) return offhand;
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.items.size(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (isCastableSpellStack(player, stack)) return stack;
		}
		return CuriosManager.findFirstSpellItem(player,
				stack -> isCastableSpellStack(player, stack));
	}

	private static boolean isCastableSpellStack(Player player, ItemStack stack) {
		return GrazeHelper.isSpellStack(stack) && !DynamicSpellItem.isNonSpell(stack)
				&& isSpellCardCastable(player, stack);
	}
}
