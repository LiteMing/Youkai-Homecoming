package dev.xkmc.youkaishomecoming.content.spell.payment;

import dev.xkmc.youkaishomecoming.compat.stg.YHStgApi;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Default STG provider: pays bomb display units (design doc §14).
 */
public class BombPaymentProvider implements SpellPaymentProvider {

	@Override
	public ResourceLocation id() {
		return SpellPaymentProviders.BOMB;
	}

	@Override
	public boolean supports(ServerPlayer player, SpellCostContext context) {
		return context == SpellCostContext.SPELL_CAST_STG;
	}

	@Override
	public PaymentQuote quote(ServerPlayer player, long costUnits, SpellCostContext context) {
		long bomb = costUnits;
		if (context == SpellCostContext.SPELL_CAST_STG) {
			bomb = YHModConfig.COMMON.spellBombCost.get();
		} else {
			// abstract units -> bomb display units: 100 units = 1 bomb (Phase 7 rate)
			bomb = Math.max(1, (long) Math.ceil(costUnits / 100.0));
		}
		return new PaymentQuote(this, context, costUnits, bomb, "bomb");
	}

	@Override
	public boolean canAfford(ServerPlayer player, PaymentQuote quote) {
		return YHStgApi.getBomb(player) >= quote.amount();
	}

	@Override
	public PaymentResult tryPay(ServerPlayer player, PaymentQuote quote) {
		if (!canAfford(player, quote)) {
			return PaymentResult.failure("not enough bomb");
		}
		YHStgApi.addBomb(player, -quote.amount());
		return PaymentResult.success(new PaymentReceipt(this, quote.context(), quote.amount()));
	}

	@Override
	public void refund(ServerPlayer player, PaymentReceipt receipt) {
		YHStgApi.addBomb(player, receipt.amount());
	}
}
