package dev.xkmc.youkaishomecoming.content.spell.certification.network;

import dev.xkmc.l2serial.network.SerialPacketBase;
import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRegistry;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import dev.xkmc.youkaishomecoming.init.data.YHLangData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client → server: request a firm certification quote for a candidate
	 * definition (design doc §18). The client supplies only the id; the server
	 * resolves the canonical definition from its registry.
 */
@SerialClass
public class CertificationQuoteRequestToServer extends SerialPacketBase {

	@SerialClass.SerialField
	public String spellId = "";

	public CertificationQuoteRequestToServer() {
	}

	public CertificationQuoteRequestToServer(dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition definition) {
		this.spellId = definition.id.toString();
	}

	@Override
	public void handle(NetworkEvent.Context context) {
		ServerPlayer player = context.getSender();
		if (player == null) return;
		context.enqueueWork(() -> {
			try {
				if (spellId.isEmpty()) {
					player.displayClientMessage(YHLangData.CERT_QUOTE_FAIL.get(
							YHLangData.CERT_QUOTE_MISSING_SPELL_ID.get()), false);
					return;
				}
				var id = net.minecraft.resources.ResourceLocation.tryParse(spellId);
				var definition = id == null ? null : SpellRegistry.get(id);
				if (definition == null) {
					player.displayClientMessage(YHLangData.CERT_QUOTE_FAIL.get(
							YHLangData.CERT_QUOTE_UNKNOWN_DEFINITION.get()), false);
					return;
				}
				CertificationQuoteRequestHandler.accept(player, definition);
			} catch (Exception e) {
				YoukaisHomecoming.LOGGER.error("Unexpected certification quote failure for {}", spellId, e);
				player.displayClientMessage(YHLangData.CERT_QUOTE_FAIL.get(
						YHLangData.CERT_QUOTE_INTERNAL_ERROR.get()), false);
			}
		});
		context.setPacketHandled(true);
	}
}
