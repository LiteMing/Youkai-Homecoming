package dev.xkmc.youkaishomecoming.content.spell.payment;

import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Default non-STG provider: pays experience levels (design doc §14).
 */
public class ExperiencePaymentProvider implements SpellPaymentProvider {

	@Override
	public ResourceLocation id() {
		return SpellPaymentProviders.EXPERIENCE;
	}

	@Override
	public boolean supports(ServerPlayer player, SpellCostContext context) {
		return context == SpellCostContext.SPELL_CAST_NON_STG;
	}

	@Override
	public PaymentQuote quote(ServerPlayer player, long costUnits, SpellCostContext context) {
		// 20 abstract units = one XP level (100 units = 5 levels baseline).
		long levels = Math.max(1, (long) Math.ceil(costUnits / 20.0));
		return new PaymentQuote(this, context, costUnits, levels, "experience");
	}

	@Override
	public boolean canAfford(ServerPlayer player, PaymentQuote quote) {
		return player.experienceLevel >= quote.amount();
	}

	@Override
	public PaymentResult tryPay(ServerPlayer player, PaymentQuote quote) {
		if (!canAfford(player, quote)) {
			return PaymentResult.failure("not enough experience levels");
		}
		player.giveExperienceLevels(-(int) quote.amount());
		return PaymentResult.success(new PaymentReceipt(this, quote.context(), quote.amount()));
	}

	@Override
	public void refund(ServerPlayer player, PaymentReceipt receipt) {
		player.giveExperienceLevels((int) receipt.amount());
	}
}
