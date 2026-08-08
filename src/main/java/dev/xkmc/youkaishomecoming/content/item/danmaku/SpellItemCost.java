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

	public static boolean tryPay(ServerPlayer sp) {
		SpellCostContext context = YHStgApi.isInDanmakuSession(sp)
				? SpellCostContext.SPELL_CAST_STG : SpellCostContext.SPELL_CAST_NON_STG;
		long costUnits = context == SpellCostContext.SPELL_CAST_STG
				? YHModConfig.COMMON.spellBombCost.get() : YHModConfig.COMMON.spellXpCost.get();
		PaymentResult result = SpellPaymentRouter.pay(sp, costUnits, context);
		if (result.success()) {
			return true;
		}
		if (context == SpellCostContext.SPELL_CAST_STG) {
			sp.displayClientMessage(YHLangData.SPELL_COST_NO_BOMB.get(costUnits, YHStgApi.getBomb(sp)), false);
		} else {
			sp.displayClientMessage(YHLangData.SPELL_COST_NO_XP.get(costUnits, sp.experienceLevel), false);
		}
		return false;
	}

	public static void appendCostTooltip(List<Component> list) {
		if (YHModConfig.COMMON.spellBombCost.get() > 0) {
			list.add(YHLangData.SPELL_COST_BOMB.get(YHModConfig.COMMON.spellBombCost.get()));
		}
		if (YHModConfig.COMMON.spellXpCost.get() > 0) {
			list.add(YHLangData.SPELL_COST_XP.get(YHModConfig.COMMON.spellXpCost.get()));
		}
	}
}
