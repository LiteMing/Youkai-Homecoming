package dev.xkmc.youkaishomecoming.content.spell.payment;

import dev.xkmc.youkaishomecoming.compat.stg.YHStgApi;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Points provider — usable as certification start fee or discount
 * (design doc §14; certification wiring lands in Phase 2).
 */
public class PointsPaymentProvider implements SpellPaymentProvider {

	@Override
	public ResourceLocation id() {
		return SpellPaymentProviders.POINTS;
	}

	@Override
	public boolean supports(ServerPlayer player, SpellCostContext context) {
		return context == SpellCostContext.CERTIFICATION_START
				|| context == SpellCostContext.CERTIFICATION_ISSUE;
	}

	@Override
	public PaymentQuote quote(ServerPlayer player, long costUnits, SpellCostContext context) {
		return new PaymentQuote(this, context, costUnits, costUnits, "points");
	}

	@Override
	public boolean canAfford(ServerPlayer player, PaymentQuote quote) {
		return YHStgApi.getPoints(player) >= quote.amount();
	}

	@Override
	public PaymentResult tryPay(ServerPlayer player, PaymentQuote quote) {
		if (!canAfford(player, quote)) {
			return PaymentResult.failure("not enough points");
		}
		YHStgApi.addPoints(player, -quote.amount());
		return PaymentResult.success(new PaymentReceipt(this, quote.context(), quote.amount()));
	}

	@Override
	public void refund(ServerPlayer player, PaymentReceipt receipt) {
		YHStgApi.addPoints(player, receipt.amount());
	}
}
