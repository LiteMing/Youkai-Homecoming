package dev.xkmc.youkaishomecoming.content.item.danmaku;

import dev.xkmc.youkaishomecoming.compat.stg.YHStgApi;
import dev.xkmc.youkaishomecoming.content.spell.certification.CertifiedSpellValidator;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.payment.PaymentResult;
import dev.xkmc.youkaishomecoming.content.spell.payment.SpellCostContext;
import dev.xkmc.youkaishomecoming.content.spell.payment.SpellPaymentRouter;
import dev.xkmc.youkaishomecoming.init.data.YHLangData;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Unified spell-card cast cost shared by every ISpellItem implementation.
 * Routes through the replaceable payment system (design doc §14):
 * inside STG danmaku combat a bomb is spent, outside combat player XP levels.
 * Returns false and notifies the player when the cost cannot be paid.
 * <p>
 * Historical behavior (context selection, messages, units) is preserved exactly;
 * only the deduction path now goes through SpellPaymentRouter so KubeJS scripts
 * may replace the provider or take over the payment.
 */
public final class SpellItemCost {

	private SpellItemCost() {
	}

	public static boolean tryPay(ServerPlayer sp, int durationTicks) {
		SpellCostContext context = YHStgApi.isInDanmakuSession(sp)
				? SpellCostContext.SPELL_CAST_STG : SpellCostContext.SPELL_CAST_NON_STG;
		long costUnits = unitsForDuration(durationTicks, context);
		return tryPayUnits(sp, costUnits);
	}

	/** Returns the abstract cast cost used by the payment providers. */
	public static long unitsForDuration(int durationTicks, SpellCostContext context) {
		return durationTicks >= 0
				// duration-driven cost: 1 bomb / 5 XP baseline, +0.2 / +1 per 20 ticks
				? dev.xkmc.youkaishomecoming.content.spell.payment.CastCost.unitsForDuration(durationTicks)
				: legacyConfigUnits(context);
	}

	/**
	 * Estimates a stack's cast cost for selection and UI. Certified cards carry
	 * an immutable quote; dynamic cards otherwise derive it from their duration.
	 * The final payment remains authoritative at the cast boundary.
	 */
	public static long getStackCostUnits(ItemStack stack, boolean inCombat) {
		if (CertifiedSpellValidator.isCertified(stack)) {
			return CertifiedSpellValidator.getCertifiedCost(stack);
		}
		if (stack.getItem() instanceof DynamicSpellItem) {
			SpellDefinition definition = DynamicSpellItem.getSpellDefinition(stack);
			if (definition != null) {
				int duration = DynamicSpellItem.getStackDuration(stack);
				if (duration < 0) {
					duration = definition.itemForm.duration();
				}
				return unitsForDuration(duration,
						inCombat ? SpellCostContext.SPELL_CAST_STG : SpellCostContext.SPELL_CAST_NON_STG);
			}
		}
		return unitsForDuration(0,
				inCombat ? SpellCostContext.SPELL_CAST_STG : SpellCostContext.SPELL_CAST_NON_STG);
	}

	/** Server-side preflight for automatic card selection. */
	public static boolean canAfford(ServerPlayer player, ItemStack stack) {
		if (player.getAbilities().instabuild) return true;
		boolean inCombat = YHStgApi.isInDanmakuSession(player);
		SpellCostContext context = inCombat
				? SpellCostContext.SPELL_CAST_STG : SpellCostContext.SPELL_CAST_NON_STG;
		return SpellPaymentRouter.quote(player, getStackCostUnits(stack, inCombat), context).canAfford(player);
	}

	/** Pays a pre-validated certificate cost without recomputing it from mutable NBT. */
	public static boolean tryPayUnits(ServerPlayer sp, long costUnits) {
		SpellCostContext context = YHStgApi.isInDanmakuSession(sp)
				? SpellCostContext.SPELL_CAST_STG : SpellCostContext.SPELL_CAST_NON_STG;
		PaymentResult result = SpellPaymentRouter.pay(sp, costUnits, context);
		if (result.success()) {
			return true;
		}
		if (context == SpellCostContext.SPELL_CAST_STG) {
			sp.displayClientMessage(YHLangData.SPELL_COST_NO_BOMB.get(
					String.format(java.util.Locale.ROOT, "%.1f", costUnits / 100.0),
					String.format(java.util.Locale.ROOT, "%.1f", YHStgApi.getBomb(sp))), false);
		} else {
			sp.displayClientMessage(YHLangData.SPELL_COST_NO_XP.get(
					Math.max(1, (costUnits + 19) / 20), sp.experienceLevel), false);
		}
		return false;
	}

	/** Legacy fixed config cost (used by cards without a declared duration). */
	private static long legacyConfigUnits(SpellCostContext context) {
		return context == SpellCostContext.SPELL_CAST_STG
				? YHModConfig.COMMON.spellBombCost.get() * 100L
				: YHModConfig.COMMON.spellXpCost.get() * 20L;
	}

	public static void appendCostTooltip(List<Component> list, int durationTicks) {
		if (durationTicks >= 0) {
			long units = dev.xkmc.youkaishomecoming.content.spell.payment.CastCost.unitsForDuration(durationTicks);
			double bombs = units / 100.0;
			list.add(YHLangData.SPELL_COST_BOMB.get(String.format(java.util.Locale.ROOT, "%.1f", bombs)));
			list.add(YHLangData.SPELL_COST_XP.get(Math.max(1, Math.round(units / 20.0f))));
		} else {
			// legacy cards without a declared duration: show the configured costs
			int bomb = YHModConfig.COMMON.spellBombCost.get();
			int xp = YHModConfig.COMMON.spellXpCost.get();
			if (bomb > 0) {
				list.add(YHLangData.SPELL_COST_BOMB.get(bomb));
			}
			if (xp > 0) {
				list.add(YHLangData.SPELL_COST_XP.get(xp));
			}
		}
	}
}
