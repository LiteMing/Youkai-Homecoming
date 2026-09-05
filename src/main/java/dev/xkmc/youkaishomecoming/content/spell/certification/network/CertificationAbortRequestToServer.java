package dev.xkmc.youkaishomecoming.content.spell.certification.network;

import dev.xkmc.l2serial.network.SerialPacketBase;
import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.content.spell.certification.CertificationManager;
import dev.xkmc.youkaishomecoming.content.spell.certification.CertificationController;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client → server: manual abort of the player's own certification (design doc
 * §18). Only the creator may abort; refunds follow the abort policy.
 */
@SerialClass
public class CertificationAbortRequestToServer extends SerialPacketBase {

	public CertificationAbortRequestToServer() {
	}

	@Override
	public void handle(NetworkEvent.Context context) {
		ServerPlayer player = context.getSender();
		if (player == null) return;
		context.enqueueWork(() -> {
			CertificationController trial = CertificationManager.INSTANCE.getActiveTrial(player);
			if (trial != null) {
				trial.abort();
			}
		});
		context.setPacketHandled(true);
	}
}
