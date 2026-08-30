package dev.xkmc.youkaishomecoming.content.client;

import dev.xkmc.youkaishomecoming.content.capability.GrazeCapability;
import dev.xkmc.youkaishomecoming.content.capability.GrazeHelper;
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

	public static boolean isSpellCardCastable(Player player, ItemStack stack) {
		if (player == null || stack.isEmpty()) return false;
		var cap = GrazeCapability.HOLDER.get(player);
		String cardKey = GrazeHelper.spellCardKey(stack);
		if (cap.isSpellCardUnavailable(cardKey)) {
			return false;
		}

		// 检查资源是否足够支付 (弹幕战内看 BOMB，战外看经验等级)
		boolean inCombat = cap.isInDanmakuCombat();
		long costUnits = getStackCostUnits(stack, inCombat);
		if (inCombat) {
			// 1 BOMB = 100 units
			return cap.getBomb() * 100 >= costUnits;
		} else {
			// 1 XP Level = 20 units
			int xpLevels = (int) Math.ceil(costUnits / 20.0);
			return player.experienceLevel >= xpLevels || player.getAbilities().instabuild;
		}
	}

	private static long getStackCostUnits(ItemStack stack, boolean inCombat) {
		if (dev.xkmc.youkaishomecoming.content.spell.certification.CertifiedSpellValidator.isCertified(stack)) {
			return dev.xkmc.youkaishomecoming.content.spell.certification.CertifiedSpellValidator.getCertifiedCost(stack);
		}
		if (stack.getItem() instanceof dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem) {
			var def = dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem.getSpellDefinition(stack);
			if (def != null) {
				int duration = def.itemForm.duration();
				return dev.xkmc.youkaishomecoming.content.spell.payment.CastCost.unitsForDuration(duration);
			}
		}
		return inCombat ? 100L : 100L;
	}
}
