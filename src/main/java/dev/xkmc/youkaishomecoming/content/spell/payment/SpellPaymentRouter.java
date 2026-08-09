package dev.xkmc.youkaishomecoming.content.spell.payment;

import dev.xkmc.youkaishomecoming.compat.kubejs.spell.SpellPaymentEventJS;
import dev.xkmc.youkaishomecoming.compat.kubejs.spell.YHSpellKubeJSEvents;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

/**
 * Server-side payment routing (design doc §14). Selects the default provider per
 * context, lets KubeJS scripts override the provider or take over the payment
 * entirely (handled), then quotes and pays. Callers keep the failure messaging
 * responsibility (e.g. SpellItemCost preserves its historical messages).
 */
public final class SpellPaymentRouter {

	private SpellPaymentRouter() {
	}

	public static SpellPaymentProvider defaultProvider(SpellCostContext context) {
		return switch (context) {
			case SPELL_CAST_STG -> SpellPaymentProviders.get(SpellPaymentProviders.BOMB);
			case SPELL_CAST_NON_STG -> SpellPaymentProviders.get(SpellPaymentProviders.EXPERIENCE);
			// INV-8: the certification start/issue provider is config-driven
			// (certification.startPaymentProvider, default experience) — never
			// hard-code a second default here.
			case CERTIFICATION_START, CERTIFICATION_ISSUE -> providerFromConfig(
					YHModConfig.COMMON.certificationStartPaymentProvider.get());
		};
	}

	private static SpellPaymentProvider providerFromConfig(String id) {
		ResourceLocation parsed = ResourceLocation.tryParse(id);
		if (parsed == null) {
			return SpellPaymentProviders.get(SpellPaymentProviders.EXPERIENCE);
		}
		try {
			return SpellPaymentProviders.get(parsed);
		} catch (IllegalArgumentException e) {
			return SpellPaymentProviders.get(SpellPaymentProviders.EXPERIENCE);
		}
	}

	public static PaymentResult pay(ServerPlayer player, long costUnits, SpellCostContext context) {
		SpellPaymentProvider provider = defaultProvider(context);
		boolean kubejs = ModList.get().isLoaded("kubejs")
				&& YHSpellKubeJSEvents.SPELL_PAYMENT.hasListeners();
		if (kubejs) {
			var event = new SpellPaymentEventJS(player, context, costUnits, provider);
			if (YHSpellKubeJSEvents.SPELL_PAYMENT.post(event).interruptFalse()) {
				return PaymentResult.failure("payment rejected by script");
			}
			provider = event.provider();
			if (event.handled()) {
				return PaymentResult.takenOver();
			}
		}
		PaymentQuote quote = provider.quote(player, costUnits, context);
		if (kubejs) {
			// second pass lets scripts adjust the quote after provider resolution
			var event = new SpellPaymentEventJS(player, context, costUnits, provider);
			event.setQuote(quote);
			YHSpellKubeJSEvents.SPELL_PAYMENT.post(event);
			quote = event.quote() != null ? event.quote() : quote;
		}
		return provider.tryPay(player, quote);
	}

	public static PaymentQuote quote(ServerPlayer player, long costUnits, SpellCostContext context) {
		SpellPaymentProvider provider = defaultProvider(context);
		return provider.quote(player, costUnits, context);
	}

	public static ResourceLocation providerId(ServerPlayer player, SpellCostContext context) {
		return defaultProvider(context).id();
	}

	/** Refund through the provider named on the receipt. */
	public static void refund(ServerPlayer player, PaymentReceipt receipt) {
		receipt.provider().refund(player, receipt);
	}
}
