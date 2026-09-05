package dev.xkmc.youkaishomecoming.content.spell.payment;

import org.jetbrains.annotations.Nullable;

/**
 * Result of a payment attempt. {@code handled} is set when a script took over
 * the payment entirely (KubeJS handled semantics); {@code reason} carries a
 * human-readable failure cause for the caller to surface.
 */
public record PaymentResult(boolean success, boolean handled, @Nullable String reason,
							@Nullable PaymentReceipt receipt) {

	public static PaymentResult success(PaymentReceipt receipt) {
		return new PaymentResult(true, false, null, receipt);
	}

	public static PaymentResult failure(String reason) {
		return new PaymentResult(false, false, reason, null);
	}

	public static PaymentResult takenOver() {
		return new PaymentResult(true, true, null, null);
	}
}
