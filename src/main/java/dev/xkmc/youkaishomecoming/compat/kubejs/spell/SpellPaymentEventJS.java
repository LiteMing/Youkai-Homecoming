package dev.xkmc.youkaishomecoming.compat.kubejs.spell;

import dev.latvian.mods.kubejs.event.EventJS;
import dev.xkmc.youkaishomecoming.content.spell.payment.PaymentQuote;
import dev.xkmc.youkaishomecoming.content.spell.payment.SpellCostContext;
import dev.xkmc.youkaishomecoming.content.spell.payment.SpellPaymentProvider;
import dev.xkmc.youkaishomecoming.content.spell.payment.SpellPaymentProviders;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * KubeJS payment hook (design doc §14). Fired before a payment is executed.
 * <ul>
 *   <li>{@code setProvider(id)} replaces the payment provider;</li>
 *   <li>{@code handled()} declares the script paid on its own — the default
 *   provider is skipped entirely;</li>
 *   <li>interrupting the event (hasResult) rejects the payment with a failure
 *   reason script-provided via {@code setReason(reason)}.</li>
 * </ul>
 */
public class SpellPaymentEventJS extends EventJS {

	public final ServerPlayer player;
	public final SpellCostContext context;
	public final long costUnits;

	private SpellPaymentProvider provider;
	private boolean handled;
	private @Nullable PaymentQuote quote;
	private @Nullable String reason;

	public SpellPaymentEventJS(ServerPlayer player, SpellCostContext context,
							   long costUnits, SpellPaymentProvider provider) {
		this.player = player;
		this.context = context;
		this.costUnits = costUnits;
		this.provider = provider;
	}

	public SpellPaymentProvider provider() {
		return provider;
	}

	public boolean handled() {
		return handled;
	}

	@Nullable
	public PaymentQuote quote() {
		return quote;
	}

	@Nullable
	public String reason() {
		return reason;
	}

	public void setProvider(String id) {
		this.provider = SpellPaymentProviders.get(new ResourceLocation(id));
	}

	public void markHandled() {
		this.handled = true;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

	public void setQuote(PaymentQuote quote) {
		this.quote = quote;
	}

	/** Script-facing description of the current context. */
	public String getContext() {
		return context.name();
	}

	public long getCostUnits() {
		return costUnits;
	}

	public String getProviderId() {
		return provider.id().toString();
	}
}
