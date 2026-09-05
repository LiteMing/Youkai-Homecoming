package dev.xkmc.youkaishomecoming.content.spell.certification.network;

import dev.xkmc.l2serial.network.SerialPacketBase;
import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.content.spell.certification.CertificationManager;
import dev.xkmc.youkaishomecoming.content.spell.certification.CertificationQuote;
import dev.xkmc.youkaishomecoming.content.spell.certification.CertificationService;
import dev.xkmc.youkaishomecoming.content.spell.certification.CertifiedSpellStorage;
import dev.xkmc.youkaishomecoming.init.data.YHLangData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client → server: start the certification for a previously quoted definition.
 * Only the quoteId travels — the server resolves the cached definition, so a
 * tampered client cannot substitute its own (design doc §18).
 */
@SerialClass
public class CertificationStartRequestToServer extends SerialPacketBase {

	@SerialClass.SerialField
	public String quoteId = "";

	@SerialClass.SerialField
	public byte[] snapshotPng = new byte[0];

	public CertificationStartRequestToServer() {
	}

	public CertificationStartRequestToServer(String quoteId) {
		this(quoteId, new byte[0]);
	}

	public CertificationStartRequestToServer(String quoteId, byte[] snapshotPng) {
		this.quoteId = quoteId;
		this.snapshotPng = snapshotPng == null ? new byte[0] : snapshotPng;
	}

	@Override
	public void handle(NetworkEvent.Context context) {
		ServerPlayer player = context.getSender();
		if (player == null) return;
		context.enqueueWork(() -> {
			CertificationQuote quote = CertificationManager.INSTANCE.getQuote(player);
			if (quote == null || !quote.quoteId().equals(quoteId)) {
				player.displayClientMessage(YHLangData.CERT_START_FAIL.get(
						YHLangData.CERT_START_QUOTE_EXPIRED.get()), false);
				return;
			}
			if (!CertificationService.start(player, quote)) {
				player.displayClientMessage(YHLangData.CERT_START_FAIL.get(
						YHLangData.CERT_START_REJECTED.get()), false);
			} else if (snapshotPng != null && snapshotPng.length > 0) {
				CertifiedSpellStorage.saveSnapshot(player.server, quote.definitionHash(), snapshotPng);
			}
		});
		context.setPacketHandled(true);
	}
}
