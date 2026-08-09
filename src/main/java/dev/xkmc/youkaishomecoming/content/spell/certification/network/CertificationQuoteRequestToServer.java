package dev.xkmc.youkaishomecoming.content.spell.certification.network;

import dev.xkmc.l2serial.network.SerialPacketBase;
import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.content.spell.certification.CertificationService;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRegistry;
import dev.xkmc.youkaishomecoming.init.data.YHLangData;
import net.minecraft.network.chat.Component;
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
	public String definitionJson = "";
	@SerialClass.SerialField
	public String spellId = "";
	@SerialClass.SerialField
	public int durationTicks = 1200;
	@SerialClass.SerialField
	public double arenaHalfSize = 8;

	public CertificationQuoteRequestToServer() {
	}

	public CertificationQuoteRequestToServer(String definitionJson, int durationTicks, double arenaHalfSize) {
		this.definitionJson = definitionJson;
		this.durationTicks = durationTicks;
		this.arenaHalfSize = arenaHalfSize;
	}

	public CertificationQuoteRequestToServer(dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition definition,
											int durationTicks, double arenaHalfSize) {
		this.spellId = definition.id.toString();
		this.durationTicks = durationTicks;
		this.arenaHalfSize = arenaHalfSize;
	}

	@Override
	public void handle(NetworkEvent.Context context) {
		ServerPlayer player = context.getSender();
		if (player == null) return;
		context.enqueueWork(() -> {
			try {
				if (spellId.isEmpty()) {
					player.displayClientMessage(YHLangData.CERT_QUOTE_FAIL.get("missing spell id"), false);
					return;
				}
				var id = net.minecraft.resources.ResourceLocation.tryParse(spellId);
				var definition = id == null ? null : SpellRegistry.get(id);
				if (definition == null) {
					player.displayClientMessage(YHLangData.CERT_QUOTE_FAIL.get("unknown definition"), false);
					return;
				}
				CertificationQuoteRequestHandler.accept(player, definition, durationTicks, arenaHalfSize);
			} catch (Exception e) {
				player.displayClientMessage(YHLangData.CERT_QUOTE_FAIL.get(e.getMessage()), false);
			}
		});
		context.setPacketHandled(true);
	}
}
