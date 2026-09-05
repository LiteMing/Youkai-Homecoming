package dev.xkmc.youkaishomecoming.content.spell.payment;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Replaceable payment route (design doc §14). Providers are registered by stable
 * string id in {@link SpellPaymentProviders}; scripts may register external-mana
 * providers and override the default routing per context.
 */
public interface SpellPaymentProvider {

	ResourceLocation id();

	/** Whether this provider can serve the given context/player at all. */
	default boolean supports(ServerPlayer player, SpellCostContext context) {
		return true;
	}

	/**
	 * Convert abstract cost units into this provider's concrete amount and produce
	 * a firm quote. Should not mutate anything.
	 */
	PaymentQuote quote(ServerPlayer player, long costUnits, SpellCostContext context);

	/** Whether the player can currently afford the quoted amount. */
	boolean canAfford(ServerPlayer player, PaymentQuote quote);

	/** Execute the payment; returns success + receipt, or a failure reason. */
	PaymentResult tryPay(ServerPlayer player, PaymentQuote quote);

	/** Refund a previously issued receipt. */
	void refund(ServerPlayer player, PaymentReceipt receipt);
}
