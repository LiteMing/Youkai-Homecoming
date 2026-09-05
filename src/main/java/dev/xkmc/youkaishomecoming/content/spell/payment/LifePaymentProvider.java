package dev.xkmc.youkaishomecoming.content.spell.payment;

import dev.xkmc.youkaishomecoming.compat.stg.YHStgApi;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Life provider — high-cost "overload casting" gated behind configuration;
 * never deducted silently by default (design doc §14).
 */
public class LifePaymentProvider implements SpellPaymentProvider {

	@Override
	public ResourceLocation id() {
		return SpellPaymentProviders.LIFE;
	}

	@Override
	public boolean supports(ServerPlayer player, SpellCostContext context) {
		return YHModConfig.COMMON.lifePaymentEnabled.get()
				&& (context == SpellCostContext.SPELL_CAST_STG
				|| context == SpellCostContext.SPELL_CAST_NON_STG);
	}

	@Override
	public PaymentQuote quote(ServerPlayer player, long costUnits, SpellCostContext context) {
		// abstract units -> life display units: 1000 units = 1 life (Phase 7 rate)
		long life = Math.max(1, (long) Math.ceil(costUnits / 1000.0));
		return new PaymentQuote(this, context, costUnits, life, "life");
	}

	@Override
	public boolean canAfford(ServerPlayer player, PaymentQuote quote) {
		return YHStgApi.getLife(player) > quote.amount();
	}

	@Override
	public PaymentResult tryPay(ServerPlayer player, PaymentQuote quote) {
		if (!canAfford(player, quote)) {
			return PaymentResult.failure("not enough life");
		}
		YHStgApi.addLife(player, -quote.amount());
		return PaymentResult.success(new PaymentReceipt(this, quote.context(), quote.amount()));
	}

	@Override
	public void refund(ServerPlayer player, PaymentReceipt receipt) {
		YHStgApi.addLife(player, receipt.amount());
	}
}
