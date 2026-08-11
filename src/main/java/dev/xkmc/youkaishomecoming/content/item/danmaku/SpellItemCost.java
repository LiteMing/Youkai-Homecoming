package dev.xkmc.youkaishomecoming.content.item.danmaku;

import dev.xkmc.youkaishomecoming.compat.stg.YHStgApi;
import dev.xkmc.youkaishomecoming.content.spell.payment.PaymentResult;
import dev.xkmc.youkaishomecoming.content.spell.payment.SpellCostContext;
import dev.xkmc.youkaishomecoming.content.spell.payment.SpellPaymentRouter;
import dev.xkmc.youkaishomecoming.init.data.YHLangData;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

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
		long costUnits = durationTicks >= 0
				// duration-driven cost: 1 bomb / 5 XP baseline, +0.2 / +1 per 20 ticks
				? dev.xkmc.youkaishomecoming.content.spell.payment.CastCost.unitsForDuration(durationTicks)
				: legacyConfigUnits(context);
		return tryPayUnits(sp, costUnits);
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
