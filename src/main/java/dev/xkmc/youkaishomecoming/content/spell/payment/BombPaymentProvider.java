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
		// STG resources use fifths of a bomb internally, so 20 abstract units
		// (0.2 bomb) map to one raw resource unit. Keeping this conversion here
		// preserves fractional costs while the public API continues to expose bombs.
		long rawBomb = Math.max(1, (long) Math.ceil(costUnits / 20.0));
		return new PaymentQuote(this, context, costUnits, rawBomb, "bomb");
	}

	@Override
	public boolean canAfford(ServerPlayer player, PaymentQuote quote) {
		return YHStgApi.getBombRaw(player) >= quote.amount();
	}

	@Override
	public PaymentResult tryPay(ServerPlayer player, PaymentQuote quote) {
		if (!canAfford(player, quote)) {
			return PaymentResult.failure("not enough bomb");
		}
		YHStgApi.addBombRaw(player, (int) -quote.amount());
		return PaymentResult.success(new PaymentReceipt(this, quote.context(), quote.amount()));
	}

	@Override
	public void refund(ServerPlayer player, PaymentReceipt receipt) {
		YHStgApi.addBombRaw(player, (int) receipt.amount());
	}
}
