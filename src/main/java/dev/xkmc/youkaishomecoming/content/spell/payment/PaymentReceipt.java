package dev.xkmc.youkaishomecoming.content.spell.payment;

import java.util.UUID;

/**
 * Payment transaction receipt for refunds (design doc §14). Providers may store
 * provider-specific payload (e.g. serialized external-mana token); default
 * providers only need the amount that was deducted.
 */
public record PaymentReceipt(UUID receiptId, SpellPaymentProvider provider,
							 SpellCostContext context, long amount, String payload) {

	public PaymentReceipt(SpellPaymentProvider provider, SpellCostContext context, long amount) {
		this(UUID.randomUUID(), provider, context, amount, "");
	}

	public PaymentReceipt withPayload(String newPayload) {
		return new PaymentReceipt(receiptId, provider, context, amount, newPayload);
	}
}
