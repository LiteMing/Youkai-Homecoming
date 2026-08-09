package dev.xkmc.youkaishomecoming.content.spell.certification.network;

import dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem;
import dev.xkmc.youkaishomecoming.content.spell.certification.CertificationManager;
import dev.xkmc.youkaishomecoming.content.spell.certification.CertificationQuote;
import dev.xkmc.youkaishomecoming.content.spell.certification.CertificationService;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRegistry;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import dev.xkmc.youkaishomecoming.init.data.YHLangData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Server-side quote handling: analyze (certification profile), hash, cost and
 * cache the quote + definition keyed by quoteId (design doc §5.2, §18).
 * This is the in-game player path: only spell cards the player created
 * themselves (a DynamicSpellItem held in the inventory) may be certified here.
 * Built-in/registered spells are exclusively available through the OP
 * "certification boss" command (or the OP test command).
 */
public final class CertificationQuoteRequestHandler {

	private CertificationQuoteRequestHandler() {
	}

	public static void accept(ServerPlayer player, SpellDefinition definition,
							  int requestedDurationTicks, double requestedHalfSize) {
		if (!dev.xkmc.youkaishomecoming.init.data.YHModConfig.COMMON.certificationEnabled.get()) {
			player.displayClientMessage(YHLangData.CERT_DISABLED.get(), false);
			return;
		}
		if (!holdsOwnSpell(player, definition)) {
			player.displayClientMessage(YHLangData.CERT_SELF_MADE_ONLY.get(), false);
			return;
		}
		CertificationQuote quote;
		try {
			quote = CertificationService.quote(player, definition, requestedDurationTicks, requestedHalfSize);
		} catch (IllegalArgumentException e) {
			player.displayClientMessage(YHLangData.CERT_QUOTE_FAIL.get(e.getMessage()), false);
			return;
		}
		CertificationManager.INSTANCE.setQuote(player, quote, definition);
		YoukaisHomecoming.HANDLER.toClientPlayer(
				new CertificationQuoteToClient(quote), player);
	}

	/**
	 * The player must actually hold the spell card in their inventory: the item
	 * must be a DynamicSpellItem whose spell_id matches the requested definition.
	 * Built-in spells (which have a code default) never pass this check — they are
	 * reserved for the OP/console boss certification command.
	 */
	private static boolean holdsOwnSpell(ServerPlayer player, SpellDefinition definition) {
		ResourceLocation id = definition.id;
		if (id == null || SpellRegistry.hasDefault(id)) return false;
		for (ItemStack stack : player.getInventory().items) {
			if (stack.getItem() instanceof DynamicSpellItem
					&& id.equals(DynamicSpellItem.getSpellId(stack))) {
				return true;
			}
		}
		return false;
	}
}
