package dev.xkmc.youkaishomecoming.content.spell.payment;

import net.minecraft.server.level.ServerPlayer;

/**
 * Firm payment quote issued by a provider for a given cost (design doc §14).
 * {@code amount} is expressed in the provider's own unit (bomb display units,
 * XP levels, points, life display units, external mana, ...).
 */
public record PaymentQuote(SpellPaymentProvider provider, SpellCostContext context,
						   long costUnits, long amount, String description) {

	public PaymentQuote withAmount(long newAmount) {
		return new PaymentQuote(provider, context, costUnits, newAmount, description);
	}

	public boolean canAfford(ServerPlayer player) {
		return provider.canAfford(player, this);
	}
}
